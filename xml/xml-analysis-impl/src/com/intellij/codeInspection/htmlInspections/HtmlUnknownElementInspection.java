// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.StringTokenizer;

public abstract class HtmlUnknownElementInspection extends HtmlLocalInspectionTool implements XmlEntitiesInspection {
  public JDOMExternalizableStringList myValues;
  public boolean myCustomValuesEnabled = true;

  public HtmlUnknownElementInspection(@NotNull String defaultValues) {
    myValues = reparseProperties(defaultValues);
  }

  protected static JDOMExternalizableStringList reparseProperties(final @NotNull String properties) {
    final JDOMExternalizableStringList result = new JDOMExternalizableStringList();

    final StringTokenizer tokenizer = new StringTokenizer(properties, ",");
    while (tokenizer.hasMoreTokens()) {
      result.add(tokenizer.nextToken());
    }

    return result;
  }

  protected static void registerProblemOnAttributeName(@NotNull XmlAttribute attribute,
                                                       @InspectionMessage String message,
                                                       @NotNull ProblemsHolder holder,
                                                       @NotNull ProblemHighlightType highlightType,
                                                       @NotNull LocalQuickFix @NotNull ... quickfixes) {
    final ASTNode node = attribute.getNode();
    assert node != null;
    final ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
    if (nameNode != null) {
      final PsiElement nameElement = nameNode.getPsi();
      if (nameElement.getTextLength() > 0) {
        holder.registerProblem(nameElement, message, highlightType, quickfixes);
      }
    }
  }

  protected boolean isCustomValue(@NotNull String value) {
    return ContainerUtil.exists(myValues, val -> StringUtil.equalsIgnoreCase(val, value));
  }

  @Override
  public void addEntry(final @NotNull String text) {
    final String s = text.trim();
    if (!isCustomValue(s)) {
      myValues.add(s);
    }

    if (!isCustomValuesEnabled()) {
      myCustomValuesEnabled = true;
    }
  }

  public boolean isCustomValuesEnabled() {
    return myCustomValuesEnabled;
  }

  @Override
  public String getAdditionalEntries() {
    return StringUtil.join(myValues, ",");
  }

  public void updateAdditionalEntries(final @NotNull String values, Disposable disposable) {
    JDOMExternalizableStringList oldValue = myValues;
    myValues = reparseProperties(values);
    if (disposable != null) {
      Disposer.register(disposable, () -> {
        myValues = oldValue;
      });
    }
  }

  protected abstract @NotNull Logger getLogger();
}
