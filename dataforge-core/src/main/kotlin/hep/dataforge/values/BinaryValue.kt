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

package hep.dataforge.values

import java.nio.ByteBuffer
import java.time.Instant

class BinaryValue(override val value: ByteBuffer): AbstractValue(){
    override val number: Number
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val boolean: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val time: Instant
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val string: String
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override val type: ValueType = ValueType.BINARY
}