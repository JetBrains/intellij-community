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
package org.intellij.lang.xpath.context;

import org.intellij.lang.xpath.XPathFile;

import java.util.Map;
import java.util.HashMap;

public final class ContextType {
    private static final Map<String, ContextType> ourRegistry = new HashMap<String, ContextType>();

    public static final ContextType PLAIN = lookupOrCreate("PLAIN");

    /** @deprecated left here for compatibility with IntelliLang */
    public static final ContextType INTERACTIVE = lookupOrCreate("INTERACTIVE");

    private final String myName;

    private ContextType(String name) {
        assert !ourRegistry.containsKey(name);
        myName = name;
        ourRegistry.put(name, this);
    }

    public static synchronized ContextType valueOf(String name) {
        final ContextType t = ourRegistry.get(name);
        if (t == null) throw new IllegalArgumentException(name);
        return t;
    }

    public static synchronized ContextType lookupOrCreate(String name) {
        return ourRegistry.containsKey(name) ? ourRegistry.get(name) : new ContextType(name);
    }

    public static ContextType fromFile(XPathFile file){
        return ContextProvider.getContextProvider(file).getContextType();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ContextType that = (ContextType)o;

        if (!myName.equals(that.myName)) return false;

        return true;
    }

    public int hashCode() {
        return myName.hashCode();
    }

    public String toString() {
        return myName;
    }
}
