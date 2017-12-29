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

  /**
   * Provides members for specified module.
   *
   * @param module members owner
   * @param point  position in import
   * @return provided members
   * @deprecated Use {@link PyModuleMembersProvider#getMembers(PyFile, PointInImport, TypeEvalContext)} instead.
   * This method will be removed in 2018.2.
   */
  @Deprecated
  public Collection<PyCustomMember> getMembers(PyFile module, PointInImport point) {
    return getMembers(module, point, TypeEvalContext.codeInsightFallback(module.getProject()));
  }

  /**
   * Provides members for specified module.
   *
   * @param module  members owner
   * @param point   position in import
   * @param context type evaluation context
   * @return provided members
   */
  @NotNull
  public Collection<PyCustomMember> getMembers(@NotNull PyFile module, @NotNull PointInImport point, @NotNull TypeEvalContext context) {
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
   * @param module member owner
   * @param name   member name
   * @return provided member
   * @deprecated Use {@link PyModuleMembersProvider#resolveMember(PyFile, String, PyResolveContext)} instead.
   * This method will be removed in 2018.2.
   */
  @Nullable
  @Deprecated
  public PsiElement resolveMember(PyFile module, String name) {
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(module.getProject());
    return resolveMember(module, name, PyResolveContext.noImplicits().withTypeEvalContext(context));
  }

  /**
   * Provides member with specified name for specified module.
   *
   * @param module         member owner
   * @param name           member name
   * @param resolveContext context to be used in resolve
   * @return provided member
   */
  @Nullable
  public PsiElement resolveMember(@NotNull PyFile module, @NotNull String name, @NotNull PyResolveContext resolveContext) {
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
   * @param module module itself
   * @param qName  module name
   * @return provided members
   * @deprecated Use {@link PyModuleMembersProvider#getMembersByQName(PyFile, String, TypeEvalContext)}} instead.
   * This method will be removed in 2018.2.
   */
  @Deprecated
  protected abstract Collection<PyCustomMember> getMembersByQName(PyFile module, String qName);

  /**
   * Provides members for module with specified qualified name.
   *
   * @param module  module itself
   * @param qName   module name
   * @param context type evaluation context
   * @return provided members
   * @apiNote This method will be marked as abstract in 2018.2.
   */
  @NotNull
  protected Collection<PyCustomMember> getMembersByQName(@NotNull PyFile module, @NotNull String qName, @NotNull TypeEvalContext context) {
    return getMembersByQName(module, qName);
  }
}
