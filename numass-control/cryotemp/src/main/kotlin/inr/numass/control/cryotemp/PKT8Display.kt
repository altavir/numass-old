package inr.numass.control.cryotemp

import hep.dataforge.fx.asBooleanProperty
import hep.dataforge.fx.bindWindow
import hep.dataforge.fx.dfIconView
import hep.dataforge.fx.fragments.LogFragment
import hep.dataforge.fx.plots.PlotContainer
import hep.dataforge.meta.Meta
import hep.dataforge.plots.Plot
import hep.dataforge.plots.PlotUtils
import hep.dataforge.plots.data.TimePlot
import hep.dataforge.plots.jfreechart.JFreeChartFrame
import inr.numass.control.DeviceDisplayFX
import javafx.beans.binding.ListBinding
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Orientation
import javafx.scene.Parent
import javafx.scene.control.ToggleButton
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import tornadofx.*
import java.time.Instant

/**
 * Created by darksnake on 30-May-17.
 */
class PKT8Display : DeviceDisplayFX<PKT8Device>(), PKT8ValueListener {

    override fun buildView(device: PKT8Device) = CryoView()

    internal val table = FXCollections.observableHashMap<String, PKT8Reading>()
    val lastUpdateProperty = SimpleObjectProperty<String>("NEVER")


    override fun getBoardView(): Parent {
        return VBox().apply {
            this += super.getBoardView()
        }
    }

//    override fun onMeasurementFailed(measurement: Measurement<*>, exception: Throwable) {
//
//    }
//
//    override fun onMeasurementResult(measurement: Measurement<*>, result: Any, time: Instant) {
//        if (result is PKT8Result) {
//            Platform.runLater {
//                lastUpdateProperty.set(time.toString())
//                table[result.channel] = result;
//            }
//        }
//    }
//

    override fun report(reading: PKT8Reading, time: Instant) {
        runLater {
            lastUpdateProperty.set(time.toString())
            table[reading.channel] = reading;
        }
    }

    inner class CryoView : Fragment("PKT values", dfIconView) {
        private var plotButton: ToggleButton by singleAssign()
        private var logButton: ToggleButton by singleAssign()

//        private val logWindow = FragmentWindow(LogFragment().apply {
//            addLogHandler(device.logger)
//        })

        // need those to have strong references to listeners
        private val plotView = CryoPlotView();
//        private val plotWindow = FragmentWindow(FXFragment.buildFromNode(plotView.title) { plotView.root })

        override val root = borderpane {
            top {
                toolbar {
                    togglebutton("Measure") {
                        isSelected = false
                        selectedProperty().bindBidirectional(device.measuring.asBooleanProperty())
                    }
                    togglebutton("Store") {
                        isSelected = false
                        selectedProperty().bindBidirectional(device.storing.asBooleanProperty())
                    }
                    separator(Orientation.VERTICAL)
                    pane {
                        hgrow = Priority.ALWAYS
                    }
                    separator(Orientation.VERTICAL)

                    plotButton = togglebutton("Plot") {
                        isSelected = false
                        plotView.bindWindow(this, selectedProperty())
                    }

                    logButton = togglebutton("Log") {
                        isSelected = false
                        LogFragment().apply {
                            addLogHandler(device.logger)
                            bindWindow(this@togglebutton, selectedProperty())
                        }
                    }
                }
            }
            center {
                tableview<PKT8Reading> {
                    items = object : ListBinding<PKT8Reading>() {
                        init {
                            bind(table)
                        }

                        override fun computeValue(): ObservableList<PKT8Reading> {
                            return FXCollections.observableArrayList(table.values).apply {
                                sortBy { it.channel }
                            }
                        }
                    }
                    readonlyColumn("Sensor", PKT8Reading::channel);
                    readonlyColumn("Resistance", PKT8Reading::rawValue).cellFormat {
                        text = String.format("%.2f", it)
                    }
                    readonlyColumn("Temperature", PKT8Reading::temperature).cellFormat {
                        text = String.format("%.2f", it)
                    }
                }
            }
            bottom {
                toolbar {
                    label("Last update: ")
                    label(lastUpdateProperty) {
                        font = Font.font("System Bold")
                    }
                }
            }
        }
    }

    inner class CryoPlotView : Fragment("PKT8 temperature plot", dfIconView) {
        private val plotFrameMeta: Meta = device.meta.getMetaOrEmpty("plotConfig")

        private val plotFrame by lazy {
            JFreeChartFrame().apply {
                configure(plotFrameMeta)
                plots.setType<TimePlot>()
                PlotUtils.setXAxis(this, "timestamp", "", "time")
            }
        }

        var rawDataButton: ToggleButton by singleAssign()

        override val root: Parent = borderpane {
            prefWidth = 800.0
            prefHeight = 600.0
            center = PlotContainer(plotFrame).root
            top {
                toolbar {
                    rawDataButton = togglebutton("Raw data") {
                        isSelected = false
                        action {
                            clearPlot()
                        }
                    }
                    button("Reset") {
                        action {
                            clearPlot()
                        }
                    }
                }
            }
        }

        init {
            if (device.meta.hasMeta("plotConfig")) {
                with(plotFrame.plots) {
                    //configure(device.meta().getMeta("plotConfig"))
                    TimePlot.setMaxItems(this, 1000)
                    TimePlot.setPrefItems(this, 400)
                }
            }
            table.addListener(MapChangeListener { change ->
                if (change.wasAdded()) {
                    change.valueAdded.apply {
                        getPlot(channel)?.apply {
                            if (rawDataButton.isSelected) {
                                TimePlot.put(this, rawValue)
                            } else {
                                if (temperature != null) {
                                    TimePlot.put(this, temperature)
                                }
                            }
                        }
                    }
                }
            })
        }

        private fun getPlot(channelName: String): Plot? {
            return plotFrame[channelName] as? Plot ?: device.channels.values.find { it.name == channelName }?.let {
                TimePlot(it.name).apply {
                    configure(it.meta)
                    plotFrame.add(this)
                }
            }

        }

        private fun clearPlot() {
            plotFrame.clear()
        }
    }
}

