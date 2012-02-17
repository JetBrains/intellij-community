package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyLiteralCollectionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides access to Python builtins via skeletons.
 */
public class PyBuiltinCache {
  public static final @NonNls String BUILTIN_FILE = "__builtin__.py";
  @NonNls public static final String BUILTIN_FILE_3K = "builtins.py";

  private PyType STRING_TYPE_PY2 = null;

  /**
   * Returns an instance of builtin cache. Instances differ per module and are cached.
   * @param reference something to define the module from.
   * @return an instance of cache. If reference was null, the instance is a fail-fast dud one.
   */
  @NotNull
  public static PyBuiltinCache getInstance(@Nullable PsiElement reference) {
    if (reference != null && reference.isValid()) {
      Sdk sdk = findSdkForFile(reference.getContainingFile());
      if (sdk != null) {
        return PythonSdkPathCache.getInstance(reference.getProject(), sdk).getBuiltins();
      }
    }
    return DUD_INSTANCE; // a non-functional fail-fast instance, for a case when skeletons are not available
  }

  @Nullable
  public static Sdk findSdkForFile(PsiFileSystemItem psifile) {
    if (psifile == null) {
      return null;
    }
    Module module = ModuleUtil.findModuleForPsiElement(psifile);
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
  public static PyFile getBuiltinsForSdk(Project project, Sdk sdk) {
    SdkType sdk_type = sdk.getSdkType();
    if (sdk_type instanceof PythonSdkType) {
      // dig out the builtins file, create an instance based on it
      final String[] urls = sdk.getRootProvider().getUrls(PythonSdkType.BUILTIN_ROOT_TYPE);
      for (String url : urls) {
        if (url.contains(PythonSdkType.SKELETON_DIR_NAME)) {
          final String builtins_url = url + "/" + PythonSdkType.getBuiltinsFileName(sdk);
          File builtins = new File(VfsUtil.urlToPath(builtins_url));
          if (builtins.isFile() && builtins.canRead()) {
            VirtualFile builtins_vfile = LocalFileSystem.getInstance().findFileByIoFile(builtins);
            if (builtins_vfile != null) {
              PsiFile file = PsiManager.getInstance(project).findFile(builtins_vfile);
              if (file instanceof PyFile) {
                return (PyFile)file;
              }
            }
          }
        }
      }
    }
    return null;
  }

  private static final PyBuiltinCache DUD_INSTANCE = new PyBuiltinCache((PyFile)null);

  @Nullable
  static PyType createLiteralCollectionType(final PySequenceExpression sequence, final String name) {
    final PyBuiltinCache builtinCache = getInstance(sequence);
    final PyClass setClass = builtinCache.getClass(name);
    if (setClass != null) {
      return new PyLiteralCollectionType(setClass, false, sequence);
    }
    return null;
  }


  private PyFile myBuiltinsFile;

  public PyBuiltinCache() {
  }


  public static final Key<String> MARKER_KEY = new Key<String>("python.builtins.skeleton.file");

  public PyBuiltinCache(@Nullable final PyFile builtins) {
    myBuiltinsFile = builtins;
    if (myBuiltinsFile != null) {
      myBuiltinsFile.putUserData(MARKER_KEY, ""); // mark this file as builtins
    }
  }

  @Nullable
  public PyFile getBuiltinsFile() {
    return myBuiltinsFile;
  }

  public boolean isValid() {
    return myBuiltinsFile == null || myBuiltinsFile.isValid();
  }

  /**
   * Looks for a top-level named item. (Package builtins does not contain any sensible nested names anyway.)
   * @param name to look for
   * @return found element, or null.
   */
  @Nullable
  public PsiElement getByName(@NonNls String name) {
    if (myBuiltinsFile != null) {
      return myBuiltinsFile.getElementNamed(name);
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

  /**
   * Stores the most often used types, returned by getNNNType().
   */
  private final Map<String, PyClassType> myTypeCache = new HashMap<String, PyClassType>();
  private final Map<String, Ref<PyType>> myStdlibTypeCache = new HashMap<String, Ref<PyType>>();

  /**
  @return
  */
  @Nullable
  public PyClassType getObjectType(@NonNls String name) {
    PyClassType val = myTypeCache.get(name);
    if (val == null) {
      PyClass cls = getClass(name);
      if (cls != null) { // null may happen during testing
        val = new PyClassType(cls, false);
        myTypeCache.put(name, val);
      }
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
      if (STRING_TYPE_PY2 == null) {
        STRING_TYPE_PY2 = PyUnionType.union(getObjectType("str"), getObjectType("unicode"));
      }
      return STRING_TYPE_PY2;
    }
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

  public Ref<PyType> getStdlibType(@NotNull String key) {
    synchronized (myStdlibTypeCache) {
      return myStdlibTypeCache.get(key);
    }
  }

  public void storeStdlibType(@NotNull String key, @Nullable PyType result) {
    synchronized (myStdlibTypeCache) {
      myStdlibTypeCache.put(key, new Ref<PyType>(result));
    }
  }

  /**
   * @param target an element to check.
   * @return true iff target is inside the __builtins__.py
   */
  public boolean hasInBuiltins(@Nullable PsiElement target) {
    if (target == null) return false;
    if (! target.isValid()) return false;
    final PsiFile the_file = target.getContainingFile();
    if (!(the_file instanceof PyFile)) {
      return false;
    }
    return myBuiltinsFile == the_file; // files are singletons, no need to compare URIs
  }
}
