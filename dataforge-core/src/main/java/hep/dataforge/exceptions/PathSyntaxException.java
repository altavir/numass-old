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
package hep.dataforge.exceptions;

/**
 *  Ошибка синтаксиса пути
 *
 * @author Alexander Nozik
 * @version $Id: $Id
 */
public class PathSyntaxException extends NamingException {

    /**
     * Creates a new instance of <code>PathSyntaxException</code> without detail
     * message.
     */
    public PathSyntaxException() {
    }

    /**
     * Constructs an instance of <code>PathSyntaxException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public PathSyntaxException(String msg) {
        super(msg);
    }
}
