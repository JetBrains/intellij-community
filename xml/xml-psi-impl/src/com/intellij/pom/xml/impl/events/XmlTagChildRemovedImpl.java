/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.pom.xml.impl.events;

import com.intellij.pom.xml.events.XmlTagChildRemoved;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;

public class XmlTagChildRemovedImpl implements XmlTagChildRemoved {
  private final XmlTag myTag;
  private final XmlTagChild myChild;
  public XmlTagChildRemovedImpl(XmlTag context, XmlTagChild treeElement) {
    myTag = context;
    myChild = treeElement;
  }

  @Override
  public XmlTag getTag() {
    return myTag;
  }

  @Override
  public XmlTagChild getChild() {
    return myChild;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "child removed from " + getTag().getName() + " child: " + myChild.toString();
  }
}
