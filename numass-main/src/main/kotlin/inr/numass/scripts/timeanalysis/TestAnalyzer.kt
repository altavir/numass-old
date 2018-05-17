package inr.numass.scripts.timeanalysis

import hep.dataforge.fx.plots.FXPlotManager
import hep.dataforge.kodex.buildMeta
import hep.dataforge.maths.chain.MarkovChain
import inr.numass.NumassPlugin
import inr.numass.actions.TimeAnalyzerAction
import inr.numass.data.api.OrphanNumassEvent
import inr.numass.data.api.SimpleNumassPoint
import inr.numass.data.generateBlock
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.channels.toList
import kotlinx.coroutines.experimental.runBlocking
import org.apache.commons.math3.random.JDKRandomGenerator
import org.apache.commons.math3.random.RandomGenerator
import java.time.Instant

fun main(args: Array<String>) {
    FXPlotManager().startGlobal()
    NumassPlugin().startGlobal()

    val cr = 30e3
    val length = 30e9.toLong()
    val num = 4
    val dt = 6.5

    val rnd = JDKRandomGenerator()

    fun RandomGenerator.nextExp(mean: Double): Double {
        return -mean * Math.log(1 - nextDouble())
    }

    fun RandomGenerator.nextDeltaTime(cr: Double): Long {
        return (nextExp(1.0 / cr) * 1e9).toLong()
    }


    val start = Instant.now()

    //TODO make parallel
    val blockChannel = produce {
        (1..num).forEach {
            send(
                    MarkovChain(OrphanNumassEvent(1000, 0)) { event ->
                        //val deltaT = rnd.nextDeltaTime(cr * exp(- event.timeOffset / 1e11))
                        val deltaT = rnd.nextDeltaTime(cr)
                        OrphanNumassEvent(1000, event.timeOffset + deltaT)
                    }.generateBlock(start.plusNanos(it * length), length)
            )
        }
    }

    val blocks = runBlocking {
        blockChannel.toList()
    }


    val point = SimpleNumassPoint(blocks, 12000.0)

    val meta = buildMeta {
        "t0" to 3000
        "binNum" to 200
        "t0Step" to 200
        "chunkSize" to 5000
    }

    TimeAnalyzerAction().simpleRun(point, meta);
}