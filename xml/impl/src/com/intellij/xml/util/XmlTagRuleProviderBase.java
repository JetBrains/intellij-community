/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.InsertRequiredAttributeFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.RoleFinder;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlTagRuleProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlTagRuleProviderBase extends XmlTagRuleProvider {

  public static RequireAttributeOneOf requireAttr(String ... oneOf) {
    return new RequireAttributeOneOf(oneOf);
  }

  public static IncompatiblesAttributeRule incompatible(String attribute, String ... excluded) {
    return new IncompatiblesAttributeRule(attribute, excluded);
  }

  public static ShouldHaveParams shouldHaveParams() {
    return new ShouldHaveParams();
  }

  public static class IncompatiblesAttributeRule extends Rule {
    private final String[] myExcludedAttributes;
    private final String myAttribute;

    public IncompatiblesAttributeRule(String attribute, String ... excluded) {
      myAttribute = attribute;
      myExcludedAttributes = excluded;
    }
  }

  public static class ShouldHaveParams extends Rule {
    @Override
    public boolean needAtLeastOneAttribute(@NotNull XmlTag tag) {
      return true;
    }
  }

  @Nullable
  public static PsiElement getTagElement(RoleFinder roleFinder, XmlTag tag) {
    ASTNode tagNode = tag.getNode();
    if (tagNode == null) return null;

    ASTNode nameElement = roleFinder.findChild(tagNode);
    if (nameElement == null) return null;

    return nameElement.getPsi();
  }

  @Nullable
  public static PsiElement getTagNameElement(XmlTag tag) {
    return getTagElement(XmlChildRole.START_TAG_NAME_FINDER, tag);
  }

  public static boolean isClosedTag(XmlTag tag) {
    return getTagElement(XmlChildRole.EMPTY_TAG_END_FINDER, tag) != null || getTagElement(XmlChildRole.CLOSING_TAG_START_FINDER, tag) != null;
  }

  public static class RequireAttributeOneOf extends ShouldHaveParams {
    private final String[] myAttributeNames;
    private final ProblemHighlightType myProblemHighlightType;

    public RequireAttributeOneOf(String ... attributeNames) {
      myAttributeNames = attributeNames;
      myProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }

    public RequireAttributeOneOf(@NotNull ProblemHighlightType problemHighlightType, String... attributeNames) {
      assert attributeNames.length > 0;
      myAttributeNames = attributeNames;
      myProblemHighlightType = problemHighlightType;
    }

    public String[] getAttributeNames() {
      return myAttributeNames;
    }

    @Override
    public void annotate(@NotNull XmlTag tag, ProblemsHolder holder) {
      for (String attributeName : myAttributeNames) {
        if (tag.getAttribute(attributeName) != null) {
          return;
        }
      }

      if (!isClosedTag(tag)) return;

      PsiElement tagNameElement = getTagNameElement(tag);
      if (tagNameElement == null) return;

      LocalQuickFix[] fixes = new LocalQuickFix[myAttributeNames.length];
      for (int i = 0; i < myAttributeNames.length; i++) {
        fixes[i] = new InsertRequiredAttributeFix(tag, myAttributeNames[i], null);
      }

      holder.registerProblem(tagNameElement, "Tag should have one of following attributes: " + StringUtil.join(myAttributeNames, ", "),
                             myProblemHighlightType,
                             fixes);
    }
  }
}
