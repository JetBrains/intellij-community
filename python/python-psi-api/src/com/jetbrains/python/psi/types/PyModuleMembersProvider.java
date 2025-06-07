// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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


public abstract class PyModuleMembersProvider {
  public static final ExtensionPointName<PyModuleMembersProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyModuleMembersProvider");

  /**
   * Provides members for specified module.
   *
   * @param module  members owner
   * @param point   position in import
   * @param context type evaluation context
   * @return provided members
   */
  public @NotNull Collection<PyCustomMember> getMembers(@NotNull PyFile module, @NotNull PointInImport point, @NotNull TypeEvalContext context) {
    final VirtualFile vFile = module.getVirtualFile();
    if (vFile != null) {
      final String qName = PyPsiFacade.getInstance(module.getProject()).findShortestImportableName(vFile, module);
      if (qName != null) {
        return getMembersByQName(module, qName, context);
      }
    }
    return Collections.emptyList();
  }

  /**
   * Provides member with specified name for specified module.
   *
   * @param module         member owner
   * @param name           member name
   * @param resolveContext context to be used in resolve
   * @return provided member
   */
  public @Nullable PsiElement resolveMember(@NotNull PyFile module, @NotNull String name, @NotNull PyResolveContext resolveContext) {
    for (PyCustomMember o : getMembers(module, PointInImport.NONE, resolveContext.getTypeEvalContext())) {
      if (o.getName().equals(name)) {
        return o.resolve(module, resolveContext);
      }
    }
    return null;
  }

  /**
   * Provides members for module with specified qualified name.
   *
   * @param module  module itself
   * @param qName   module name
   * @param context type evaluation context
   * @return provided members
   */
  protected abstract @NotNull Collection<PyCustomMember> getMembersByQName(@NotNull PyFile module,
                                                                  @NotNull String qName,
                                                                  @NotNull TypeEvalContext context);
}
