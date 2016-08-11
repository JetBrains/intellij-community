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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    return findPythonStubInRoots(file);
  }

  @Nullable
  private static PyiFile findPythonStubInRoots(@NotNull PyFile file) {
    final QualifiedName qName = findImportableName(file);
    final Sdk sdk = PythonSdkType.getSdk(file);
    if (qName != null && sdk != null) {
      final List<String> stubQNameComponents = ContainerUtil.newArrayList("python-stubs");
      stubQNameComponents.addAll(qName.getComponents());
      final QualifiedName stubQName = QualifiedName.fromComponents(stubQNameComponents);
      final Project project = file.getProject();
      final PythonSdkPathCache cache = PythonSdkPathCache.getInstance(project, sdk);
      final List<PsiElement> cachedResults = cache.get(stubQName);
      if (cachedResults != null) {
        return getFirstPyiFile(cachedResults);
      }
      final ArrayList<PsiElement> results = new ArrayList<>();
      final PsiManager psiManager = PsiManager.getInstance(project);
      final String nameAsPath = StringUtil.join(qName.getComponents(), "/");
      final List<String> paths = ImmutableList.of(
        nameAsPath + "/__init__.pyi",
        nameAsPath + ".pyi");
      final RootVisitor rootVisitor = new RootVisitor() {
        @Override
        public boolean visitRoot(VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource) {
          if (root.isValid()) {
            for (String path : paths) {
              final VirtualFile pyiVirtualFile = root.findFileByRelativePath(path);
              if (pyiVirtualFile != null) {
                final PsiFile pyiFile = psiManager.findFile(pyiVirtualFile);
                if (pyiFile instanceof PyiFile) {
                  results.add(pyiFile);
                  return false;
                }
              }
            }
          }
          return true;
        }
      };
      RootVisitorHost.visitRoots(file, rootVisitor);
      RootVisitorHost.visitSdkRoots(sdk, rootVisitor);
      cache.put(stubQName, results);
      return getFirstPyiFile(results);
    }
    return null;
  }

  @Nullable
  private static PyiFile getFirstPyiFile(List<PsiElement> elements) {
    if (elements.isEmpty()) {
      return null;
    }
    final PsiElement result = elements.get(0);
    if (result instanceof PyiFile) {
      return (PyiFile)result;
    }
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
  private static QualifiedName findImportableName(@NotNull PyFile file) {
    final VirtualFile moduleVirtualFile = file.getVirtualFile();
    if (moduleVirtualFile != null) {
      String moduleName = QualifiedNameFinder.findShortestImportableName(file, moduleVirtualFile);
      if (moduleName != null) {
        final QualifiedName qName = QualifiedName.fromDottedString(moduleName);
        for (PyCanonicalPathProvider provider : Extensions.getExtensions(PyCanonicalPathProvider.EP_NAME)) {
          final QualifiedName restored = provider.getCanonicalPath(qName, null);
          if (restored != null) {
            return restored;
          }
        }
        return qName;
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
      else if (originalOwner instanceof PyFile) {
        return ((PyFile)originalOwner).getElementNamed(name);
      }
    }
    return null;
  }
}
