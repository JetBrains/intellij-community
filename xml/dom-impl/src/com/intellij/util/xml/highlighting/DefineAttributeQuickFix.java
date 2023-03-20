// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlDomBundle;
import org.jetbrains.annotations.NotNull;

public class DefineAttributeQuickFix implements LocalQuickFix {
  private final String myAttrName;
  private final String myAttrValue;
  private final String myNamespace;

  public DefineAttributeQuickFix(String attrName) {
    this(attrName, "", "");
  }

  public DefineAttributeQuickFix(@NotNull final String attrName, @NotNull String namespace) {
    this(attrName, namespace, "");
  }

  public DefineAttributeQuickFix(@NotNull final String attrName, @NotNull String namespace, @NotNull String attrValue) {
    myAttrName = attrName;
    myNamespace = namespace;
    myAttrValue = attrValue;
  }

  @Override
  @NotNull
  public String getName() {
    return XmlDomBundle.message("dom.quickfix.define.attribute.text", myAttrName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlDomBundle.message("dom.quickfix.define.attribute.family");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    XmlTag tag = (XmlTag)descriptor.getPsiElement();
    XmlAttribute attribute = tag.setAttribute(myAttrName, myNamespace.equals(tag.getNamespace()) ? "" : myNamespace, myAttrValue);
    VirtualFile virtualFile = tag.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile,
                                                           attribute.getValueElement().getTextRange().getStartOffset() +
                                                           1).navigate(true);
    }
  }
}
