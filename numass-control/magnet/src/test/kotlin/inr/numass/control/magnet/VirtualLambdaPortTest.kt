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

package inr.numass.control.magnet

import hep.dataforge.context.Global
import hep.dataforge.meta.buildMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualLambdaPortTest{
    val magnetMeta = buildMeta {
        node("magnet") {
            "address" to 2
        }
    }

    @Test
    fun testSendOk(){
        val port = VirtualLambdaPort(magnetMeta)
        val controller = LambdaPortController(Global,port)
        controller.setAddress(2)
        assertEquals(2,port.currentAddress)
    }
}