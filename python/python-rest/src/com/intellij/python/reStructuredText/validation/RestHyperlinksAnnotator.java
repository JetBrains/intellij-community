// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.python.reStructuredText.RestBundle;
import com.intellij.python.reStructuredText.psi.RestReference;

/**
 * Looks for not defined hyperlinks
 *
 * User : catherine
 */
public class RestHyperlinksAnnotator extends RestAnnotator {

  @Override
  public void visitReference(final RestReference node) {
    if (node.getText().matches("`[^`]*<[^`]+>`_(_)?"))
      return;

    if (node.resolve() == null)
      getHolder().newAnnotation(HighlightSeverity.WARNING, RestBundle.message("ANN.unknown.target", node.getReferenceText())).create();
  }
}
