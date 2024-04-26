// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.PsiReference;

/**
 * @author Dmitry Avdeev
 */
public class XmlPathReferenceInspection extends XmlReferenceInspectionBase {

  @Override
  protected boolean needToCheckRef(PsiReference reference) {
    return XmlHighlightVisitor.isUrlReference(reference);
  }
}
