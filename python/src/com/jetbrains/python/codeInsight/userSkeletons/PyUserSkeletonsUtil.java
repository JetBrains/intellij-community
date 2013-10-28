/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PythonHelpersLocator;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyUserSkeletonsUtil {
  public static final String USER_SKELETONS_DIR = "python-skeletons";
  @Nullable private static VirtualFile ourUserSkeletonsDirectory;

  @NotNull
  private static List<String> getPossibleUserSkeletonsPaths() {
    final List<String> result = new ArrayList<String>();
    result.add(PathManager.getConfigPath() + File.separator + USER_SKELETONS_DIR);
    result.add(ApplicationManager.getApplication().isInternal()
               ? StringUtil.join(new String[]{PythonHelpersLocator.getPythonCommunityPath(), "helpers", USER_SKELETONS_DIR}, File.separator)
               : PythonHelpersLocator.getHelperPath(USER_SKELETONS_DIR));
    return result;
  }

  @Nullable
  public static VirtualFile getUserSkeletonsDirectory() {
    if (ourUserSkeletonsDirectory == null) {
      for (String path : getPossibleUserSkeletonsPaths()) {
        ourUserSkeletonsDirectory = LocalFileSystem.getInstance().findFileByPath(path);
        if (ourUserSkeletonsDirectory != null) {
          break;
        }
      }
    }
    return ourUserSkeletonsDirectory;
  }

  public static boolean isUnderUserSkeletonsDirectory(@NotNull PsiFile file) {
    final VirtualFile skeletonsDir = getUserSkeletonsDirectory();
    final VirtualFile virtualFile = file.getVirtualFile();
    return skeletonsDir != null && virtualFile != null && VfsUtilCore.isAncestor(skeletonsDir, virtualFile, false);
  }

  @Nullable
  public static <T extends PyElement> T getUserSkeleton(@NotNull T element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyFile) {
      final PyFile skeletonFile = getUserSkeletonForFile((PyFile)file);
      if (skeletonFile != null && skeletonFile != file) {
        final PsiElement skeletonElement = getUserSkeleton(element, skeletonFile);
        if (element.getClass().isInstance(skeletonElement) && skeletonElement != element) {
          //noinspection unchecked
          return (T)skeletonElement;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PyFile getUserSkeletonForModuleQName(@NotNull String qName, @NotNull PsiElement foothold) {
    final Sdk sdk = PythonSdkType.getSdk(foothold);
    if (sdk != null) {
      final Project project = foothold.getProject();
      final PythonSdkPathCache cache = PythonSdkPathCache.getInstance(project, sdk);
      final QualifiedName cacheQName = QualifiedName.fromDottedString(USER_SKELETONS_DIR + "." + qName);
      final List<PsiElement> results = cache.get(cacheQName);
      if (results != null) {
        final PsiElement element = results.isEmpty() ? null : results.get(0);
        if (element instanceof PyFile) {
          return (PyFile)element;
        }
      }
      final VirtualFile directory = getUserSkeletonsDirectory();
      if (directory != null) {
        final PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
        PsiElement fileSkeleton = new QualifiedNameResolverImpl(qName).resolveModuleAt(psiDirectory);
        if (fileSkeleton instanceof PsiDirectory) {
          fileSkeleton = PyUtil.getPackageElement((PsiDirectory)fileSkeleton);
        }
        if (fileSkeleton instanceof PyFile) {
          cache.put(cacheQName, Collections.singletonList(fileSkeleton));
          return (PyFile)fileSkeleton;
        }
      }
      cache.put(cacheQName, Collections.<PsiElement>emptyList());
    }
    return null;
  }

  public static void addUserSkeletonsRoot(@NotNull SdkModificator sdkModificator) {
    final VirtualFile root = getUserSkeletonsDirectory();
    if (root != null) {
      sdkModificator.addRoot(root, OrderRootType.CLASSES);
    }
  }

  @Nullable
  private static PsiElement getUserSkeleton(@NotNull PyElement element, @NotNull PyFile skeletonFile) {
    if (element instanceof PyFile) {
      return skeletonFile;
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
    final String name = element.getName();
    if (owner != null && name != null) {
      assert owner != element;
      final PsiElement originalOwner = getUserSkeleton(owner, skeletonFile);
      if (originalOwner instanceof PyClass) {
        final PyType type = TypeEvalContext.codeInsightFallback().getType((PyClass)originalOwner);
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

  @Nullable
  private static PyFile getUserSkeletonForFile(@NotNull PyFile file) {
    final VirtualFile moduleVirtualFile = file.getVirtualFile();
    if (moduleVirtualFile != null) {
      String moduleName = QualifiedNameFinder.findShortestImportableName(file, moduleVirtualFile);
      if (moduleName != null) {
        final QualifiedName qName = QualifiedName.fromDottedString(moduleName);
        for (PyCanonicalPathProvider provider : Extensions.getExtensions(PyCanonicalPathProvider.EP_NAME)) {
          final QualifiedName restored = provider.getCanonicalPath(qName, null);
          if (restored != null) {
            moduleName = restored.toString();
          }
        }
        return getUserSkeletonForModuleQName(moduleName, file);
      }
    }
    return null;
  }
}
