package com.jetbrains.python.codeInsight;

import com.jetbrains.python.psi.PyFile;
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
    if (qName.equals("os") && point == ResolveImportUtil.PointInImport.AS_MODULE) {
      return Collections.singletonList(new PyDynamicMember("path"));
    }
    return Collections.emptyList();
  }
}
