package inr.numass.control

import hep.dataforge.control.connections.DeviceConnection
import hep.dataforge.control.devices.Device
import hep.dataforge.control.devices.DeviceListener
import hep.dataforge.control.devices.PortSensor
import hep.dataforge.control.devices.Sensor
import hep.dataforge.fx.FXObject
import hep.dataforge.fx.fragments.FXFragment
import hep.dataforge.fx.fragments.FragmentWindow
import hep.dataforge.values.Value
import javafx.beans.binding.ObjectBinding
import javafx.beans.property.BooleanProperty
import javafx.beans.value.ObservableValue
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import tornadofx.*
import java.util.*

/**
 * Created by darksnake on 14-May-17.
 */
abstract class DeviceViewConnection<D : Device> : DeviceConnection<D>(), DeviceListener, FXObject {
    private val bindings = HashMap<String, ObjectBinding<Value>>()

    /**
     * Get binding for a given device state

     * @param state
     * *
     * @return
     */
    fun getStateBinding(state: String): ObjectBinding<Value> {
        return bindings.computeIfAbsent(state) { stateName ->
            object : ObjectBinding<Value>() {
                override fun computeValue(): Value {
                    if (isOpen) {
                        return device.getState(stateName)
                    } else {
                        return Value.NULL
                    }
                }
            }
        }
    }

    fun getBooleanStateBinding(state: String): ObservableValue<Boolean>{
        return getStateBinding(state).booleanBinding{it!!.booleanValue()}
    }

    /**
     * Bind existing boolean property to writable device state

     * @param state
     * *
     * @param property
     */
    protected fun bindBooleanToState(state: String, property: BooleanProperty) {
        getStateBinding(state).addListener { observable, oldValue, newValue ->
            if (isOpen && oldValue !== newValue) {
                property.value = newValue.booleanValue()
            }
        }
        property.addListener { observable, oldValue, newValue ->
            if (isOpen && oldValue != newValue) {
                device.setState(state, newValue)
            }
        }
    }

    override fun notifyDeviceStateChanged(device: Device, name: String, state: Value) {
        bindings[name]?.invalidate()
    }

    open fun getBoardView(): Parent {
        return HBox().apply {
            alignment = Pos.CENTER_LEFT
            vgrow = Priority.ALWAYS;
            deviceStateIndicator(this@DeviceViewConnection, Device.INITIALIZED_STATE)
            deviceStateIndicator(this@DeviceViewConnection, PortSensor.CONNECTED_STATE)
            deviceStateIndicator(this@DeviceViewConnection, Sensor.MEASURING_STATE)
            deviceStateIndicator(this@DeviceViewConnection, "storing")
            pane {
                hgrow = Priority.ALWAYS
            }
            togglebutton("View") {
                isSelected = false
                FragmentWindow(FXFragment.buildFromNode(device.name) { fxNode }).bindTo(this)
            }
        }
    }
}