// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyStdlibModuleMembersProvider extends PyModuleMembersProvider {
  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName, @NotNull TypeEvalContext context) {
    if (qName.equals("os")) {
      final List<PyCustomMember> results = new ArrayList<>();
      PsiElement path = null;
      if (module != null) {
        final String pathModuleName = SystemInfo.isWindows ? "ntpath" : "posixpath";
        path = ResolveImportUtil.resolveModuleInRoots(QualifiedName.fromDottedString(pathModuleName), module);
      }
      results.add(new PyCustomMember("path", path));
      return results;
    }
    return Collections.emptyList();
  }
}
