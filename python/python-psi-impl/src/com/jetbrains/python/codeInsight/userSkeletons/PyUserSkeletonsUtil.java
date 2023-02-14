/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.ImmutableSet;
import com.intellij.model.ModelBranchUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PyUserSkeletonsUtil {
  public static final String USER_SKELETONS_DIR = "python-skeletons";
  private static final Logger LOG = Logger.getInstance(PyUserSkeletonsUtil.class);
  public static final Key<Boolean> HAS_SKELETON = Key.create("PyUserSkeleton.hasSkeleton");

  private static final ImmutableSet<String> STDLIB_SKELETONS = ImmutableSet.of(
    "asyncio",
    "multiprocessing",
    "os",
    "_csv.py",
    "copy.py",
    "cStringIO.py",
    "decimal.py",
    "io.py",
    "itertools.py",
    "logging.py",
    "pathlib.py",
    "pickle.py",
    "StringIO.py",
    "struct.py",
    "sys.py"
  );

  @Nullable private static VirtualFile ourUserSkeletonsDirectory;
  private static boolean ourNoSkeletonsErrorReported = false;

  @NotNull
  private static List<String> getPossibleUserSkeletonsPaths() {
    final List<String> result = new ArrayList<>();
    result.add(PathManager.getConfigPath() + File.separator + USER_SKELETONS_DIR);
    result.add(PythonHelpersLocator.getHelperPath(USER_SKELETONS_DIR));
    return result;
  }

  @Nullable
  public static VirtualFile getUserSkeletonsDirectory() {
    if (ourUserSkeletonsDirectory == null) {
      for (String path : getPossibleUserSkeletonsPaths()) {
        ourUserSkeletonsDirectory = StandardFileSystems.local().findFileByPath(path);
        if (ourUserSkeletonsDirectory != null) {
          break;
        }
      }
    }
    if (!ourNoSkeletonsErrorReported && ourUserSkeletonsDirectory == null) {
      ourNoSkeletonsErrorReported = true;
      LOG.warn("python-skeletons directory not found in paths: " + getPossibleUserSkeletonsPaths());
    }
    return ourUserSkeletonsDirectory;
  }

  public static boolean isUnderUserSkeletonsDirectory(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    return isUnderUserSkeletonsDirectory(virtualFile);
  }

  public static boolean isUnderUserSkeletonsDirectory(@NotNull final VirtualFile virtualFile) {
    final VirtualFile skeletonsDir = getUserSkeletonsDirectory();
    return skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, virtualFile, false);
  }

  @NotNull
  public static GlobalSearchScope getUserSkeletonsDirectoryScope(@NotNull Project project) {
    VirtualFile userSkeletonsDirectory = getUserSkeletonsDirectory();
    if (userSkeletonsDirectory != null) {
      return new GlobalSearchScopesCore.DirectoryScope(project, userSkeletonsDirectory, true);
    }
    else {
      return GlobalSearchScope.EMPTY_SCOPE;
    }
  }

  public static boolean isStandardLibrarySkeleton(@NotNull VirtualFile virtualFile) {
    final VirtualFile skeletonsDir = getUserSkeletonsDirectory();
    if (skeletonsDir == null) {
      return false;
    }
    final String relativePath =
      VfsUtilCore.getRelativePath(virtualFile, ModelBranchUtil.obtainCopyFromTheSameBranch(virtualFile, skeletonsDir), '/');
    // not under skeletons directory
    if (relativePath == null) {
      return false;
    }
    final String firstComponent = ContainerUtil.getFirstItem(StringUtil.split(relativePath, "/"));
    return STDLIB_SKELETONS.contains(firstComponent);
  }

  @Nullable
  public static <T extends PyElement> T getUserSkeleton(@NotNull T element) {
    return getUserSkeletonWithContext(element, null);
  }
  @Nullable
  public static <T extends PyElement> T getUserSkeletonWithContext(@NotNull T element, @Nullable final TypeEvalContext context) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyFile) {
      final PyFile skeletonFile = getUserSkeletonForFile((PyFile)file);
      if (skeletonFile != null && skeletonFile != file) {
        final PsiElement skeletonElement = getUserSkeleton(element, skeletonFile, context);
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
    final Sdk sdk = PythonSdkUtil.findPythonSdk(foothold);
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
        PsiElement fileSkeleton = PyResolveImportUtil.resolveModuleAt(QualifiedName.fromDottedString(qName), psiDirectory,
                                                                      PyResolveImportUtil.fromFoothold(foothold))
          .stream().findFirst().orElse(null);
        if (fileSkeleton instanceof PsiDirectory) {
          fileSkeleton = PyUtil.getPackageElement((PsiDirectory)fileSkeleton, foothold);
        }
        if (fileSkeleton instanceof PyFile) {
          cache.put(cacheQName, Collections.singletonList(fileSkeleton));
          return (PyFile)fileSkeleton;
        }
      }
      cache.put(cacheQName, Collections.emptyList());
    }
    return null;
  }

  @Nullable
  private static PsiElement getUserSkeleton(@NotNull PyElement element, @NotNull PyFile skeletonFile, @Nullable TypeEvalContext context) {
    if (element instanceof PyFile) {
      return skeletonFile;
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
    final String name = element.getName();
    if (owner != null && name != null) {
      assert owner != element;
      final PsiElement originalOwner = getUserSkeleton(owner, skeletonFile, context);
      if (originalOwner instanceof PyClass classOwner) {
        final var fallbackContext = TypeEvalContext.codeInsightFallback(classOwner.getProject());
        final PyType type = fallbackContext.getType(classOwner);
        if (type instanceof PyClassLikeType classType) {
          final PyClassLikeType instanceType = classType.toInstance();
          final PyResolveContext resolveContext = PyResolveContext.defaultContext(ObjectUtils.notNull(context, fallbackContext));
          final List<? extends RatedResolveResult> resolveResults = instanceType.resolveMember(name, null, AccessDirection.READ,
                                                                                               resolveContext, false);
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

  @Nullable
  private static PyFile getUserSkeletonForFile(@NotNull PyFile file) {
    final Boolean hasSkeleton = file.getUserData(HAS_SKELETON);
    if (hasSkeleton != null && !hasSkeleton) {
      return null;
    }
    final VirtualFile moduleVirtualFile = file.getVirtualFile();
    if (moduleVirtualFile != null) {
      String moduleName = QualifiedNameFinder.findShortestImportableName(file, moduleVirtualFile);
      if (moduleName != null) {
        final QualifiedName qName = QualifiedName.fromDottedString(moduleName);
        final QualifiedName restored = QualifiedNameFinder.canonizeQualifiedName(file, qName, null);
        if (restored != null) {
          moduleName = restored.toString();
        }
        final PyFile skeletonFile = getUserSkeletonForModuleQName(moduleName, file);
        file.putUserData(HAS_SKELETON, skeletonFile != null);
        return skeletonFile;
      }
    }
    return null;
  }
}
