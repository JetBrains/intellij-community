// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public final class PyStructuredDocstringFormatter {

  private static final Logger LOG = Logger.getInstance(PyStructuredDocstringFormatter.class);

  private PyStructuredDocstringFormatter() {
  }

  /**
   * @param docstring docstring text without string literal prefix, without quotes and already escaped.
   *                  Supposedly result of {@link PyStringLiteralExpression#getStringValue()}.
   */
  @Nullable
  public static List<String> formatDocstring(@NotNull final PsiElement element, @NotNull final String docstring) {
    Module module = DocStringUtil.getModuleForElement(element);
    if (module == null) return new ArrayList<>();
    final List<String> result = new ArrayList<>();

    final String preparedDocstring = PyIndentUtil.removeCommonIndent(docstring, true).trim();

    final DocStringFormat format = DocStringUtil.guessDocStringFormat(preparedDocstring, element);
    if (format == DocStringFormat.PLAIN) {
      return null;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return Collections.singletonList("Unittest placeholder");
    }

    final StructuredDocString structuredDocString = DocStringUtil.parseDocStringContent(format, preparedDocstring);

    String output = null;
    try {
      output = ApplicationUtil.runWithCheckCanceled(
        () -> PythonRuntimeService.getInstance().formatDocstring(module, format, preparedDocstring),
        // It's supposed to be run inside a non-blocking read action and, thus, have an associated progress indicator
        ProgressManager.getInstance().getProgressIndicator()
      );
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.warn(e);
    }

    if (output != null) {
      result.add(output);
    }
    else {
      result.add(structuredDocString.getDescription());
    }

    return result;
  }
}
