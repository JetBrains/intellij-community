// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.XmlDomBundle;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AddDomElementQuickFix<T extends DomElement> implements LocalQuickFix {

  protected final T myElement;
  protected final @IntentionName String myName;

  public AddDomElementQuickFix(@NotNull T element) {
    myElement = element.createStableCopy();
    myName = computeName();
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  private @IntentionName String computeName() {
    final String name = myElement.getXmlElementName();
    return XmlDomBundle.message(isTag() ? "dom.quickfix.add.element.name" : "dom.quickfix.add.attribute.name", name);
  }

  private boolean isTag() {
    return myElement.getXmlElement() instanceof XmlTag;
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlDomBundle.message("dom.quickfix.add.element.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final XmlElement element = myElement.ensureXmlElementExists();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      final XmlElement navigationElement = isTag() ? element : ((XmlAttribute)element).getValueElement();
      PsiNavigateUtil.navigate(navigationElement);
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    XmlElement parent = PsiTreeUtil.findSameElementInCopy(myElement.getParent().getXmlElement(), target);
    DomElement element = DomUtil.getDomElement(parent);
    if (element == null) return null;
    AbstractDomChildrenDescription description = myElement.getChildDescription();
    if (description == null) return null;
    List<? extends DomElement> values = description.getStableValues(element);
    return values.isEmpty() ? null : new AddDomElementQuickFix<>(values.get(0));
  }
}