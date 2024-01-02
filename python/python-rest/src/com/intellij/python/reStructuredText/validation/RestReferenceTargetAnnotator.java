// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.python.reStructuredText.RestBundle;
import com.intellij.python.reStructuredText.RestFile;
import com.intellij.python.reStructuredText.psi.RestReferenceTarget;

/**
 * Looks for double defined hyperlinks
 *
 * User : catherine
 */
public class RestReferenceTargetAnnotator extends RestAnnotator {

  @Override
  public void visitReferenceTarget(final RestReferenceTarget node) {
    RestFile file = (RestFile)node.getContainingFile();
    RestReferenceTarget[] targets = PsiTreeUtil.getChildrenOfType(file, RestReferenceTarget.class);
    String quotedName = node.getReferenceName();
    String name = node.getReferenceName(false);
    if (targets != null) {
      if ("__".equals(name) && !node.hasReference()) {
        getHolder().newAnnotation(HighlightSeverity.WARNING, RestBundle.message("ANN.unusable.anonymous.target")).create();
      }
      for (RestReferenceTarget element : targets) {
        if ((element.getReferenceName().equalsIgnoreCase(name) || element.getReferenceName(false).equalsIgnoreCase(name) ||
            element.getReferenceName().equalsIgnoreCase(quotedName) || element.getReferenceName(false).equalsIgnoreCase(quotedName)) &&
                                    !element.equals(node) && ! "__".equals(name) && !"[#]".equals(quotedName) && !"[*]".equals(quotedName)) {
          getHolder().newAnnotation(HighlightSeverity.WARNING, RestBundle.message("ANN.duplicate.target", name)).create();
        }
      }
    }
  }
}
