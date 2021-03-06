/* 
 * Copyright 2015 Alexander Nozik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package inr.numass.scripts

import hep.dataforge.context.Global
import hep.dataforge.grind.GrindMetaBuilder
import hep.dataforge.io.FittingIOUtils
import hep.dataforge.meta.Meta
import hep.dataforge.stat.fit.ParamSet
import hep.dataforge.stat.models.XYModel
import hep.dataforge.stat.parametric.ParametricFunction
import inr.numass.data.SpectrumAdapter
import inr.numass.models.NBkgSpectrum
import inr.numass.models.sterile.SterileNeutrinoSpectrum

import static java.util.Locale.setDefault

/**
 *
 * @author Darksnake
 */

setDefault(Locale.US);

//ParametricFunction beta = new BetaSpectrum();

//ModularSpectrum beta = new ModularSpectrum(new BetaSpectrum(), 8.3e-5, 13990d, 18600d);
//beta.setCaching(false)

Meta cfg = new GrindMetaBuilder().meta() {
    resolution(width: 8.3e-5)
}.build();

ParametricFunction beta = new SterileNeutrinoSpectrum(Global.instance(), cfg);

NBkgSpectrum spectrum = new NBkgSpectrum(beta);
XYModel model = new XYModel(spectrum, new SpectrumAdapter());

ParamSet allPars = new ParamSet();

allPars.setPar("N", 6.6579e+05, 1.8e+03, 0d, Double.POSITIVE_INFINITY);
allPars.setPar("bkg", 0.5387, 0.050);
allPars.setPar("E0", 18574.94, 1.4);
allPars.setPar("mnu2", 0d, 1d);
allPars.setPar("msterile2", 1000d * 1000d, 0);
allPars.setPar("U2", 0.0, 1e-4, -1d, 1d);
allPars.setPar("X", 0.04, 0.01, 0d, Double.POSITIVE_INFINITY);
allPars.setPar("trap", 1.634, 0.01, 0d, Double.POSITIVE_INFINITY);

FittingIOUtils.printSpectrum(Global.out(), spectrum, allPars, 14000, 18600.0, 400);

//SpectrumGenerator generator = new SpectrumGenerator(model, allPars, 12316);
//
//ListTable data = generator.generateData(DataModelUtils.getUniformSpectrumConfiguration(14000d, 18500, 2000, 90));
//
//data = NumassUtils.correctForDeadTime(data, new SpectrumAdapter(), 1e-8);
////        data = data.filter("X", Value.of(15510.0), Value.of(18610.0));
////        allPars.setParValue("X", 0.4);
//FitState state = new FitState(data, model, allPars);
////new PlotFitResultAction().eval(state);
//        
//        
//FitState res = fm.runStage(state, "QOW", FitTask.TASK_RUN, "N", "bkg", "E0", "U2", "trap");
//
//        
//
//res.print(onComplete());
//
