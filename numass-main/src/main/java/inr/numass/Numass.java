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
package inr.numass;

import hep.dataforge.context.Context;
import hep.dataforge.context.ContextBuilder;
import hep.dataforge.context.Global;
import hep.dataforge.exceptions.DescriptorException;
import hep.dataforge.meta.Meta;

/**
 * @author Darksnake
 */
public class Numass {

    public static Context buildContext(Context parent, Meta meta) {
        return new ContextBuilder("NUMASS", parent)
                .properties(meta)
                .plugin(NumassPlugin.class)
                .build();
    }

    public static Context buildContext() {
        return buildContext(Global.INSTANCE, Meta.empty());
    }

    public static void printDescription(Context context) throws DescriptorException {

//        MarkupBuilder builder = new MarkupBuilder()
//                .text("***Data description***", "red")
//                .ln()
//                .text("\t")
//                .content(
//                        MarkupUtils.markupDescriptor(Descriptors.buildDescriptor("method::hep.dataforge.data.DataManager.read"))
//                )
//                .ln()
//                .text("***Allowed actions***", "red")
//                .ln();
//
//
//        ActionManager am = context.get(ActionManager.class);
//
//        am.listActions()
//                .map(name -> am.optAction(name).get())
//                .map(ActionDescriptor::build).forEach(descriptor ->
//                builder.text("\t").content(MarkupUtils.markupDescriptor(descriptor))
//        );
//
//        builder.text("***End of actions list***", "red");
//
//
//        context.getIo().getOutput().render(builder.build(), Meta.empty());
    }
}
