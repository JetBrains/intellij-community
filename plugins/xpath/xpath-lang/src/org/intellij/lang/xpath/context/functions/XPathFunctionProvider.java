/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.context.functions;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.context.ContextType;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class XPathFunctionProvider {
    public static final ExtensionPointName<XPathFunctionProvider> EXTENSION_POINT_NAME =
            ExtensionPointName.create("XPathView.xpath.functionProvider");

    @NotNull
    public abstract Map<QName, ? extends Function> getFunctions(ContextType contextType);

    public static List<Pair<QName, ? extends Function>> getAvailableFunctions(ContextType type) {
        final XPathFunctionProvider[] components = Extensions.getExtensions(EXTENSION_POINT_NAME);
        final ArrayList<Pair<QName, ? extends Function>> list = new ArrayList<>();
        for (XPathFunctionProvider provider : components) {
            final Map<QName, ? extends Function> functions = provider.getFunctions(type);

            final Set<QName> names = functions.keySet();
            for (QName name : names) {
                list.add(Pair.create(name, functions.get(name)));
            }
        }
        return list;
    }
}
