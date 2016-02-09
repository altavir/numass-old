/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package inr.numass.readvac.fx;

import hep.dataforge.control.connections.Roles;
import hep.dataforge.control.devices.Device;
import hep.dataforge.control.devices.DeviceListener;
import hep.dataforge.control.measurements.Measurement;
import hep.dataforge.control.measurements.MeasurementListener;
import hep.dataforge.data.DataPoint;
import hep.dataforge.meta.Meta;
import hep.dataforge.meta.MetaBuilder;
import hep.dataforge.plots.PlotFrame;
import hep.dataforge.plots.data.DynamicPlottable;
import hep.dataforge.plots.data.DynamicPlottableSet;
import hep.dataforge.plots.fx.PlotContainer;
import hep.dataforge.plots.jfreechart.JFreeChartFrame;
import hep.dataforge.values.Value;
import inr.numass.readvac.devices.MKSVacDevice;
import inr.numass.readvac.devices.VacCollectorDevice;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

/**
 * FXML Controller class
 *
 * @author Alexander Nozik <altavir@gmail.com>
 */
public class VacCollectorController implements Initializable, DeviceListener, MeasurementListener<DataPoint> {

    private VacCollectorDevice device;
    private final List<VacuumeterView> views = new ArrayList<>();
    private PlotContainer plotContainer;
    private DynamicPlottableSet plottables;

    @FXML
    private AnchorPane plotHolder;
    @FXML
    private HBox vacBoxHolder;

    @Override
    public void evaluateDeviceException(Device device, String message, Throwable exception) {

    }

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        plotContainer = PlotContainer.anchorTo(plotHolder);
    }

    public VacCollectorDevice getDevice() {
        return device;
    }

    @Override
    public void notifyDeviceStateChanged(Device device, String name, Value state) {

    }

    @Override
    public void onMeasurementFailed(Measurement measurement, Throwable exception) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onMeasurementResult(Measurement<DataPoint> measurement, DataPoint result, Instant time) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void setupView() {
        vacBoxHolder.getChildren().clear();
        plottables = new DynamicPlottableSet();
        views.stream().forEach((controller) -> {
            vacBoxHolder.getChildren().add(controller.getComponent());
            plottables.addPlottable(new DynamicPlottable(controller.getTitle(), 
                    controller.meta(), controller.getName()));
        });
        plotContainer.setPlot(setupPlot(plottables));
    }

    private PlotFrame setupPlot(DynamicPlottableSet plottables) {
        Meta plotConfig = new MetaBuilder("plotFrame")
                .setNode(new MetaBuilder("yAxis")
                        .setValue("logAxis", true)
                        .setValue("axisTitle", "pressure")
                        .setValue("axisUnits", "mbar")
                )
                .setValue("xAxis.timeAxis", true);
        JFreeChartFrame frame = new JFreeChartFrame("pressure", plotConfig);
        frame.addAll(plottables);
        return frame;
    }

    public void setDevice(VacCollectorDevice device) {
        this.device = device;
        device.getSensors().stream().map((sensor) -> {
            VacuumeterView controller;
            if (sensor instanceof MKSVacDevice) {
                controller = new PoweredVacuumeterView();
            } else {
                controller = new VacuumeterView();
            }
            sensor.connect(controller, Roles.DEVICE_LISTENER_ROLE, Roles.MEASUREMENT_CONSUMER_ROLE);
            return controller;
        }).forEach((controller) -> {
            views.add(controller);
        });
        setupView();
    }
    
    private void startMeasurement(){
        getDevice().startMeasurement();
    }

}
