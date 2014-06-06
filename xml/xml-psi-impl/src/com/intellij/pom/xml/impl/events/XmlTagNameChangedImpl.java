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
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.pom.xml.events.XmlTagNameChanged;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class XmlTagNameChangedImpl implements XmlTagNameChanged {
  private final String myOldName;
  private final XmlTag myTag;

  public XmlTagNameChangedImpl(XmlTag tag, String oldName) {
    myOldName = oldName;
    myTag = tag;
  }

  @Override
  public String getOldName() {
    return myOldName;
  }

  @Override
  public XmlTag getTag() {
    return myTag;
  }

  public static PomModelEvent createXmlTagNameChanged(PomModel model, XmlTag tag, String oldName) {
    final PomModelEvent event = new PomModelEvent(model);
    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(model, PsiTreeUtil.getParentOfType(tag, XmlFile.class));
    xmlAspectChangeSet.add(new XmlTagNameChangedImpl(tag, oldName));
    event.registerChangeSet(model.getModelAspect(XmlAspect.class), xmlAspectChangeSet);
    return event;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "tag name changed to " + getTag().getName() + " was: " + getOldName();
  }
}
