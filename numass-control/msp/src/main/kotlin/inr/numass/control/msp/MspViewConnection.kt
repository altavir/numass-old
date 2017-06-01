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
package inr.numass.control.msp

import hep.dataforge.control.NamedValueListener
import hep.dataforge.control.devices.Device
import hep.dataforge.control.devices.DeviceListener
import hep.dataforge.control.devices.PortSensor
import hep.dataforge.control.devices.Sensor
import hep.dataforge.control.devices.Sensor.MEASURING_STATE
import hep.dataforge.fx.fragments.FragmentWindow
import hep.dataforge.fx.fragments.LogFragment
import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaBuilder
import hep.dataforge.plots.PlotUtils
import hep.dataforge.plots.data.TimePlottable
import hep.dataforge.plots.data.TimePlottableGroup
import hep.dataforge.plots.fx.FXPlotFrame
import hep.dataforge.plots.fx.PlotContainer
import hep.dataforge.plots.jfreechart.JFreeChartFrame
import hep.dataforge.values.Value
import inr.numass.control.DeviceViewConnection
import inr.numass.control.deviceStateIndicator
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Alert
import javafx.scene.control.ToggleButton
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Paint
import org.controlsfx.control.ToggleSwitch
import tornadofx.*

/**
 * FXML Controller class

 * @author darksnake
 */
class MspViewConnection : DeviceViewConnection<MspDevice>(), DeviceListener, NamedValueListener {
    private val mspView by lazy { MspView() }

    override fun getBoardView(): Parent {
        return VBox().apply {
            this += super.getBoardView()
        }
    }

    override fun getFXNode(): Node {
        if (!isOpen) {
            throw RuntimeException("Not connected!")
        }
        return mspView.root;
    }

    override fun pushValue(valueName: String, value: Value) {
        val pl = plottables.get(valueName)
        if (pl != null) {
            if (value.doubleValue() > 0) {
                pl.put(value)
            } else {
                pl.put(Value.NULL)
            }
            val titleBase = pl.config.getString("titleBase")
            val title = String.format("%s (%.4g)", titleBase, value.doubleValue())
            pl.configureValue("title", title)
        }
    }


    inner class MspView : View("Numass mass-spectrometer measurement") {
        val plotFrameMeta: Meta = device.meta().getMeta("plotConfig", device.meta)

        val plotFrame: FXPlotFrame by lazy {
            val basePlotConfig = MetaBuilder("plotFrame")
                    .setNode(MetaBuilder("yAxis")
                            .setValue("type", "log")
                            .setValue("axisTitle", "partial pressure")
                            .setValue("axisUnits", "mbar")
                    )
                    .setValue("xAxis.type", "time")


            JFreeChartFrame(basePlotConfig).apply {
                PlotUtils.setXAxis(this, "timestamp", null, "time")
                configure(plotFrameMeta)
            }
        }
        val plottables: TimePlottableGroup = TimePlottableGroup()

        private var logButton: ToggleButton by singleAssign()

        private val logWindow = FragmentWindow(LogFragment().apply {
            addLogHandler(device.logger)
        })

        val filamentProperty = SimpleObjectProperty<Int>().apply {
            addListener { _, oldValue, newValue ->
                if (newValue != oldValue) {
                    device.selectFillament(newValue);
                }
            }
        }

        override val root = borderpane {
            minHeight = 400.0
            minWidth = 600.0
            top {
                toolbar {
                    togglebutton("Connect") {
                        isSelected = false
                        bindBooleanToState(PortSensor.CONNECTED_STATE, selectedProperty())
                    }
                    combobox(filamentProperty, listOf(1, 2)) {
                        cellFormat {
                            text = "Filament $it"
                        }
                        selectionModel.selectFirst()
                        disableProperty()
                                .bind(getStateBinding(MEASURING_STATE).booleanBinding { it!!.booleanValue() })
                    }
                    add(ToggleSwitch().apply {
                        padding = Insets(11.0, 0.0, 0.0, 0.0)
                        disableProperty()
                                .bind(getStateBinding(PortSensor.CONNECTED_STATE).booleanBinding { !it!!.booleanValue() })
                        bindBooleanToState("filamentOn", selectedProperty())
                    })
                    deviceStateIndicator(this@MspViewConnection, "filamentStatus") {
                        when (it.stringValue()) {
                            "ON" -> Paint.valueOf("red")
                            "OFF" -> Paint.valueOf("blue")
                            "WARM-UP", "COOL-DOWN" -> Paint.valueOf("yellow")
                            else -> error("Unknown filament state")

                        }
                    }
                    separator(Orientation.VERTICAL)

                    togglebutton("Measure") {
                        isSelected = false
                        disableProperty()
                                .bind(getStateBinding(PortSensor.CONNECTED_STATE).booleanBinding { !it!!.booleanValue() })

                        bindBooleanToState(Sensor.MEASURING_STATE, selectedProperty())
                    }
                    togglebutton("Store") {
                        isSelected = false
                        disableProperty()
                                .bind(getStateBinding(Sensor.MEASURING_STATE).booleanBinding { !it!!.booleanValue() })
                        bindBooleanToState("storing", selectedProperty())
                    }
                    separator(Orientation.VERTICAL)
                    pane {
                        hgrow = Priority.ALWAYS
                    }
                    separator(Orientation.VERTICAL)

                    logButton = togglebutton("Log") {
                        isSelected = false
                        logWindow.bindTo(this)
                    }
                }
            }
            PlotContainer.centerIn(this).plot = plotFrame
        }

        private var plot: JFreeChartFrame? = null
        private var logFragment: LogFragment? = null

        @Throws(Exception::class)
        override fun open(device: MspDevice) {
            super.open(device)
            updatePlot()

            bindBooleanToState("connected", connectButton!!.selectedProperty())
        }

        private fun initPlot() {
            val plotConfig = MetaBuilder("plotFrame")
                    .setNode(MetaBuilder("yAxis")
                            .setValue("type", "log")
                            .setValue("axisTitle", "partial pressure")
                            .setValue("axisUnits", "mbar")
                    )
                    .setValue("xAxis.type", "time")

            this.plot = JFreeChartFrame(plotConfig)
            val container = PlotContainer.centerIn(plotPane)
            container.plot = plot
        }

        private fun updatePlot() {
            if (plot == null) {
                initPlot()
            }
            if (plotFrameMeta.hasMeta("plotFrame")) {
                this.plot!!.configure(plotFrameMeta.getMeta("plotFrame"))
            }
            if (plotFrameMeta.hasMeta("peakJump.peak")) {
                for (peakMeta in plotFrameMeta.getMetaList("peakJump.peak")) {
                    val mass = peakMeta.getString("mass")
                    if (!this.plottables.has(mass)) {
                        val newPlottable = TimePlottable(mass, mass)
                        newPlottable.configure(peakMeta)
                        newPlottable.setMaxItems(1000)
                        newPlottable.setPrefItems(400)
                        newPlottable.configureValue("titleBase", peakMeta.getString("title", mass))
                        this.plottables.add(newPlottable)
                        plot!!.add(newPlottable)
                    } else {
                        plottables.get(mass).configure(peakMeta)
                    }
                }
            } else {
                showError("No peaks defined in config")
                throw RuntimeException()
            }
        }

        override fun evaluateDeviceException(device: Device, message: String, exception: Throwable) {
            Platform.runLater {
                logFragment!!.appendLine("ERROR: " + message)
                showError(message)
            }
        }

        private fun showError(message: String) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Error!"
            alert.headerText = null
            alert.contentText = message

            alert.showAndWait()
        }
    }
}