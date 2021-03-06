/*
 * Copyright  2018 Alexander Nozik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package inr.numass.scripts.models

import hep.dataforge.buildContext
import hep.dataforge.configure
import hep.dataforge.fx.FXPlugin
import hep.dataforge.fx.output.FXOutputManager
import hep.dataforge.io.output.stream
import hep.dataforge.meta.Meta
import hep.dataforge.plots.Plot
import hep.dataforge.plots.data.DataPlot
import hep.dataforge.plots.jfreechart.JFreeChartPlugin
import hep.dataforge.plots.output.plotFrame
import hep.dataforge.stat.fit.FitManager
import hep.dataforge.stat.fit.FitStage
import hep.dataforge.stat.fit.FitState
import hep.dataforge.stat.fit.ParamSet
import hep.dataforge.stat.models.XYModel
import hep.dataforge.step
import hep.dataforge.tables.Adapters.X_AXIS
import hep.dataforge.values.ValueMap
import inr.numass.NumassPlugin
import inr.numass.data.SpectrumAdapter
import inr.numass.data.SpectrumGenerator
import inr.numass.models.NBkgSpectrum
import inr.numass.models.sterile.SterileNeutrinoSpectrum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import java.io.PrintWriter
import kotlin.math.sqrt


fun main() {

    val context = buildContext("NUMASS", NumassPlugin::class.java, FXPlugin::class.java, JFreeChartPlugin::class.java) {
        output = FXOutputManager()
    }

    val spectrum = NBkgSpectrum(SterileNeutrinoSpectrum(context, Meta.empty()))

    val t = 30 * 50 // time in seconds per point

    val params = ParamSet().apply {
        setPar("N", 8e5, 6.0, 0.0, Double.POSITIVE_INFINITY)
        setPar("bkg", 2.0, 0.03)
        setPar("E0", 18575.0, 1.0)
        setPar("mnu2", 0.0, 1.0)
        setParValue("msterile2", (1000 * 1000).toDouble())
        setPar("U2", 1e-2, 1e-3)
        setPar("X", 0.1, 0.01)
        setPar("trap", 1.0, 0.01)
    }

    fun ParamSet.update(vararg override: Pair<String, Double>): ParamSet = this.copy().also { set ->
        override.forEach {
            set.setParValue(it.first, it.second)
        }
    }

//    (14000.0..18600.0 step 10.0).forEach {
//        println("$it\t${spectrum.value(it,params)}")
//    }

    fun plotSpectrum(name: String, vararg override: Pair<String, Double>): Plot {
        val x = (14000.0..18600.0).step(100.0).toList()
        val y = x.map { spectrum.value(it, params.update(*override)) }
        return DataPlot.plot(name, x.toDoubleArray(), y.toDoubleArray())
    }

    fun plotResidual(name: String, vararg override: Pair<String, Double>): Plot {
        val paramsMod = params.update(*override)

        val x = (14000.0..18600.0).step(100.0).toList()
        val y = x.map {
            val base = spectrum.value(it, params)
            val mod = spectrum.value(it, paramsMod)
            val err = sqrt(base / t)
            (mod - base) / err
        }
        return DataPlot.plot(name, x.toDoubleArray(), y.toDoubleArray())
    }

    val adapter = SpectrumAdapter(Meta.empty())
    val fm = context.getOrLoad(FitManager::class.java)

    fun plotFitResidual(name: String, vararg override: Pair<String, Double>): Plot {
        val paramsMod = params.update(*override)

        val x = (14000.0..18400.0).step(100.0).toList()

        val model = XYModel(Meta.empty(), adapter, spectrum)

        val generator = SpectrumGenerator(model, paramsMod, 12316);
        val configuration = x.map { ValueMap.ofPairs(X_AXIS to it, "time" to t) }
        val data = generator.generateData(configuration);

//        val table = ListTable.Builder(Adapters.getFormat(adapter)).apply {
//            x.forEach { u ->
//                row(adapter.buildSpectrumDataPoint(u, t * spectrum.value(u, paramsMod).toLong(), t.toDouble()))
//            }
//        }.build()


        val state = FitState(data, model, params)
        val res = fm.runStage(state, "QOW", FitStage.TASK_RUN, "N", "E0", "bkg")

        res.printState(PrintWriter(System.out))
        res.printState(PrintWriter(context.output["fitResult", name].stream))
        //context.output["fitResult",name].stream

        val y = x.map { u ->
            val base = spectrum.value(u, params)
            val mod = spectrum.value(u, res.parameters)
            val err = sqrt(base / t)
            (mod - base) / err
        }
        return DataPlot.plot(name, x.toDoubleArray(), y.toDoubleArray())
    }


    context.plotFrame("fit", stage = "plots") {
        plots.configure {
            "showLine" to true
            "showSymbol" to false
            "showErrors" to false
            "thickness" to 4.0
        }
        plots.setType<DataPlot>()
        +plotResidual("trap", "trap" to 0.99)
        context.launch(Dispatchers.JavaFx) {
            try {
                +plotFitResidual("trap_fit", "trap" to 0.99)
            } catch (ex: Exception) {
                context.logger.error("Failed to do fit", ex)
            }
        }
        +plotResidual("X", "X" to 0.11)
        context.launch(Dispatchers.JavaFx) {
            +plotFitResidual("X_fit", "X" to 0.11)
        }
        +plotResidual("sterile_1", "U2" to 1e-3)
        context.launch(Dispatchers.Main) {
            +plotFitResidual("sterile_1_fit", "U2" to 1e-3)
        }
        +plotResidual("sterile_3", "msterile2" to (3000 * 3000).toDouble(), "U2" to 1e-3)
        context.launch(Dispatchers.JavaFx) {
            +plotFitResidual("sterile_3_fit", "msterile2" to (3000 * 3000).toDouble(), "U2" to 1e-3)
        }

    }

}