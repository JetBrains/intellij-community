/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.pyi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyiUtil {
  private PyiUtil() {}

  @Nullable
  public static PsiElement getPythonStub(@NotNull PyElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyFile && !(file instanceof PyiFile)) {
      final PyiFile pythonStubFile = getPythonStubFile((PyFile)file);
      if (pythonStubFile != null) {
        return findStubInFile(element, pythonStubFile);
      }
    }
    return null;
  }

  @Nullable
  private static PyiFile getPythonStubFile(@NotNull PyFile file) {
    final PyiFile result = findPythonStubNextToFile(file);
    if (result != null) {
      return result;
    }
    // TODO: Find stubs in the paths of the current SDK
    return null;
  }

  @Nullable
  private static PyiFile findPythonStubNextToFile(@NotNull PyFile file) {
    final PsiDirectory dir = file.getContainingDirectory();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (dir != null && virtualFile != null) {
      final String fileName = virtualFile.getNameWithoutExtension();
      final String pythonStubFileName = fileName + "." + PyiFileType.INSTANCE.getDefaultExtension();
      final PsiFile pythonStubFile = dir.findFile(pythonStubFileName);
      if (pythonStubFile instanceof PyiFile) {
        return (PyiFile)pythonStubFile;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement findStubInFile(@NotNull PyElement element, @NotNull PyiFile file) {
    if (element instanceof PyFile) {
      return file;
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
    final String name = element.getName();
    if (owner != null && name != null) {
      assert owner != element;
      final PsiElement originalOwner = findStubInFile(owner, file);
      if (originalOwner instanceof PyClass) {
        final PyClass classOwner = (PyClass)originalOwner;
        final PyType type = TypeEvalContext.codeInsightFallback(classOwner.getProject()).getType(classOwner);
        if (type instanceof PyClassLikeType) {
          final PyClassLikeType classType = (PyClassLikeType)type;
          final PyClassLikeType instanceType = classType.toInstance();
          final List<? extends RatedResolveResult> resolveResults = instanceType.resolveMember(name, null, AccessDirection.READ,
                                                                                               PyResolveContext.noImplicits(), false);
          if (resolveResults != null && !resolveResults.isEmpty()) {
            return resolveResults.get(0).getElement();
          }
        }
      }
      else if (originalOwner instanceof NameDefiner) {
        return ((NameDefiner)originalOwner).getElementNamed(name);
      }
    }
    return null;
  }
}
