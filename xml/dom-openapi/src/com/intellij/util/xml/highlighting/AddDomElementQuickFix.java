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
import com.intellij.util.xml.DomBundle;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class AddDomElementQuickFix<T extends DomElement> implements LocalQuickFix {

  protected final T myElement;
  protected final String myName;

  public AddDomElementQuickFix(@NotNull T element) {
    myElement = element.createStableCopy();
    myName = computeName();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  private String computeName() {
    final String name = myElement.getXmlElementName();
    return DomBundle.message(isTag() ? "add.element.fix.name" : "add.attribute.fix.name", name);
  }

  private boolean isTag() {
    return myElement.getXmlElement() instanceof XmlTag;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return DomBundle.message("add.element.fix.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    myElement.ensureXmlElementExists();
  }
}