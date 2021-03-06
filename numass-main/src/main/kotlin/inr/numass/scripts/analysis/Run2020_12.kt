package inr.numass.scripts.analysis

import hep.dataforge.context.Global
import hep.dataforge.context.plugin
import hep.dataforge.fx.output.FXOutputManager
import hep.dataforge.grind.workspace.GroovyWorkspaceParser
import hep.dataforge.plots.plotData
import hep.dataforge.tables.Adapters
import hep.dataforge.tables.Table
import hep.dataforge.workspace.FileBasedWorkspace
import hep.dataforge.workspace.Workspace
import hep.dataforge.workspace.context
import hep.dataforge.workspace.data
import inr.numass.displayChart
import java.io.File

fun main() {

//    val context = buildContext("NUMASS", NumassPlugin::class.java, JFreeChartPlugin::class.java) {
//        output = FXOutputManager()
//        rootDir = "D:\\Work\\Numass\\sterile2017_11"
//        dataDir = "D:\\Work\\Numass\\data\\2017_11"
//        properties {
//            "cache.enabled" to false
//        }
//    }
    FXOutputManager().startGlobal()

    val configPath = File("D:\\Work\\Numass\\sterile2020_12\\workspace.groovy").toPath()

    val workspace = FileBasedWorkspace.build(Global, configPath)

    //workspace.context.setValue("cache.enabled", false)

    workspace.runTask("fit","Fill_4")

//    val result = workspace.runTask("dif", "adiab_19").first().get() as Table
//
//    displayChart("Adiabacity").apply {
//        plotData("Adiabacity_19", result, Adapters.buildXYAdapter("voltage", "cr"))
//    }
//
//    println("Complete!")
}