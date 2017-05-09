/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package inr.numass.readvac.devices;

import hep.dataforge.control.devices.PortSensor;
import hep.dataforge.control.measurements.Measurement;
import hep.dataforge.control.measurements.SimpleMeasurement;
import hep.dataforge.control.ports.PortHandler;
import hep.dataforge.description.ValueDef;
import hep.dataforge.exceptions.ControlException;

/**
 *
 * @author Alexander Nozik
 */
@ValueDef(name = "channel")
public class MKSBaratronDevice extends PortSensor<Double> {


    @Override
    protected Measurement<Double> createMeasurement() {
        return new BaratronMeasurement();
    }

    @Override
    public String type() {
        return meta().getString("type", "MKS baratron");
    }

    @Override
    protected PortHandler buildHandler(String portName) throws ControlException {
        PortHandler handler = super.buildHandler(portName);
        handler.setDelimeter("\r");
        return handler;
    }

    private int getChannel() {
        return meta().getInt("channel", 2);
    }

    private class BaratronMeasurement extends SimpleMeasurement<Double> {

        @Override
        protected synchronized Double doMeasure() throws Exception {
            String answer = getHandler().sendAndWait("AV" + getChannel() + "\r", timeout());
            if (answer == null || answer.isEmpty()) {
//                invalidateState("connection");
                updateState(CONNECTION_STATE, false);
                this.progressUpdate("No connection");
                return null;
            } else {
                updateState(CONNECTION_STATE, true);
            }
            double res = Double.parseDouble(answer);
            if (res <= 0) {
                this.progressUpdate("Non positive");
//                invalidateState("power");
                return null;
            } else {
                this.progressUpdate("OK");
                return res;
            }
        }

    }
}
