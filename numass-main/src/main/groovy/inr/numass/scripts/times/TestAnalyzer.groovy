package inr.numass.scripts.times

import hep.dataforge.context.Context
import hep.dataforge.context.Global
import hep.dataforge.grind.Grind
import hep.dataforge.grind.GrindShell
import hep.dataforge.kodex.fx.plots.PlotManager
import inr.numass.NumassPlugin
import inr.numass.actions.TimeAnalyzedAction
import inr.numass.data.SimpleChainGenerator
import inr.numass.data.api.SimpleNumassPoint
import org.apache.commons.math3.random.JDKRandomGenerator

import java.time.Instant

/**
 * Created by darksnake on 27-Jun-17.
 */


Context ctx = Global.instance()
ctx.pluginManager().load(PlotManager)
ctx.pluginManager().load(NumassPlugin.class)

new GrindShell(ctx).eval {

    double cr = 15e3;
    long length = 30e9;
    def num = 5;



    def blocks = (1..num).collect {
        def generator = new SimpleChainGenerator(1e4 + 1000*num, new JDKRandomGenerator(), { 1000 })
        generator.generateBlock(Instant.now().plusNanos(it * length), length)
    }

    def point = new SimpleNumassPoint(10000, blocks)

    def meta = Grind.buildMeta(plotHist: false)

    new TimeAnalyzedAction().simpleRun(point, meta);
}