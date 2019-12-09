/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPathToken;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.context.XsltContextProviderBase;
import org.intellij.lang.xpath.xslt.quickfix.ConvertToEntityFix;
import org.intellij.lang.xpath.xslt.quickfix.FlipOperandsFix;
import org.jetbrains.annotations.NotNull;

public class XsltAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    final boolean isXslt = ContextProvider.getContextProvider(psiElement) instanceof XsltContextProviderBase;
    if (isXslt) {
      if (psiElement instanceof XPathFile) {
        visitXPathFile((XPathFile) psiElement, holder);
      }
      else if (psiElement instanceof XPathToken) {
        visitXPathToken((XPathToken) psiElement, holder);
      }
    }
  }

  public void visitXPathFile(XPathFile file, AnnotationHolder holder) {
    final XmlAttribute context = PsiTreeUtil.getContextOfType(file, XmlAttribute.class, true);
    if (context != null) {
      if (XsltSupport.isPatternAttribute(context)) {
        XsltPatternValidator.validate(holder, file);
      } else {
        if (file.getText().trim().length() == 0 && file.getExpression() == null) {
          holder.createErrorAnnotation(file, "Empty XPath expression");
        }
      }
      if (XsltSupport.isXsltAttribute(context) && !XsltSupport.mayBeAVT(context)) {
        final ASTNode node = file.getNode();
        if (node.findChildByType(XPathTokenTypes.LBRACE) != null) {
          holder.createErrorAnnotation(file, "Attribute Value Template is not allowed here");
        }
      }
    }
  }

  public void visitXPathToken(XPathToken token, AnnotationHolder holder) {
    if (XPathTokenTypes.REL_OPS.contains(token.getTokenType())) {
      if (token.textContains('<')) {
        final Annotation ann = holder.createErrorAnnotation(token, "'<' must be escaped as '&lt;' in XSLT documents");
        ann.registerFix(new ConvertToEntityFix(token));
        ann.registerFix(new FlipOperandsFix(token));
      }
    }
  }
}
