/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package inr.numass.models.sterile;

import hep.dataforge.fitting.parametric.AbstractParametricBiFunction;
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

    public NumassTransmission() {
        super(list);
        this.calculator = LossCalculator.instance();
        trapFunc = LossCalculator.getTrapFunction();
    }

    public NumassTransmission(BivariateFunction trapFunc) {
        super(list);
        this.calculator = LossCalculator.instance();
        this.trapFunc = trapFunc;
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

    private double getX(double eIn, NamedValueSet set) {
        //From our article
        return Math.log(eIn / ION_POTENTIAL) * eIn * ION_POTENTIAL / 1.9580741410115568e6;
    }

    @Override
    public double value(double eIn, double eOut, NamedValueSet set) {
        return calculator.getTotalLossValue(getX(eIn, set), eIn, eOut) + getParameter("trap", set) * trapFunc.value(eIn, eOut);
    }

}