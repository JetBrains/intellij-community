package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public abstract class PyModuleMembersProvider {
  public static final ExtensionPointName<PyModuleMembersProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyModuleMembersProvider");

  public Collection<PyDynamicMember> getMembers(PyFile module, ResolveImportUtil.PointInImport point) {
    final VirtualFile vFile = module.getVirtualFile();
    if (vFile != null) {
      final String qName = ResolveImportUtil.findShortestImportableName(module, vFile);
      if (qName != null) {
        return getMembersByQName(module, qName, point);
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public PsiElement resolveMember(PyFile module, String name) {
    for (PyDynamicMember o : getMembers(module, ResolveImportUtil.PointInImport.NONE)) {
      if (o.getName().equals(name)) {
        return o.resolve(module);
      }
    }
    return null;
  }

  protected abstract Collection<PyDynamicMember> getMembersByQName(PyFile module, String qName, ResolveImportUtil.PointInImport point);
}
