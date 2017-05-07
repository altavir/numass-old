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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.adapter.JavaBeanBooleanPropertyBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Alexander Nozik
 */
@ValueDef(name = "address", def = "253")
@ValueDef(name = "channel", def = "5")
@ValueDef(name = "powerButton", type = "BOOLEAN", def = "true")
public class MKSVacDevice extends PortSensor<Double> {

    public MKSVacDevice(String portName) {
        super(portName);
        super.getConfig().setValue("powerButton", true);
    }

    private String talk(String requestContent) throws ControlException {
        String answer = getHandler().sendAndWait(String.format("@%s%s;FF", getDeviceAddress(), requestContent), timeout());

        Matcher match = Pattern.compile("@" + getDeviceAddress() + "ACK(.*);FF").matcher(answer);
        if (match.matches()) {
            return match.group(1);
        } else {
            throw new ControlException(answer);
        }
    }

    @Override
    protected PortHandler buildHandler(String portName) throws ControlException {
        PortHandler handler = super.buildHandler(portName);
        handler.setDelimeter(";FF");
        return handler;
    }

    private String getDeviceAddress() {
        //PENDING cache this?
        return meta().getString("address", "253");
    }

    @Override
    protected Measurement<Double> createMeasurement() {
        return new MKSVacMeasurement();
    }

    @Override
    protected Object computeState(String stateName) throws ControlException {
        if (getHandler() == null) {
            notifyError("No port connection", null);
            return null;
        }
        switch (stateName) {
            case "connection":
                return !talk("T?").isEmpty();
            case "power":
                return talk("FP?").equals("ON");
            default:
                notifyError("State not found: " + stateName, null);
                return null;
        }
    }

//    @Override
//    public void command(String commandName, Value argument) throws ControlException {
//        if (commandName.equals("setPower")) {
//            boolean powerOn = argument.booleanValue();
//            setPowerOn(powerOn);
//        } else {
//            super.command(commandName, argument);
//        }
//    }

    @Override
    public void shutdown() throws ControlException {
        setPowerOn(false);
        super.shutdown();
    }

    
    
    public boolean isPowerOn() {
        return getState("power").booleanValue();
    }

    public void setPowerOn(boolean powerOn) throws ControlException {
        if (powerOn != isPowerOn()) {
            if (powerOn) {
//                String ans = talkMKS(p1Port, "@253ENC!OFF;FF");
//                if (!ans.equals("OFF")) {
//                    LoggerFactory.getLogger(getClass()).warn("The @253ENC!OFF;FF command is not working");
//                }
                String ans = talk("FP!ON");
                if (ans.equals("ON")) {
                    updateState("power", true);
                } else {
                    this.notifyError("Failed to set power state", null);
                }
            } else {
                String ans = talk("FP!OFF");
                if (ans.equals("OFF")) {
                    updateState("power", false);
                } else {
                    this.notifyError("Failed to set power state", null);
                }
            }
        }
    }

    public BooleanProperty powerOnProperty() {
        try {
            return new JavaBeanBooleanPropertyBuilder().bean(this)
                    .name("powerOn").getter("isPowerOn").setter("setPowerOn").build();
        } catch (NoSuchMethodException ex) {
            throw new Error(ex);
        }
    }

    @Override
    public String type() {
        return meta().getString("type", "MKS vacuumeter");
    }

    private int getChannel() {
        return meta().getInt("channel", 5);
    }

    private class MKSVacMeasurement extends SimpleMeasurement<Double> {

        @Override
        protected synchronized Double doMeasure() throws Exception {
//            if (getState("power").booleanValue()) {
                String answer = talk("PR" + getChannel() + "?");
                if (answer == null || answer.isEmpty()) {
                    invalidateState(CONNECTION_STATE);
                    this.progressUpdate("No connection");
                    return null;
                }
                double res = Double.parseDouble(answer);
                if (res <= 0) {
                    this.progressUpdate("No power");
                    invalidateState("power");
                    return null;
                } else {
                    this.progressUpdate("OK");
                    return res;
                }
//            } else {
//                return null;
//            }
        }

    }
}
