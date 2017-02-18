/*
 * Copyright 2005 Sascha Weinreuter
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

/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 06.05.2006
 * Time: 12:56:57
 */
package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.impl.XsltIncludeIndex;
import org.intellij.lang.xpath.xslt.quickfix.CreateTemplateFix;
import org.intellij.lang.xpath.xslt.util.NamedTemplateMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class TemplateReference extends AttributeReference implements EmptyResolveMessageProvider, LocalQuickFixProvider, PsiPolyVariantReference {
  private final String myName;

  public TemplateReference(XmlAttribute attribute) {
    super(attribute, createMatcher(attribute), false);
    myName = attribute.getValue();
  }

  private static ResolveUtil.Matcher createMatcher(XmlAttribute attribute) {
    return new NamedTemplateMatcher(PsiTreeUtil.getParentOfType(attribute, XmlDocument.class), attribute.getValue());
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiElement element = resolve();
    if (element != null) {
      return new ResolveResult[]{new PsiElementResolveResult(element)};
    }

    final XmlFile xmlFile = (XmlFile)getElement().getContainingFile();
    if (xmlFile != null) {
      final List<PsiElementResolveResult> targets = new SmartList<>();
      XsltIncludeIndex.processBackwardDependencies(xmlFile, xmlFile1 -> {
        final PsiElement e = ResolveUtil.resolve(new NamedTemplateMatcher(xmlFile1.getDocument(), myName));
        if (e != null) {
          targets.add(new PsiElementResolveResult(e));
        }
        return true;
      });
      return targets.toArray(new ResolveResult[targets.size()]);
    }
    else {
      return ResolveResult.EMPTY_ARRAY;
    }
  }

  @Nullable
  @Override
  public LocalQuickFix[] getQuickFixes() {
    return new LocalQuickFix[] { new CreateTemplateFix(myAttribute.getParent(), myName) };
  }

  @NotNull
  public String getUnresolvedMessagePattern() {
    return "Cannot resolve template ''{0}''";
  }
}
