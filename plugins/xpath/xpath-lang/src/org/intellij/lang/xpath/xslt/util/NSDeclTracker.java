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
package org.intellij.lang.xpath.xslt.util;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class NSDeclTracker implements ModificationTracker {
    private static final Key<Integer> MOD_COUNT = Key.create("MOD_COUNT");

    private final XmlTagImpl myRootTag;
    private final List<XmlAttribute> myNSDecls;
    private int myRootCount;
    private int myCount;

    public NSDeclTracker(XmlTag rootTag) {
        myRootTag = (XmlTagImpl)rootTag;
        myNSDecls = getNSDecls(false);
        myRootCount = myRootTag.getModificationCount();
        myCount = 0;
    }

    public long getModificationCount() {
        return myRootTag.getModificationCount() == myRootCount ? myCount : queryCount();
    }

    @SuppressWarnings({ "AutoUnboxing" })
    private synchronized long queryCount() {
        for (XmlAttribute decl : myNSDecls) {
            if (!decl.isValid()) {
                return update();
            }
            final Integer modCount = decl.getUserData(MOD_COUNT);
            if (modCount != null && ((XmlAttributeImpl)decl).getModificationCount() != modCount) {
                return update();
            }
        }
        final ArrayList<XmlAttribute> list = getNSDecls(false);
        if (!list.equals(myNSDecls)) {
            return update();
        }

        myRootCount = myRootTag.getModificationCount();
        return myCount;
    }

    private long update() {
        myNSDecls.clear();
        myNSDecls.addAll(getNSDecls(true));
        myRootCount = myRootTag.getModificationCount();
        return ++myCount;
    }

    private ArrayList<XmlAttribute> getNSDecls(boolean updateModCount) {
        final ArrayList<XmlAttribute> list = new ArrayList<>(Arrays.asList(myRootTag.getAttributes()));
        final Iterator<XmlAttribute> it = list.iterator();
        while (it.hasNext()) {
            final XmlAttribute attribute = it.next();
            if (!attribute.isNamespaceDeclaration()) it.remove();
            if (updateModCount) {
                attribute.putUserData(MOD_COUNT, ((XmlAttributeImpl)attribute).getModificationCount());
            }
        }
        return list;
    }
}
