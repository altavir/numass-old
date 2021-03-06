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
package inr.numass.models;

import hep.dataforge.io.IOUtils;
import hep.dataforge.tables.ValuesSource;
import hep.dataforge.values.Values;

import java.io.InputStream;
import java.util.ArrayList;

/**
 * @author Darksnake
 */
public class FSS {
    private final ArrayList<Double> ps = new ArrayList<>();
    private final ArrayList<Double> es = new ArrayList<>();
    private double norm;

    public FSS(InputStream stream) {
        ValuesSource data = IOUtils.readColumnedData(stream, "E", "P");
        norm = 0;
        for (Values dp : data) {
            es.add(dp.getDouble("E"));
            double p = dp.getDouble("P");
            ps.add(p);
            norm += p;
        }
        if (ps.isEmpty()) {
            throw new RuntimeException("Error reading FSS FILE. No points.");
        }
    }

    public double getE(int n) {
        return this.es.get(n);
    }

    public double getP(int n) {
        return this.ps.get(n) / norm;
    }

    public boolean isEmpty() {
        return ps.isEmpty();
    }

    public int size() {
        return ps.size();
    }

    public double[] getPs() {
        return ps.stream().mapToDouble(p -> p).toArray();
    }

    public double[] getEs() {
        return es.stream().mapToDouble(p -> p).toArray();
    }
}
