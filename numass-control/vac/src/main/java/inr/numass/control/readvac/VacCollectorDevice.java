/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package inr.numass.control.readvac;

import hep.dataforge.context.Context;
import hep.dataforge.control.RoleDef;
import hep.dataforge.control.collectors.PointCollector;
import hep.dataforge.control.collectors.ValueCollector;
import hep.dataforge.control.connections.Roles;
import hep.dataforge.control.connections.StorageConnection;
import hep.dataforge.control.devices.Device;
import hep.dataforge.control.devices.StateDef;
import hep.dataforge.control.measurements.AbstractMeasurement;
import hep.dataforge.control.measurements.Measurement;
import hep.dataforge.control.measurements.Sensor;
import hep.dataforge.exceptions.ControlException;
import hep.dataforge.exceptions.MeasurementException;
import hep.dataforge.meta.Meta;
import hep.dataforge.storage.api.PointLoader;
import hep.dataforge.storage.commons.LoaderFactory;
import hep.dataforge.tables.DataPoint;
import hep.dataforge.tables.MapPoint;
import hep.dataforge.tables.TableFormatBuilder;
import hep.dataforge.utils.DateTimeUtils;
import hep.dataforge.values.Value;
import hep.dataforge.values.ValueType;
import inr.numass.control.StorageHelper;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static hep.dataforge.control.devices.PortSensor.CONNECTED_STATE;

/**
 * @author <a href="mailto:altavir@gmail.com">Alexander Nozik</a>
 */
@RoleDef(name = Roles.STORAGE_ROLE, objectType = StorageConnection.class, info = "Storage for acquired points")
@StateDef(name = "storing", writable = true, info = "Define if this device is currently writes to storage")
public class VacCollectorDevice extends Sensor<DataPoint> {

    private Map<String, Sensor<Double>> sensorMap = new LinkedHashMap<>();
    private int delay = 5000;
    private StorageHelper helper = new StorageHelper(VacCollectorDevice.this, this::buildLoader);

    public VacCollectorDevice() {
    }

    public VacCollectorDevice(Context context, Meta meta) {
        setContext(context);
        setMeta(meta);
    }


    public void setSensors(Iterable<Sensor<Double>> sensors) {
        sensorMap = new LinkedHashMap<>();
        for (Sensor<Double> sensor : sensors) {
            sensorMap.put(sensor.getName(), sensor);
        }
    }

    @SuppressWarnings("unchecked")
    public void setSensors(Sensor... sensors) {
        setSensors(Arrays.asList(sensors));
    }

    @Override
    public void init() throws ControlException {
        super.init();
        for (Sensor<Double> s : sensorMap.values()) {
            s.init();
        }
    }

    //TODO add dot path notation for states

    @Override
    protected Measurement<DataPoint> createMeasurement() {
        //TODO use meta
        return new VacuumMeasurement();
    }

    @Override
    public String type() {
        return "Numass vacuum";
    }

    public void setDelay(int delay) throws MeasurementException {
        this.delay = delay;
        if (isMeasuring()) {
            getMeasurement().stop(false);
            getMeasurement().start();
        }
    }

    @Override
    public void shutdown() throws ControlException {
        super.shutdown();
        helper.close();
        for (Sensor sensor : getSensors()) {
            sensor.shutdown();
        }
    }

    private PointLoader buildLoader(StorageConnection connection) {
        TableFormatBuilder format = new TableFormatBuilder().setType("timestamp", ValueType.TIME);
        getSensors().forEach((s) -> {
            format.setType(s.getName(), ValueType.NUMBER);
        });

        String suffix = DateTimeUtils.fileSuffix();

        return LoaderFactory.buildPointLoder(connection.getStorage(), "vactms_" + suffix, "", "timestamp", format.build());
    }

    public Collection<Sensor<Double>> getSensors() {
        return sensorMap.values();
    }

    private class VacuumMeasurement extends AbstractMeasurement<DataPoint> {

        private final ValueCollector collector = new PointCollector(this::result, sensorMap.keySet());
        private ScheduledExecutorService executor;
        private ScheduledFuture<?> currentTask;

        @Override
        public Device getDevice() {
            return VacCollectorDevice.this;
        }



        @Override
        public void start() {
            executor = Executors
                    .newSingleThreadScheduledExecutor((Runnable r) -> new Thread(r, "VacuumMeasurement thread"));
            currentTask = executor.scheduleWithFixedDelay(() -> {
                sensorMap.values().forEach((sensor) -> {
                    try {
                        Object value;
                        if (sensor.optBooleanState(CONNECTED_STATE).orElse(false)) {
                            value = sensor.read();
                        } else {
                            value = null;
                        }
                        collector.put(sensor.getName(), value);
                    } catch (Exception ex) {
                        collector.put(sensor.getName(), Value.NULL);
                    }
                });
            }, 0, delay, TimeUnit.MILLISECONDS);
        }


        @Override
        protected synchronized void result(DataPoint result, Instant time) {
            super.result(result, time);
            helper.push(result);
        }

        private DataPoint terminator() {
            MapPoint.Builder p = new MapPoint.Builder();
            p.putValue("timestamp", DateTimeUtils.now());
            sensorMap.keySet().forEach((n) -> {
                p.putValue(n, null);
            });
            return p.build();
        }

        @Override
        public boolean stop(boolean force) {
            boolean isRunning = currentTask != null;
            if (isRunning) {
                getLogger().debug("Stoping vacuum collector measurement. Writing terminator point");
                result(terminator());
                currentTask.cancel(force);
                executor.shutdown();
                currentTask = null;
                afterStop();
            }
            return isRunning;
        }
    }

}