// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public final class PyStructuredDocstringFormatter {

  private static final Logger LOG = Logger.getInstance(PyStructuredDocstringFormatter.class);
  static final String FORMATTER_FRAGMENTS_FLAG = "--fragments";

  private PyStructuredDocstringFormatter() {
  }

  @Nullable
  public static PyDocumentationBuilder.DocstringFormatterRequest formatDocstring(@NotNull PsiElement element,
                                                                                 @NotNull PyDocumentationBuilder.DocstringFormatterRequest docstringFormatterRequest,
                                                                                 @NotNull List<String> flags) {
    Module module = DocStringUtil.getModuleForElement(element);
    if (module == null) return new PyDocumentationBuilder.DocstringFormatterRequest();

    final String docstring = docstringFormatterRequest.getBody();
    final String preparedDocstring = PyIndentUtil.removeCommonIndent(docstring, true).trim();
    final DocStringFormat format = DocStringUtil.guessDocStringFormat(preparedDocstring, element);

    if (format == DocStringFormat.PLAIN) {
      return null;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new PyDocumentationBuilder.DocstringFormatterRequest("Unittest placeholder", docstringFormatterRequest.getFragments());
    }

    final PyDocumentationBuilder.DocstringFormatterRequest inputStructure =
      new PyDocumentationBuilder.DocstringFormatterRequest(preparedDocstring, docstringFormatterRequest.getFragments());

    try {
      final String outputStr = runDocstringFormatterService(flags, module, format, new Gson().toJson(inputStructure));
      return new Gson().fromJson(outputStr, PyDocumentationBuilder.DocstringFormatterRequest.class);
    }
    catch (JsonSyntaxException e) {
      return docstringFormatterRequest;
    }
  }

  @NotNull
  private static String runDocstringFormatterService(@NotNull List<String> flags,
                                                     @NotNull Module module,
                                                     @NotNull DocStringFormat format,
                                                     @NotNull String docstring) {
    String result = null;
    try {
      result = ApplicationUtil.runWithCheckCanceled(
        () -> PythonRuntimeService.getInstance().formatDocstring(module, format, docstring, flags),
        // It's supposed to be run inside a non-blocking read action and, thus, have an associated progress indicator
        EmptyProgressIndicator.notNullize(ProgressManager.getInstance().getProgressIndicator())
      );
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.warn(e);
    }

    if (result != null) {
      return result;
    }
    else if (!flags.contains(FORMATTER_FRAGMENTS_FLAG)) {
      return DocStringUtil.parseDocStringContent(format, docstring).getDescription();
    }
    return "";
  }
}
