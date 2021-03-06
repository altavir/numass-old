/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package inr.numass.control.readvac

import hep.dataforge.connections.Connection
import hep.dataforge.connections.RoleDef
import hep.dataforge.context.Context
import hep.dataforge.context.launch
import hep.dataforge.control.collectors.RegularPointCollector
import hep.dataforge.control.connections.Roles
import hep.dataforge.control.devices.Device
import hep.dataforge.control.devices.DeviceHub
import hep.dataforge.control.devices.DeviceListener
import hep.dataforge.control.devices.PortSensor.Companion.CONNECTED_STATE
import hep.dataforge.control.devices.Sensor
import hep.dataforge.description.ValueDef
import hep.dataforge.exceptions.ControlException
import hep.dataforge.meta.Meta
import hep.dataforge.names.Name
import hep.dataforge.states.StateDef
import hep.dataforge.storage.StorageConnection
import hep.dataforge.storage.tables.TableLoader
import hep.dataforge.storage.tables.createTable
import hep.dataforge.tables.TableFormatBuilder
import hep.dataforge.utils.DateTimeUtils
import hep.dataforge.values.Value
import hep.dataforge.values.ValueMap
import hep.dataforge.values.ValueType
import hep.dataforge.values.Values
import inr.numass.control.DeviceView
import inr.numass.control.StorageHelper
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * @author [Alexander Nozik](mailto:altavir@gmail.com)
 */
@RoleDef(name = Roles.STORAGE_ROLE, objectType = StorageConnection::class, info = "Storage for acquired points")
@StateDef(value = ValueDef(key = "storing", info = "Define if this device is currently writes to storage"), writable = true)
@DeviceView(VacCollectorDisplay::class)
class VacCollectorDevice(context: Context, meta: Meta, val sensors: Collection<Sensor>) : Sensor(context, meta), DeviceHub {

    private val helper = StorageHelper(this, this::buildLoader)

    private val collector = object : DeviceListener {
        val averagingDuration: Duration = Duration.parse(meta.getString("averagingDuration", "PT30S"))

        private val collector = RegularPointCollector(averagingDuration) {
            notifyResult(it)
        }

        override fun notifyStateChanged(device: Device, name: String, state: Any) {
            if (name == MEASUREMENT_RESULT_STATE) {
                collector.put(device.name, (state as Meta).getValue(RESULT_VALUE))
            }
        }
    }


    override fun optDevice(name: Name): Optional<Device> =
            Optional.ofNullable(sensors.find { it.name == name.unescaped })

    override val deviceNames: List<Name>
        get() = sensors.map { Name.ofSingle(it.name) }


    override fun init() {
        for (s in sensors) {
            s.init()
            s.connect(collector, Roles.DEVICE_LISTENER_ROLE)
        }
        super.init()
    }

    override val type: String
        get() = "numass.vac.collector"


    @Throws(ControlException::class)
    override fun shutdown() {
        super.shutdown()
        helper.close()
        for (sensor in sensors) {
            sensor.shutdown()
        }
    }

    private fun buildLoader(connection: StorageConnection): TableLoader {
        val format = TableFormatBuilder().setType("timestamp", ValueType.TIME)
        sensors.forEach { s -> format.setType(s.name, ValueType.NUMBER) }

        val suffix = DateTimeUtils.fileSuffix()

        return connection.storage.createTable("vactms_$suffix", format.build())
        //LoaderFactory.buildPointLoader(connection.storage, "vactms_$suffix", "", "timestamp", format.build())
    }

    override fun connectAll(connection: Connection, vararg roles: String) {
        connect(connection, *roles)
        this.sensors.forEach { it.connect(connection, *roles) }
    }

    override fun connectAll(context: Context, meta: Meta) {
        this.connectionHelper.connect(context, meta)
        this.sensors.forEach { it.connectionHelper.connect(context, meta) }
    }


    private fun notifyResult(values: Values, timestamp: Instant = Instant.now()) {
        super.notifyResult(values, timestamp)
        helper.push(values)
    }

    override fun stopMeasurement() {
        super.stopMeasurement()
        notifyResult(terminator())
    }

    override fun startMeasurement(oldMeta: Meta?, newMeta: Meta) {
        oldMeta?.let {
            stopMeasurement()
        }

        val interval = Duration.ofSeconds(meta.getInt("delay", 5).toLong())

        job = launch {
            while (true) {
                notifyMeasurementState(MeasurementState.IN_PROGRESS)
                sensors.forEach { sensor ->
                    if (sensor.states.getBoolean(CONNECTED_STATE, false)) {
                        sensor.measure()
                    }
                }
                notifyMeasurementState(MeasurementState.WAITING)
                delay(interval)
            }
        }
    }

    private fun terminator(): Values {
        val p = ValueMap.Builder()
        deviceNames.forEach { n -> p.putValue(n.unescaped, Value.NULL) }
        return p.build()
    }


}
