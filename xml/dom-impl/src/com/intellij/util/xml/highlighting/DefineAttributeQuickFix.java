/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefineAttributeQuickFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.spring.model.highlighting.DefineAttributeQuickFix");
  private final String myAttrName;
  private final String myNamespace;

  public DefineAttributeQuickFix(String attrName) {
    this(attrName, "");
  }

  public DefineAttributeQuickFix(@NotNull final String attrName, @NotNull String namespace) {
    myAttrName = attrName;
    myNamespace = namespace;
  }

  @NotNull
  public String getName() {
    return "Define " + myAttrName + " attribute";
  }

  @NotNull
  public String getFamilyName() {
    return "Define attribute";
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    try {
      final XmlTag tag = (XmlTag)descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement().getContainingFile())) return;
      final XmlAttribute attribute = tag.setAttribute(myAttrName, myNamespace, "");
      new OpenFileDescriptor(project, tag.getContainingFile().getVirtualFile(),
                             attribute.getValueElement().getTextRange().getStartOffset() + 1).navigate(true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
