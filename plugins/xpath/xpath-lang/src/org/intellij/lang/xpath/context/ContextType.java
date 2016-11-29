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

import java.util.HashMap;
import java.util.Map;

public final class ContextType {
    private static final Map<String, ContextType> ourRegistry = new HashMap<>();

    public static final ContextType PLAIN = lookupOrCreate("PLAIN", XPathVersion.V1);
    public static final ContextType PLAIN_V2 = lookupOrCreate("PLAIN_V2", XPathVersion.V2);

    /** @deprecated left here for compatibility with IntelliLang */
    public static final ContextType INTERACTIVE = lookupOrCreate("INTERACTIVE");

    private final String myName;
    private final XPathVersion myVersion;

    public ContextType(String name, XPathVersion version) {
        assert !ourRegistry.containsKey(name);
        ourRegistry.put(name, this);

        myName = name;
        myVersion = version;
    }

    public static synchronized ContextType valueOf(String name) {
        final ContextType t = ourRegistry.get(name);
        if (t == null) throw new IllegalArgumentException(name);
        return t;
    }

    public static synchronized ContextType lookupOrCreate(String name, XPathVersion version) {
      return ourRegistry.containsKey(name) ? ourRegistry.get(name) : new ContextType(name, version);
    }

    public static synchronized ContextType lookupOrCreate(String name) {
        return ourRegistry.containsKey(name) ? ourRegistry.get(name) : new ContextType(name, XPathVersion.V1);
    }

    public static ContextType fromFile(XPathFile file){
        return ContextProvider.getContextProvider(file).getContextType();
    }

    public XPathVersion getVersion() {
      return myVersion;
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
