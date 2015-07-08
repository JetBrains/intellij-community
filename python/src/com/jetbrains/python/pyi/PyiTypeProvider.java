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

import com.intellij.openapi.util.Ref;
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
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyiTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final PsiElement pythonStub = getPythonStub(func);
      if (pythonStub instanceof PyFunction) {
        final PyFunction functionStub = (PyFunction)pythonStub;
        final PyNamedParameter paramSkeleton = functionStub.getParameterList().findParameterByName(name);
        if (paramSkeleton != null) {
          final PyType type = context.getType(paramSkeleton);
          if (type != null) {
            return Ref.create(type);
          }
        }
      }
      // TODO: Allow the stub for a function to be defined as a class or a target expression alias
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    final PsiElement pythonStub = getPythonStub(callable);
    if (pythonStub instanceof PyCallable) {
      final PyType type = context.getReturnType((PyCallable)pythonStub);
      if (type != null) {
        return Ref.create(type);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    final PsiElement pythonStub = getPythonStub(callable);
    if (pythonStub instanceof PyCallable) {
      return context.getType((PyCallable)pythonStub);
    }
    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement target, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (target instanceof PyTargetExpression) {
      final PsiElement pythonStub = getPythonStub((PyTargetExpression)target);
      if (pythonStub instanceof PyTypedElement) {
        // XXX: Force the switch to AST for getting the type out of the hint in the comment
        final TypeEvalContext effectiveContext = context.maySwitchToAST(pythonStub) ?
                                                 context : TypeEvalContext.deepCodeInsight(target.getProject());
        return effectiveContext.getType((PyTypedElement)pythonStub);
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement getPythonStub(@NotNull PyElement element) {
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
