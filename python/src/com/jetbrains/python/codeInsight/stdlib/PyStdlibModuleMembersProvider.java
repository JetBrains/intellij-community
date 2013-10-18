package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyStdlibModuleMembersProvider extends PyModuleMembersProvider {
  @Override
  protected Collection<PyDynamicMember> getMembersByQName(PyFile module, String qName) {
    if (qName.equals("os")) {
      final List<PyDynamicMember> results = new ArrayList<PyDynamicMember>();
      PsiElement path = null;
      PsiElement osError = null;
      if (module != null) {
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(module);
        osError = builtinCache.getByName("OSError");

        final String pathModuleName = SystemInfo.isWindows ? "ntpath" : "posixpath";
        path = ResolveImportUtil.resolveModuleInRoots(QualifiedName.fromDottedString(pathModuleName), module);
      }
      results.add(new PyDynamicMember("error", osError));
      results.add(new PyDynamicMember("path", path));
      return results;
    }
    return Collections.emptyList();
  }
}
