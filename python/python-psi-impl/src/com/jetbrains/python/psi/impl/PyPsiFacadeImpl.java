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
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
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

import static com.jetbrains.python.psi.LanguageLevel.getDefault;


public final class PyPsiFacadeImpl extends PyPsiFacade {
  private final Project myProject;

  public PyPsiFacadeImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<PsiElement> resolveQualifiedName(@NotNull QualifiedName name, @NotNull PyQualifiedNameResolveContext context) {
    return PyResolveImportUtil.resolveQualifiedName(name, context);
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext createResolveContextFromFoothold(@NotNull PsiElement foothold) {
    return PyResolveImportUtil.fromFoothold(foothold);
  }

  @NotNull
  @Override
  public PyClassType createClassType(@NotNull PyClass pyClass, boolean isDefinition) {
    return new PyClassTypeImpl(pyClass, isDefinition);
  }

  @Nullable
  @Override
  public PyType createUnionType(@NotNull Collection<PyType> members) {
    return PyUnionType.union(members);
  }

  @Nullable
  @Override
  public PyType createTupleType(@NotNull List<PyType> members, @NotNull PsiElement anchor) {
    return PyTupleType.create(anchor, members);
  }

  @Nullable
  @Override
  public PyType parseTypeAnnotation(@NotNull String annotation, @NotNull PsiElement anchor) {
    return PyTypeParser.getTypeByName(anchor, annotation);
  }

  @Nullable
  @Override
  public PyClass createClassByQName(@NotNull final String qName, @NotNull final PsiElement anchor) {
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

  @Nullable
  @Override
  public String findShortestImportableName(@NotNull VirtualFile targetFile, @NotNull PsiElement anchor) {
    return QualifiedNameFinder.findShortestImportableName(anchor, targetFile);
  }

  @NotNull
  @Override
  public LanguageLevel getLanguageLevel(@NotNull PsiElement element) {
    if (element instanceof PsiDirectory directory) {
      return PythonLanguageLevelPusher.getLanguageLevelForVirtualFile(directory.getProject(), directory.getVirtualFile());
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PyFile) {
      return ((PyFile)containingFile).getLanguageLevel();
    }

    return getDefault();
  }
}
