// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyElement;

public final class CodeStylePyImportCollectorFactory implements PyImportCollectorFactory {
  @Override
  public PyImportCollector create(PyElement node, PsiReference reference, String refText) {
    return new CodeStylePyImportCollector(node, reference, refText);
  }
}
