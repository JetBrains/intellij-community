/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


public final class PyPsiFacadeImpl extends PyPsiFacade {
  private final Project myProject;

  public PyPsiFacadeImpl(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<PsiElement> resolveQualifiedName(@NotNull QualifiedName name, @NotNull PyQualifiedNameResolveContext context) {
    return PyResolveImportUtil.resolveQualifiedName(name, context);
  }

  @Override
  public @NotNull PyQualifiedNameResolveContext createResolveContextFromFoothold(@NotNull PsiElement foothold) {
    return PyResolveImportUtil.fromFoothold(foothold);
  }

  @Override
  public @NotNull PyClassType createClassType(@NotNull PyClass pyClass, boolean isDefinition) {
    return new PyClassTypeImpl(pyClass, isDefinition);
  }

  @Override
  public @Nullable PyType createUnionType(@NotNull Collection<PyType> members) {
    return PyUnionType.union(members);
  }

  @Override
  public @Nullable PyType createTupleType(@NotNull List<PyType> members, @NotNull PsiElement anchor) {
    return PyTupleType.create(anchor, members);
  }

  @Override
  public @Nullable PyType parseTypeAnnotation(@NotNull String annotation, @NotNull PsiElement anchor) {
    return PyTypeParser.getTypeByName(anchor, annotation);
  }

  @Override
  public @Nullable PyClass createClassByQName(final @NotNull String qName, final @NotNull PsiElement anchor) {
    final QualifiedName qualifiedName = QualifiedName.fromDottedString(qName);
    // Only built-in classes can be found by their unqualified names.
    if (qualifiedName.getComponentCount() == 1) {
      return PyBuiltinCache.getInstance(anchor).getClass(qName);
    }

    final Module module = ModuleUtilCore.findModuleForPsiElement(ObjectUtils.notNull(anchor.getContainingFile(), anchor));
    if (module == null) return null;
    // Don't use PyResolveImportUtil.fromFoothold here as setting foothold file is going to affect resolve results
    // particularly if the anchor element happens to be in the same file as the target class.
    final PyQualifiedNameResolveContext resolveContext = PyResolveImportUtil.fromModule(module).copyWithMembers();
    return StreamEx.of(resolveQualifiedName(qualifiedName, resolveContext))
      .select(PyClass.class)
      .findFirst()
      .orElse(null);
  }

  @Override
  public @Nullable String findShortestImportableName(@NotNull VirtualFile targetFile, @NotNull PsiElement anchor) {
    return QualifiedNameFinder.findShortestImportableName(anchor, targetFile);
  }

  @Override
  public @NotNull LanguageLevel getLanguageLevel(@NotNull PsiElement element) {
    return LanguageLevel.forElement(element);
  }
}
