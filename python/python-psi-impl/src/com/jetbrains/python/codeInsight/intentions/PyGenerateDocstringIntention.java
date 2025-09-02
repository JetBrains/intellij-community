// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: catherine
 * Intention to add documentation string for function
 * (with checked format)
 */
public final class PyGenerateDocstringIntention extends PyBaseIntentionAction {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.insert.docstring.stub");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile) || psiFile instanceof PyDocstringFile) return false;
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(psiFile, editor.getCaretModel().getOffset());
    if (elementAt == null) {
      return false;
    }
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(elementAt, PyStatementList.class, false, PyFunction.class);
    if (function == null || statementList != null) {
      return false;
    }
    if (!elementAt.equals(function.getNameNode())) return false;
    return isAvailableForFunction(function);
  }

  private boolean isAvailableForFunction(PyFunction function) {
    if (function.getDocStringValue() != null) {
      if (PyDocstringGenerator.forDocStringOwner(function).withInferredParameters(false).hasParametersToAdd()) {
        setText(PyPsiBundle.message("INTN.add.parameters.to.docstring"));
        return true;
      }
      else {
        return false;
      }
    }
    else {
      setText(PyPsiBundle.message("INTN.insert.docstring.stub"));
      return true;
    }
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);

    if (function == null) {
      return;
    }

    generateDocstring(function, editor);
  }

  public static void generateDocstring(@NotNull PyDocStringOwner docStringOwner, @Nullable Editor editor) {
    if (!ensureNotPlainDocstringFormat(docStringOwner)) {
      return;
    }
    DocstringQuickFix.doGenerateDocstring(docStringOwner, editor);
  }

  /**
   * Checks that docstring format is set either via element module's {@link com.jetbrains.python.PyNames#DOCFORMAT} attribute or
   * in module settings. If none of them applies, show standard choose dialog, asking user to pick one and updates module settings
   * accordingly.
   *
   * @param anchor PSI element that will be used to locate containing file and project module
   * @return false if no structured docstring format was specified initially and user didn't select any, true otherwise
   */
  public static boolean ensureNotPlainDocstringFormat(@NotNull PsiElement anchor) {
    final Module module = DocStringParser.getModuleForElement(anchor);
    if (module == null) {
      return false;
    }

    return ensureNotPlainDocstringFormatForFile(anchor.getContainingFile(), module);
  }

  private static boolean ensureNotPlainDocstringFormatForFile(@NotNull PsiFile file, @NotNull Module module) {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(module);
    if (settings.isPlain(file)) {
      final List<String> values = DocStringFormat.ALL_NAMES_BUT_PLAIN;
      final int i = PythonUiService.getInstance().showChooseDialog(null, null,
                                                                   PyPsiBundle.message("python.docstring.format"),
                                                                   PyPsiBundle.message("python.docstring.select.type"),
                                                                   ArrayUtilRt.toStringArray(values), values.get(0), null);
      if (i < 0) {
        return false;
      }
      SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS);
      settings.setFormat(DocStringFormat.fromNameOrPlain(values.get(i)));
    }
    return true;
  }
}
