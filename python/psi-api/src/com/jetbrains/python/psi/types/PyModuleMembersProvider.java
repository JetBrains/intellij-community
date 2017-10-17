// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public abstract class PyModuleMembersProvider {
  public static final ExtensionPointName<PyModuleMembersProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyModuleMembersProvider");

  public Collection<PyCustomMember> getMembers(PyFile module, PointInImport point, @NotNull TypeEvalContext context) {
    final VirtualFile vFile = module.getVirtualFile();
    if (vFile != null) {
      final String qName = PyPsiFacade.getInstance(module.getProject()).findShortestImportableName(vFile, module);
      if (qName != null) {
        return getMembersByQName(module, qName, context);
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public PsiElement resolveMember(PyFile module, String name, @NotNull PyResolveContext resolveContext) {
    for (PyCustomMember o : getMembers(module, PointInImport.NONE, resolveContext.getTypeEvalContext())) {
      if (o.getName().equals(name)) {
        return o.resolve(module, resolveContext);
      }
    }
    return null;
  }

  protected abstract Collection<PyCustomMember> getMembersByQName(PyFile module, String qName, @NotNull TypeEvalContext context);
}
