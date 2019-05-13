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

import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.events.XmlAttributeSet;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class XmlAttributeSetImpl implements XmlAttributeSet {
  private final String myName;
  private final String myValue;
  private final XmlTag myTag;

  public XmlAttributeSetImpl(XmlTag xmlTag, String name, String value) {
    myName = name;
    myValue = value;
    myTag = xmlTag;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getValue() {
    return myValue;
  }

  @Override
  public XmlTag getTag() {
    return myTag;
  }

  public static PomModelEvent createXmlAttributeSet(PomModel model, XmlTag xmlTag, String name, String value) {
    final PomModelEvent event = new PomModelEvent(model);
    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(model, PsiTreeUtil.getParentOfType(xmlTag, XmlFile.class));
    xmlAspectChangeSet.add(new XmlAttributeSetImpl(xmlTag, name, value));
    event.registerChangeSet(model.getModelAspect(XmlAspect.class), xmlAspectChangeSet);
    return event;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Attribute \"" + getName() + "\" for tag \"" + getTag().getName() + "\" set to \"" + getValue() + "\"";
  }
}
