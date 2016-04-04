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

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.xml.DomBundle;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class RemoveDomElementQuickFix implements LocalQuickFix {

  private final boolean myIsTag;
  private final String myName;

  public RemoveDomElementQuickFix(@NotNull DomElement element) {
    myIsTag = element.getXmlElement() instanceof XmlTag;
    myName = element.getXmlElementName();
  }

  @Override
  @NotNull
  public String getName() {
    return DomBundle.message(myIsTag ? "remove.element.fix.name" : "remove.attribute.fix.name", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return DomBundle.message("remove.element.fix.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (myIsTag) {
      final XmlTag tag = (XmlTag)descriptor.getPsiElement();
      final XmlTag parentTag = tag.getParentTag();
      final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);
      assert domElement != null;
      domElement.undefine();
      if (parentTag != null && parentTag.isValid()) {
        parentTag.collapseIfEmpty();
      }
    } else {
      final DomElement domElement = DomManager.getDomManager(project).getDomElement((XmlAttribute)descriptor.getPsiElement());
      assert domElement != null;
      domElement.undefine();
    }
  }
}
