/* 
 * Copyright 2015 Alexander Nozik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package inr.numass.control.cryotemp

import hep.dataforge.context.Context
import hep.dataforge.control.RoleDef
import hep.dataforge.control.RoleDefs
import hep.dataforge.control.collectors.RegularPointCollector
import hep.dataforge.control.connections.Roles
import hep.dataforge.control.connections.StorageConnection
import hep.dataforge.control.devices.Device
import hep.dataforge.control.devices.PortSensor
import hep.dataforge.control.devices.StateDef
import hep.dataforge.control.measurements.AbstractMeasurement
import hep.dataforge.control.measurements.Measurement
import hep.dataforge.control.ports.PortHandler
import hep.dataforge.description.ValueDef
import hep.dataforge.exceptions.ControlException
import hep.dataforge.exceptions.MeasurementException
import hep.dataforge.exceptions.StorageException
import hep.dataforge.meta.Meta
import hep.dataforge.storage.api.TableLoader
import hep.dataforge.storage.commons.LoaderFactory
import hep.dataforge.tables.TableFormat
import hep.dataforge.tables.TableFormatBuilder
import hep.dataforge.utils.DateTimeUtils
import hep.dataforge.values.Values
import inr.numass.control.DeviceView
import inr.numass.control.StorageHelper
import java.time.Duration
import java.util.*
import kotlin.streams.toList


/**
 * A device controller for Dubna PKT 8 cryogenic thermometry device
 *
 * @author Alexander Nozik
 */
@RoleDefs(
        RoleDef(name = Roles.STORAGE_ROLE),
        RoleDef(name = Roles.VIEW_ROLE)
)
@ValueDef(name = "port", def = "virtual", info = "The name of the port for this PKT8")
@StateDef(ValueDef(name = "storing"))
@DeviceView(PKT8Display::class)
class PKT8Device(context: Context, meta: Meta) : PortSensor<PKT8Result>(context, meta) {
    /**
     * The key is the letter (a,b,c,d...) as in measurements
     */
    private val channels = LinkedHashMap<String, PKT8Channel>()
    private var collector: RegularPointCollector? = null
    private var storageHelper: StorageHelper? = null

    /**
     * Cached values
     */
    private var format: TableFormat? = null


    private// Building data format
    val tableFormat: TableFormat by lazy {
        val tableFormatBuilder = TableFormatBuilder()
                .addTime("timestamp")

        for (channel in channels.values) {
            tableFormatBuilder.addNumber(channel.name)
        }
        tableFormatBuilder.build()
    }

    val chanels: Collection<PKT8Channel>
        get() = this.channels.values

    val sps: String
        get() = getState(SPS).stringValue()

    val pga: String
        get() = getState(PGA).stringValue()

    val abuf: String
        get() = getState(ABUF).stringValue()

    private fun buildLoader(connection: StorageConnection): TableLoader {
        val storage = connection.storage
        val suffix = DateTimeUtils.fileSuffix()

        try {
            return LoaderFactory.buildPointLoder(storage,
                    "cryotemp_" + suffix, "", "timestamp", tableFormat)
        } catch (e: StorageException) {
            throw RuntimeException("Failed to builder loader from storage", e)
        }

    }

    @Throws(ControlException::class)
    override fun init() {

        //read channel configuration
        if (meta().hasMeta("channel")) {
            for (node in meta().getMetaList("channel")) {
                val designation = node.getString("designation", "default")
                this.channels.put(designation, createChannel(node))
            }
        } else {
            //set default channel configuration
            for (designation in CHANNEL_DESIGNATIONS) {
                channels.put(designation, createChannel(designation))
            }
            logger.warn("No channels defined in configuration")
        }

        super.init()

        //update parameters from meta
        if (meta().hasValue("pga")) {
            logger.info("Setting dynamic range to " + meta().getInt("pga")!!)
            val response = sendAndWait("g" + meta().getInt("pga")!!, TIMEOUT).trim { it <= ' ' }
            if (response.contains("=")) {
                updateState(PGA, Integer.parseInt(response.substring(4)))
            } else {
                logger.error("Setting pga failsed with message: " + response)
            }
        }

        setSPS(meta().getInt("sps", 0)!!)
        setBUF(meta().getInt("abuf", 100)!!)

        // setting up the collector
        storageHelper = StorageHelper(this) { connection: StorageConnection -> this.buildLoader(connection) }
        val duration = Duration.parse(meta().getString("averagingDuration", "PT30S"))
        collector = RegularPointCollector(
                duration,
                channels.values.stream().map { it.name }.toList()
        ) { dp: Values ->
            logger.debug("Point measurement complete. Pushing...")
            storageHelper!!.push(dp)
        }
    }

    @Throws(ControlException::class)
    override fun shutdown() {
        storageHelper!!.close()
        if (collector != null) {
            collector!!.stop()
            collector = null
        }
        super.shutdown()
    }

    @Throws(ControlException::class)
    override fun buildHandler(portName: String): PortHandler {
        val handler: PortHandler
        //setup connection
        if ("virtual" == portName) {
            logger.info("Starting {} using virtual debug port", name)
            handler = PKT8VirtualPort("PKT8", meta().getMetaOrEmpty("debug"))
        } else {
            handler = super.buildHandler(portName)
        }
        handler.setDelimiter("\n")

        return handler
    }

    private fun setBUF(buf: Int) {
        logger.info("Setting avaraging buffer size to " + buf)
        var response: String
        try {
            response = sendAndWait("b" + buf, Duration.ofMillis(400)).trim { it <= ' ' }
        } catch (ex: Exception) {
            response = ex.message ?: ""
        }

        if (response.contains("=")) {
            updateState(ABUF, Integer.parseInt(response.substring(14)))
            //            getLogger().info("successfully set buffer size to {}", this.abuf);
        } else {
            logger.error("Setting averaging buffer failed with message: " + response)
        }
    }

    @Throws(ControlException::class)
    fun changeParameters(sps: Int, abuf: Int) {
        stopMeasurement(false)
        //setting sps
        setSPS(sps)
        //setting buffer
        setBUF(abuf)
    }

    /**
     * '0' : 2,5 SPS '1' : 5 SPS '2' : 10 SPS '3' : 25 SPS '4' : 50 SPS '5' :
     * 100 SPS '6' : 500 SPS '7' : 1 kSPS '8' : 3,75 kSPS
     *
     * @param sps
     * @return
     */
    private fun spsToStr(sps: Int): String {
        when (sps) {
            0 -> return "2.5 SPS"
            1 -> return "5 SPS"
            2 -> return "10 SPS"
            3 -> return "25 SPS"
            4 -> return "50 SPS"
            5 -> return "100 SPS"
            6 -> return "500 SPS"
            7 -> return "1 kSPS"
            8 -> return "3.75 kSPS"
            else -> return "unknown value"
        }
    }

    /**
     * '0' : ± 5 В '1' : ± 2,5 В '2' : ± 1,25 В '3' : ± 0,625 В '4' : ± 312.5 мВ
     * '5' : ± 156,25 мВ '6' : ± 78,125 мВ
     *
     * @param pga
     * @return
     */
    private fun pgaToStr(pga: Int): String {
        when (pga) {
            0 -> return "± 5 V"
            1 -> return "± 2,5 V"
            2 -> return "± 1,25 V"
            3 -> return "± 0,625 V"
            4 -> return "± 312.5 mV"
            5 -> return "± 156.25 mV"
            6 -> return "± 78.125 mV"
            else -> return "unknown value"
        }
    }

    private fun setSPS(sps: Int) {
        logger.info("Setting sampling rate to " + spsToStr(sps))
        var response: String
        try {
            response = sendAndWait("v" + sps, TIMEOUT).trim { it <= ' ' }
        } catch (ex: Exception) {
            response = ex.message ?: ""
        }

        if (response.contains("=")) {
            updateState(SPS, Integer.parseInt(response.substring(4)))
            //            getLogger().info("successfully sampling rate to {}", spsToStr(this.sps));
        } else {
            logger.error("Setting sps failsed with message: " + response)
        }
    }

    //    public void connectPointListener(PointListenerConnection listener) {
    //        this.connect(listener, Roles.POINT_LISTENER_ROLE);
    //    }

    @Throws(MeasurementException::class)
    override fun createMeasurement(): Measurement<PKT8Result> {
        return if (this.measurement != null) {
            this.measurement
        } else {
            try {
                if (handler.isLocked) {
                    logger.error("Breaking hold on handler because it is locked")
                    handler.breakHold()
                }
                PKT8Measurement(handler)
            } catch (e: ControlException) {
                throw MeasurementException(e)
            }

        }
    }

    @Throws(MeasurementException::class)
    override fun startMeasurement(): Measurement<PKT8Result> {
        //clearing PKT queue
        try {
            send("p")
            sendAndWait("p", TIMEOUT)
        } catch (e: ControlException) {
            logger.error("Failed to clear PKT8 port")
            //   throw new MeasurementException(e);
        }

        return super.startMeasurement()
    }


    inner class PKT8Measurement(internal val handler: PortHandler) : AbstractMeasurement<PKT8Result>(), PortHandler.PortController {

        override fun getDevice(): Device {
            return this@PKT8Device
        }

        override fun start() {
            if (isStarted) {
                logger.warn("Trying to start measurement which is already started")
            }

            try {
                logger.info("Starting measurement")
                handler.holdBy(this)
                send("s")
                afterStart()
            } catch (ex: ControlException) {
                portError("Failed to start measurement", ex)
            }

        }

        @Throws(MeasurementException::class)
        override fun stop(force: Boolean): Boolean {
            if (isFinished) {
                logger.warn("Trying to stop measurement which is already stopped")
            }

            try {
                logger.info("Stopping measurement")
                val response = sendAndWait("p", TIMEOUT).trim { it <= ' ' }
                // Должно быть именно с большой буквы!!!
                return "Stopped" == response || "stopped" == response
            } catch (ex: Exception) {
                error(ex)
                return false
            } finally {
                if (collector != null) {
                    collector!!.clear()
                }
                handler.unholdBy(this)
            }
        }


        override fun acceptPortPhrase(message: String) {
            val trimmed = message.trim { it <= ' ' }

            if (isStarted) {
                if (trimmed == "Stopped" || trimmed == "stopped") {
                    afterPause()
                    //                    getLogger().info("Measurement stopped");
                } else {
                    val designation = trimmed.substring(0, 1)
                    val rawValue = java.lang.Double.parseDouble(trimmed.substring(1)) / 100

                    val channel = channels[designation]

                    if (channel != null) {
                        result(channel.evaluate(rawValue))
                        if (collector != null) {
                            collector!!.put(channel.getName(), channel.getTemperature(rawValue))
                        }
                    } else {
                        result(PKT8Result(designation, rawValue, -1.0))
                    }
                }
            }
        }

        override fun portError(errorMessage: String, error: Throwable) {
            super.error(error)
        }
    }

    companion object {
        val PKT8_DEVICE_TYPE = "numass.pkt8"
        private val TIMEOUT = Duration.ofMillis(400)

        val PGA = "pga"
        val SPS = "sps"
        val ABUF = "abuf"
        private val CHANNEL_DESIGNATIONS = arrayOf("a", "b", "c", "d", "e", "f", "g", "h")
    }

    init {
        setContext(context)
        setMeta(meta)
    }
}