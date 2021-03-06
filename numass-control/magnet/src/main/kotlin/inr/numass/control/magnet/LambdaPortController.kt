package inr.numass.control.magnet

import hep.dataforge.context.Context
import hep.dataforge.control.ports.GenericPortController
import hep.dataforge.control.ports.Port
import hep.dataforge.exceptions.PortException
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.time.Duration

//@ValueDef(name = "timeout", type = [(ValueType.NUMBER)], def = "400", info = "A timeout for port response")
class LambdaPortController(context: Context, port: Port, val timeout : Duration = Duration.ofMillis(200)) : GenericPortController(context, port, "\r") {
    private var currentAddress: Int = -1;

    fun setAddress(address: Int) {
        if(currentAddress!= address) {
            val response = sendAndWait("ADR $address\r", timeout) { true }.trim()
            if (response == "OK") {
                currentAddress = address
            } else {
                throw RuntimeException("Failed to set address to LAMBDA device on $port")
            }
        }
    }

    /**
     * perform series of synchronous actions ensuring that all of them have the same address
     */
    fun <R> talk(address: Int, action: GenericPortController.() -> R): R {
        synchronized(this) {
            setAddress(address)
            return action.invoke(this)
        }
    }


    @Throws(PortException::class)
    fun talk(addres: Int, request: String): String {
        return talk(addres) {
            try {
                send(request + "\r")
                waitFor(timeout).trim()
            } catch (tex: Port.PortTimeoutException) {
                //Single retry on timeout
                LoggerFactory.getLogger(javaClass).warn("A timeout exception for request '$request'. Making another attempt.")
                send(request + "\r")
                waitFor(timeout).trim()
            }
        }
    }

    fun getParameter(address: Int, name: String): String = talk(address, "$name?")

    fun setParameter(address: Int, key: String, state: String): Boolean {
        try {
            return "OK" == talk(address, "$key $state")
        } catch (ex: Exception) {
            logger.error("Failed to send message", ex)
            return false
        }
    }

    fun setParameter(address: Int, key: String, state: Int): Boolean = setParameter(address, key, state.toString())

    fun setParameter(address: Int, key: String, state: Double): Boolean = setParameter(address, key, d2s(state))


    companion object {
        private val LAMBDA_FORMAT = DecimalFormat("###.##")
        /**
         * Method converts double to LAMBDA string
         *
         * @param d double that should be converted to string
         * @return string
         */
        private fun d2s(d: Double): String = LAMBDA_FORMAT.format(d)

    }
}