// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;

public class CodeStylePyImportCollector extends PyImportCollector {
  public CodeStylePyImportCollector(PyElement node, PsiReference reference, String refText) {
    super(node, reference, refText);
  }

  @Override
  protected PsiFile addCandidatesViaFromImports(PsiFile existingImportFile, PyFile pyFile) {
    final PyCodeStyleSettings pySettings = CodeStyle.getCustomSettings(getNode().getContainingFile(), PyCodeStyleSettings.class);
    return pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS
           ? existingImportFile
           : super.addCandidatesViaFromImports(existingImportFile, pyFile);
  }
}
