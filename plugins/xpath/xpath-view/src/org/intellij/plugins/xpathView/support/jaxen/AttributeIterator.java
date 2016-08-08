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
package org.intellij.plugins.xpathView.support.jaxen;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class AttributeIterator implements Iterator {

    private final Iterator<XmlAttribute> theIterator;

    public AttributeIterator(XmlElement parent) {
        this.theIterator = filterNamespaceAttrs(((XmlTag)parent).getAttributes());
    }

    private Iterator<XmlAttribute> filterNamespaceAttrs(XmlAttribute[] attributes) {
        final List<XmlAttribute> attrs = new ArrayList<>(attributes.length);
        for (XmlAttribute attribute : attributes) {
            final String name = attribute.getName();
            if (!name.startsWith("xmlns:") && !name.equals("xmlns")) {
                attrs.add(attribute);
            }
        }
        return attrs.iterator();
    }

    /**
     * @see Iterator#remove
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public boolean hasNext() {
        return theIterator.hasNext();
    }

    public Object next() {
        return theIterator.next();
    }
}
