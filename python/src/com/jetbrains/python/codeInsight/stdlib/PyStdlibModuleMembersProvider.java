package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class PyStdlibModuleMembersProvider extends PyModuleMembersProvider {
  @Override
  protected Collection<PyDynamicMember> getMembersByQName(PyFile module, String qName, ResolveImportUtil.PointInImport point) {
    if (qName.equals("os")) {
      if (point == ResolveImportUtil.PointInImport.AS_MODULE) {
        return Collections.singletonList(new PyDynamicMember("path"));
      }
      else if (point == ResolveImportUtil.PointInImport.NONE && module != null) {
        final String name = SystemInfo.isWindows ? "ntpath" : "posixpath";
        final PsiElement resolved = ResolveImportUtil.resolveModuleInRoots(PyQualifiedName.fromDottedString(name), module);
        if (resolved != null) {
          return Collections.singletonList(new PyDynamicMember("path", resolved));
        }
      }
    }
    return Collections.emptyList();
  }
}
