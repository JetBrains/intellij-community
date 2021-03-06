// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.analysis.XmlAnalysisBundle;

/**
 * @author Dmitry Avdeev
 */
public class XmlInvalidIdInspection extends XmlDuplicatedIdInspection {

  @Override
  protected void checkValue(XmlAttributeValue value, XmlFile file, XmlRefCountHolder refHolder, XmlTag tag, ProblemsHolder holder) {

    String idRef = XmlHighlightVisitor.getUnquotedValue(value, tag);

    if (tag instanceof HtmlTag) {
      idRef = StringUtil.toLowerCase(idRef);
    }

    if (XmlUtil.isSimpleValue(idRef, value) && refHolder.isIdReferenceValue(value)) {
      boolean hasIdDeclaration = refHolder.hasIdDeclaration(idRef);
      if (!hasIdDeclaration && tag instanceof HtmlTag) {
        hasIdDeclaration = refHolder.hasIdDeclaration(value.getValue());
      }

      if (!hasIdDeclaration) {
        for(XmlIdContributor contributor: XmlIdContributor.EP_NAME.getExtensionList()) {
          if (contributor.suppressExistingIdValidation(file)) {
            return;
          }
        }

        final FileViewProvider viewProvider = tag.getContainingFile().getViewProvider();
        if (viewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
          holder.registerProblem(value, XmlAnalysisBundle.message("xml.inspections.invalid.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                 new XmlDeclareIdInCommentAction(idRef));

        }
        else {
          holder.registerProblem(value, XmlAnalysisBundle.message("xml.inspections.invalid.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }
      }
    }
  }
}
