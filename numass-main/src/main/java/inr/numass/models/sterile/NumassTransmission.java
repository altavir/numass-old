/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package inr.numass.models.sterile;

import hep.dataforge.fitting.parametric.AbstractParametricBiFunction;
import hep.dataforge.meta.Meta;
import hep.dataforge.values.NamedValueSet;
import inr.numass.models.LossCalculator;
import org.apache.commons.math3.analysis.BivariateFunction;

/**
 *
 * @author Alexander Nozik <altavir@gmail.com>
 */
public class NumassTransmission extends AbstractParametricBiFunction {

    private static final String[] list = {"trap", "X"};
    private static final double ION_POTENTIAL = 15.4;//eV
    private final LossCalculator calculator;
    private final BivariateFunction trapFunc;

//    private BicubicInterpolatingFunction cache;
//    private double cachedX;
//    private Meta cacheMeta;

    public NumassTransmission(Meta meta) {
        super(list);
        this.calculator = LossCalculator.instance();
        trapFunc = getTrapFunction(meta.getNodeOrEmpty("trapping"));
//        if (meta.hasNode("cache")) {
//            cacheMeta = meta.getNode("cache");
//        }
    }

    private BivariateFunction getTrapFunction(Meta meta) {
        return LossCalculator.getTrapFunction();
    }

    @Override
    public double derivValue(String parName, double eIn, double eOut, NamedValueSet set) {
        switch (parName) {
            case "trap":
                return trapFunc.value(eIn, eOut);
            case "X":
                return calculator.getTotalLossDeriv(getX(eIn, set), eIn, eOut);
            default:
                return super.derivValue(parName, eIn, eOut, set);
        }
    }

    @Override
    public boolean providesDeriv(String name) {
        return true;
    }

    public static double getX(double eIn, NamedValueSet set) {
        //From our article
        return set.getDouble("X") * Math.log(eIn / ION_POTENTIAL) * eIn * ION_POTENTIAL / 1.9580741410115568e6;
    }

    public static double p0(double eIn, NamedValueSet set) {
        return LossCalculator.instance().getLossProbability(0, getX(eIn, set));
    }

//    private synchronized void setupCache(double X) {
//        if (this.cachedX != X) {
//            double cacheLo = cacheMeta.getDouble("lo", 14000);
//            double cacheHi = cacheMeta.getDouble("hi", 18575);
//            int numPoints = cacheMeta.getInt("numPoints", 1000);
//            double[] eIns = GridCalculator.getUniformUnivariateGrid(cacheLo, cacheHi, numPoints);
//            double[] eOuts = GridCalculator.getUniformUnivariateGrid(cacheLo, cacheHi, numPoints);
//            double[][] vals = MathUtils.calculateFunction(calculator.getTotalLossBivariateFunction(X), eOuts, eOuts);
//            this.cachedX = X;
//            this.cache = new BicubicInterpolator().interpolate(eIns, eOuts, vals);
//        }
//    }
    @Override
    public double value(double eIn, double eOut, NamedValueSet set) {
        //calculate X taking into account its energy dependence
        double X = getX(eIn, set);
        // loss part
        double loss = calculator.getTotalLossValue(X, eIn, eOut);
        //trapping part
        double trap = getParameter("trap", set) * trapFunc.value(eIn, eOut);
        return loss + trap;
    }

}
