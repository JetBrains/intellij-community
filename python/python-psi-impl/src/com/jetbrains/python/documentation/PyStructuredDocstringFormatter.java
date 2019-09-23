// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
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
import java.util.List;

/**
 * @author yole
 */
public class PyStructuredDocstringFormatter {

  private PyStructuredDocstringFormatter() {
  }

  /**
   * @param docstring docstring text without string literal prefix, without quotes and already escaped.
   *                  Supposedly result of {@link PyStringLiteralExpression#getStringValue()}.
   */
  @Nullable
  public static List<String> formatDocstring(@NotNull final PsiElement element, @NotNull final String docstring) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      final Module[] modules = ModuleManager.getInstance(element.getProject()).getModules();
      if (modules.length == 0) return Lists.newArrayList();
      module = modules[0];
    }
    if (module == null) return Lists.newArrayList();
    final List<String> result = new ArrayList<>();

    final String preparedDocstring = PyIndentUtil.removeCommonIndent(docstring, true).trim();

    final DocStringFormat format = DocStringUtil.guessDocStringFormat(preparedDocstring, element);
    if (format == DocStringFormat.PLAIN) {
      return null;
    }

    final StructuredDocString structuredDocString = DocStringUtil.parseDocStringContent(format, preparedDocstring);

    final String output = PythonRuntimeService.getInstance().formatDocstring(module, format, preparedDocstring);
    if (output != null) {
      result.add(output);
    }
    else {
      result.add(structuredDocString.getDescription());
    }

    return result;
  }
}
