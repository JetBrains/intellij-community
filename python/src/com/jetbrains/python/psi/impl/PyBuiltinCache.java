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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Provides access to Python builtins via skeletons.
 */
public class PyBuiltinCache {
  public static final String BUILTIN_FILE = "__builtin__.py";
  public static final String BUILTIN_FILE_3K = "builtins.py";
  public static final String EXCEPTIONS_FILE = "exceptions.py";

  private static final PyBuiltinCache DUD_INSTANCE = new PyBuiltinCache(null, null);

  /**
   * Stores the most often used types, returned by getNNNType().
   */
  @NotNull private final Map<String, PyClassTypeImpl> myTypeCache = new HashMap<>();

  @Nullable private PyFile myBuiltinsFile;
  @Nullable private PyFile myExceptionsFile;
  private long myModStamp = -1;

  public PyBuiltinCache() {
  }

  public PyBuiltinCache(@Nullable final PyFile builtins, @Nullable PyFile exceptions) {
    myBuiltinsFile = builtins;
    myExceptionsFile = exceptions;
  }

  /**
   * Returns an instance of builtin cache. Instances differ per module and are cached.
   * @param reference something to define the module from.
   * @return an instance of cache. If reference was null, the instance is a fail-fast dud one.
   */
  @NotNull
  public static PyBuiltinCache getInstance(@Nullable PsiElement reference) {
    if (reference != null) {
      try {
        Sdk sdk = findSdkForFile(reference.getContainingFile());
        if (sdk != null) {
          return PythonSdkPathCache.getInstance(reference.getProject(), sdk).getBuiltins();
        }
      }
      catch (PsiInvalidElementAccessException ignored) {
      }
    }
    return DUD_INSTANCE; // a non-functional fail-fast instance, for a case when skeletons are not available
  }

  @Nullable
  public static Sdk findSdkForFile(PsiFileSystemItem psifile) {
    if (psifile == null) {
      return null;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(psifile);
    if (module != null) {
      return PythonSdkType.findPythonSdk(module);
    }
    return findSdkForNonModuleFile(psifile);
  }

  @Nullable
  public static Sdk findSdkForNonModuleFile(PsiFileSystemItem psiFile) {
    Project project = psiFile.getProject();
    Sdk sdk = null;
    final VirtualFile vfile = psiFile instanceof PsiFile ? ((PsiFile) psiFile).getOriginalFile().getVirtualFile() : psiFile.getVirtualFile();
    if (vfile != null) { // reality
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      sdk = projectRootManager.getProjectSdk();
      if (sdk == null) {
        final List<OrderEntry> orderEntries = projectRootManager.getFileIndex().getOrderEntriesForFile(vfile);
        for (OrderEntry orderEntry : orderEntries) {
          if (orderEntry instanceof JdkOrderEntry) {
            sdk = ((JdkOrderEntry)orderEntry).getJdk();
          }
          else if (orderEntry instanceof ModuleLibraryOrderEntryImpl) {
            sdk = PythonSdkType.findPythonSdk(orderEntry.getOwnerModule());
          }
        }
      }
    }
    return sdk;
  }

  @Nullable
  public static PyFile getBuiltinsForSdk(@NotNull Project project, @NotNull Sdk sdk) {
    return getSkeletonFile(project, sdk, PythonSdkType.getBuiltinsFileName(sdk));
  }

  @Nullable
  public static PyFile getSkeletonFile(final @NotNull Project project, @NotNull Sdk sdk, @NotNull String name) {
    SdkTypeId sdkType = sdk.getSdkType();
    if (sdkType instanceof PythonSdkType) {
      // dig out the builtins file, create an instance based on it
      final String[] urls = sdk.getRootProvider().getUrls(PythonSdkType.BUILTIN_ROOT_TYPE);
      for (String url : urls) {
        if (url.contains(PythonSdkType.SKELETON_DIR_NAME)) {
          final String builtins_url = url + "/" + name;
          File builtins = new File(VfsUtilCore.urlToPath(builtins_url));
          if (builtins.isFile() && builtins.canRead()) {
            final VirtualFile builtins_vfile = LocalFileSystem.getInstance().findFileByIoFile(builtins);
            if (builtins_vfile != null) {
              final Ref<PyFile> result = Ref.create();
              ApplicationManager.getApplication().runReadAction(() -> {
                PsiFile file = PsiManager.getInstance(project).findFile(builtins_vfile);
                if (file instanceof PyFile) {
                  result.set((PyFile)file);
                }
              });
              return result.get();

            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public PyType createLiteralCollectionType(final PySequenceExpression sequence, final String name, @NotNull TypeEvalContext context) {
    final PyClass cls = getClass(name);
    if (cls != null) {
      return new PyCollectionTypeImpl(cls, false, getSequenceElementTypes(sequence, context));
    }
    return null;
  }

  @NotNull
  private static List<PyType> getSequenceElementTypes(@NotNull PySequenceExpression sequence, @NotNull TypeEvalContext context) {
    final PyExpression[] elements = sequence.getElements();
    if (elements.length == 0 || elements.length > 10 /* performance */) {
      return Collections.singletonList(null);
    }
    final PyType firstElementType = context.getType(elements[0]);
    if (firstElementType == null) {
      return Collections.singletonList(null);
    }
    for (int i = 1; i < elements.length; i++) {
      final PyType elementType = context.getType(elements[i]);
      if (elementType == null || !elementType.equals(firstElementType)) {
        return Collections.singletonList(null);
      }
    }
    if (sequence instanceof PyDictLiteralExpression) {
      if (firstElementType instanceof PyTupleType) {
        final PyTupleType tupleType = (PyTupleType)firstElementType;
        if (tupleType.getElementCount() == 2) {
          return Arrays.asList(tupleType.getElementType(0), tupleType.getElementType(1));
        }
      }
      return Arrays.asList(null, null);
    }
    else {
      return Collections.singletonList(firstElementType);
    }
  }

  @Nullable
  public PyFile getBuiltinsFile() {
    return myBuiltinsFile;
  }

  public boolean isValid() {
    return myBuiltinsFile != null && myBuiltinsFile.isValid();
  }

  /**
   * Looks for a top-level named item. (Package builtins does not contain any sensible nested names anyway.)
   * @param name to look for
   * @return found element, or null.
   */
  @Nullable
  public PsiElement getByName(@NonNls String name) {
    if (myBuiltinsFile != null) {
      final PsiElement element = myBuiltinsFile.getElementNamed(name);
      if (element != null) {
        return element;
      }
    }
    if (myExceptionsFile != null) {
      return myExceptionsFile.getElementNamed(name);
    }
    return null;
  }

  @Nullable
  public PyClass getClass(@NonNls String name) {
    if (myBuiltinsFile != null) {
      return myBuiltinsFile.findTopLevelClass(name);
    }
    return null;
  }

  @Nullable
  public PyClassTypeImpl getObjectType(@NonNls String name) {
    PyClassTypeImpl val;
    synchronized (myTypeCache) {
      if (myBuiltinsFile != null) {
        if (myBuiltinsFile.getModificationStamp() != myModStamp) {
          myTypeCache.clear();
          myModStamp = myBuiltinsFile.getModificationStamp();
        }
      }
      val = myTypeCache.get(name);
    }
    if (val == null) {
      PyClass cls = getClass(name);
      if (cls != null) { // null may happen during testing
        val = new PyClassTypeImpl(cls, false);
        val.assertValid(name);
        synchronized (myTypeCache) {
          myTypeCache.put(name, val);
        }
      }
    }
    else {
      val.assertValid(name);
    }
    return val;
  }

  @Nullable
  public PyClassType getObjectType() {
    return getObjectType("object");
  }

  @Nullable
  public PyClassType getListType() {
    return getObjectType("list");
  }

  @Nullable
  public PyClassType getDictType() {
    return getObjectType("dict");
  }

  @Nullable
  public PyClassType getSetType() {
    return getObjectType("set");
  }

  @Nullable
  public PyClassType getTupleType() {
    return getObjectType("tuple");
  }

  @Nullable
  public PyClassType getIntType() {
    return getObjectType("int");
  }

  @Nullable
  public PyClassType getFloatType() {
    return getObjectType("float");
  }

  @Nullable
  public PyClassType getComplexType() {
    return getObjectType("complex");
  }

  @Nullable
  public PyClassType getStrType() {
    return getObjectType("str");
  }

  @Nullable
  public PyClassType getBytesType(LanguageLevel level) {
    if (level.isPy3K()) {
      return getObjectType("bytes");
    }
    else {
      return getObjectType("str");
    }
  }

  @Nullable
  public PyClassType getUnicodeType(LanguageLevel level) {
    if (level.isPy3K()) {
      return getObjectType("str");
    }
    else {
      return getObjectType("unicode");
    }
  }

  @Nullable
  public PyType getStringType(LanguageLevel level) {
    if (level.isPy3K()) {
      return getObjectType("str");
    }
    else {
      return getStrOrUnicodeType();
    }
  }

  @Nullable
  public PyType getByteStringType(@NotNull LanguageLevel level) {
    if (level.isPy3K()) {
      return getObjectType("bytes");
    }
    else {
      return getStrOrUnicodeType();
    }
  }

  private PyType getStrOrUnicodeType() {
    return PyUnionType.union(getObjectType("str"), getObjectType("unicode"));
  }

  @Nullable
  public PyClassType getBoolType() {
    return getObjectType("bool");
  }

  @Nullable
  public PyClassType getOldstyleClassobjType() {
    return getObjectType(PyNames.FAKE_OLD_BASE);
  }

  @Nullable
  public PyClassType getClassMethodType() {
    return getObjectType("classmethod");
  }

  @Nullable
  public PyClassType getStaticMethodType() {
    return getObjectType("staticmethod");
  }

  @Nullable
  public PyClassType getTypeType() {
    return getObjectType("type");
  }

  /**
   * @param target an element to check.
   * @return true iff target is inside the __builtins__.py
   */
  public boolean isBuiltin(@Nullable PsiElement target) {
    if (target == null) return false;
    PyPsiUtils.assertValid(target);
    if (! target.isValid()) return false;
    final PsiFile the_file = target.getContainingFile();
    if (!(the_file instanceof PyFile)) {
      return false;
    }
    // files are singletons, no need to compare URIs
    return the_file == myBuiltinsFile || the_file == myExceptionsFile;
  }

  public static boolean isInBuiltins(@NotNull PyExpression expression) {
    if (expression instanceof PyQualifiedExpression && (((PyQualifiedExpression)expression).isQualified())) {
      return false;
    }
    final String name = expression.getName();
    PsiReference reference = expression.getReference();
    if (reference != null && name != null) {
      final PyBuiltinCache cache = getInstance(expression);
      if (cache.getByName(name) != null) {
        final PsiElement resolved = reference.resolve();
        if (resolved != null && cache.isBuiltin(resolved)) {
          return true;
        }
      }
    }
    return false;
  }
}
