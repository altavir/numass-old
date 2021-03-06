package inr.numass.scripts.tristan

import hep.dataforge.context.Global
import hep.dataforge.fx.output.FXOutputManager
import hep.dataforge.fx.plots.group
import hep.dataforge.plots.jfreechart.JFreeChartPlugin
import hep.dataforge.plots.output.plotFrame
import inr.numass.data.plotAmplitudeSpectrum
import inr.numass.data.storage.readNumassSet
import java.io.File

fun main() {
    Global.output = FXOutputManager()
    JFreeChartPlugin().startGlobal()

    val file = File("D:\\Work\\Numass\\data\\2018_04\\Fill_3\\set_36").toPath()
    val set = Global.readNumassSet(file)


    Global.plotFrame("compare") {
        listOf(12000.0, 13000.0, 14000.0, 14900.0).forEach {voltage->
            val point = set.optPoint(voltage).get()
            val block = point.channel(0)!!

            group("${set.name}/p${point.index}[${point.voltage}]") {
                plotAmplitudeSpectrum(block, "cut") {
                    "t0" to 3e3
                    "sortEvents" to true
                }
                plotAmplitudeSpectrum(block, "uncut")
            }
        }
    }

    readLine()
}