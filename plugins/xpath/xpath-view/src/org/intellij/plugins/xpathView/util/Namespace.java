/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.util;

import java.util.*;

public final class Namespace implements Cloneable, Copyable<Namespace> {
    public String prefix;
    public final String uri;

    public Namespace(String prefix, String uri) {
        this.prefix = prefix;
        this.uri = uri;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getUri() {
        return uri;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Namespace namespace = (Namespace)o;

        return !(uri != null ? !uri.equals(namespace.uri) : namespace.uri != null);
    }

    public int hashCode() {
        return (uri != null ? uri.hashCode() : 0);
    }

    protected Namespace clone() {
        try {
            return (Namespace)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error();
        }
    }

    public Namespace copy() {
        return clone();
    }

    public static Collection<Namespace> fromMap(Map<String, String> namespaces) {
        final List<Namespace> list = new ArrayList<>(namespaces.size());
        for (Map.Entry<String, String> e : namespaces.entrySet()) {
            list.add(new Namespace(e.getKey(), e.getValue()));
        }
        return list;
    }

    public static Map<String, String> makeMap(Collection<Namespace> namespaces) {
        final Map<String, String> _ns = new HashMap<>();
        for (Namespace namespace : namespaces) {
            _ns.put(namespace.getPrefix(), namespace.getUri());
        }
        return _ns;
    }
}
