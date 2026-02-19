// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.restructuredtext.RestBundle;
import com.intellij.restructuredtext.psi.RestTitle;

public class RestTitleAnnotator extends RestAnnotator {
  @Override
  public void visitTitle(final RestTitle node) {
    final String name = node.getName();
    if (name == null) return;
    final String underline = node.getUnderline();
    final String overline = node.getOverline();
    if (underline != null && overline != null && overline.length() != underline.length()) {
      getHolder().newAnnotation(HighlightSeverity.WARNING, RestBundle.message("ANN.title.length")).create();
    }
  }
}
