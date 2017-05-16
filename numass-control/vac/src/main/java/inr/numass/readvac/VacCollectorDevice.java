/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package inr.numass.readvac;

import hep.dataforge.control.collectors.PointCollector;
import hep.dataforge.control.collectors.ValueCollector;
import hep.dataforge.control.connections.Roles;
import hep.dataforge.control.connections.StorageConnection;
import hep.dataforge.control.devices.annotations.RoleDef;
import hep.dataforge.control.devices.annotations.StateDef;
import hep.dataforge.control.measurements.AbstractMeasurement;
import hep.dataforge.control.measurements.Measurement;
import hep.dataforge.control.measurements.Sensor;
import hep.dataforge.exceptions.ControlException;
import hep.dataforge.exceptions.MeasurementException;
import hep.dataforge.storage.api.PointLoader;
import hep.dataforge.storage.commons.LoaderFactory;
import hep.dataforge.tables.DataPoint;
import hep.dataforge.tables.MapPoint;
import hep.dataforge.tables.PointListener;
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

/**
 * @author <a href="mailto:altavir@gmail.com">Alexander Nozik</a>
 */
@RoleDef(name = Roles.STORAGE_ROLE, objectType = PointListener.class, info = "Storage for acquired points")
@StateDef(name = "storing", writable = true, info = "Define if this device is currently writes to storage")
public class VacCollectorDevice extends Sensor<DataPoint> {

    private Map<String, Sensor<Double>> sensorMap = new LinkedHashMap<>();

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

    @Override
    protected Object computeState(String stateName) throws ControlException {
        //TODO add dot path notation for states
        return Value.NULL;
    }

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
        getConfig().setValue("delay", delay);
        if (isMeasuring()) {
            getMeasurement().stop(false);
            getMeasurement().start();
        }
    }

    @Override
    public void shutdown() throws ControlException {
        super.shutdown();
        for (Sensor sensor : getSensors()) {
            sensor.shutdown();
        }
    }

    public Collection<Sensor<Double>> getSensors() {
        return sensorMap.values();
    }

    private class VacuumMeasurement extends AbstractMeasurement<DataPoint> {

        private final ValueCollector collector = new PointCollector(this::result, sensorMap.keySet());
        private ScheduledExecutorService executor;
        private ScheduledFuture<?> currentTask;
        private StorageHelper helper;

        @Override
        public void start() {
            helper = new StorageHelper(VacCollectorDevice.this, this::buildLoader);
            executor = Executors
                    .newSingleThreadScheduledExecutor((Runnable r) -> new Thread(r, "VacuumMeasurement thread"));
            currentTask = executor.scheduleWithFixedDelay(() -> {
                sensorMap.values().forEach((sensor) -> {
                    try {
                        Object value;
                        if(sensor.optBooleanState("disabled").orElse(false)){
                            value = null;
                        } else {
                            value = sensor.read();
                        }
                        collector.put(sensor.getName(), value);
                    } catch (Exception ex) {
                        collector.put(sensor.getName(), Value.NULL);
                    }
                });
            }, 0, meta().getInt("delay", 5000), TimeUnit.MILLISECONDS);
        }

        @Override
        protected synchronized void result(DataPoint result, Instant time) {
            super.result(result, time);
            forEachConnection(Roles.STORAGE_ROLE, PointListener.class, (PointListener listener) -> {
                listener.accept(result);
            });
        }

        private PointLoader buildLoader(StorageConnection connection) {
            TableFormatBuilder format = new TableFormatBuilder().setType("timestamp", ValueType.TIME);
            getSensors().forEach((s) -> {
                format.setType(s.getName(), ValueType.NUMBER);
            });

            return LoaderFactory.buildPointLoder(connection.getStorage(), "vactms", "", "timestamp", format.build());
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
                helper.close();
                afterStop();
            }
            return isRunning;
        }
    }

}