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
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import org.jetbrains.annotations.NotNull;

import java.util.StringTokenizer;

public abstract class HtmlUnknownElementInspection extends HtmlLocalInspectionTool implements XmlEntitiesInspection {
  public JDOMExternalizableStringList myValues;
  public boolean myCustomValuesEnabled = true;

  public HtmlUnknownElementInspection(@NotNull String defaultValues) {
    myValues = reparseProperties(defaultValues);
  }

  protected static JDOMExternalizableStringList reparseProperties(@NotNull final String properties) {
    final JDOMExternalizableStringList result = new JDOMExternalizableStringList();

    final StringTokenizer tokenizer = new StringTokenizer(properties, ",");
    while (tokenizer.hasMoreTokens()) {
      result.add(tokenizer.nextToken().toLowerCase().trim());
    }

    return result;
  }

  protected static void registerProblemOnAttributeName(@NotNull XmlAttribute attribute,
                                                     String message, @NotNull ProblemsHolder holder,
                                                     LocalQuickFix... quickfixes) {
    final ASTNode node = attribute.getNode();
    assert node != null;
    final ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
    if (nameNode != null) {
      final PsiElement nameElement = nameNode.getPsi();
      if (nameElement.getTextLength() > 0) {
        holder.registerProblem(nameElement, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickfixes);
      }
    }
  }

  protected boolean isCustomValue(@NotNull final String value) {
    return myValues.contains(value.toLowerCase());
  }

  @Override
  public void addEntry(@NotNull final String text) {
    final String s = text.trim().toLowerCase();
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

  public void enableCustomValues(boolean customValuesEnabled) {
    myCustomValuesEnabled = customValuesEnabled;
  }

  public void updateAdditionalEntries(@NotNull final String values) {
    myValues = reparseProperties(values);
  }

  protected abstract String getCheckboxTitle();

  @NotNull
  protected abstract String getPanelTitle();

  @NotNull
  protected abstract Logger getLogger();
}
