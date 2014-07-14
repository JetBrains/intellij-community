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
import com.intellij.pom.xml.events.XmlDocumentChanged;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class XmlDocumentChangedImpl implements XmlDocumentChanged {
  private final XmlDocument myDocument;

  public XmlDocumentChangedImpl(@NotNull XmlDocument document) {
    myDocument = document;
  }

  @Override
  public XmlDocument getDocument() {
    return myDocument;
  }

  public static PomModelEvent createXmlDocumentChanged(PomModel source, XmlDocument document) {
    final PomModelEvent event = new PomModelEvent(source);
    XmlFile xmlFile = PsiTreeUtil.getParentOfType(document, XmlFile.class);
    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(source, xmlFile);
    xmlAspectChangeSet.add(new XmlDocumentChangedImpl(document));
    event.registerChangeSet(source.getModelAspect(XmlAspect.class), xmlAspectChangeSet);
    return event;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Xml document changed";
  }
}
