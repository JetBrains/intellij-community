package com.jetbrains.python.psi.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides access to Python builtins via skeletons.
 */
public class PyBuiltinCache {

  public static final @NonNls String BUILTIN_FILE = "__builtin__.py"; 

  @NotNull
  public static PyBuiltinCache getInstance(PsiElement reference) {
    if (reference != null) {
      final Module module = ModuleUtil.findModuleForPsiElement(reference);
      final Project project = reference.getProject();
      ComponentManager instance_key = module != null ? module : project;
      // got a cached one?
      PyBuiltinCache instance = ourInstanceCache.get(instance_key);
      if (instance != null) {
        return instance;
      }
      // actually create an instance
      Sdk sdk = null;
      if (module != null) {
        sdk = PythonSdkType.findPythonSdk(module);
      }
      else {
        final PsiFile psifile = reference.getContainingFile();
        if (psifile != null) {  // formality
          final VirtualFile vfile = psifile.getVirtualFile();
          if (vfile != null) { // reality
            sdk = ProjectRootManager.getInstance(project).getProjectJdk();
          }
        }
      }
      if (sdk != null) {
        // dig out the builtins file, create an instance based on it
        final String[] urls = sdk.getRootProvider().getUrls(PythonSdkType.BUITLIN_ROOT_TYPE);
        for (String url : urls) {
          if (url.contains(PythonSdkType.SKELETON_DIR_NAME)) {
            final String builtins_url = url + "/" + ((PythonSdkType)sdk.getSdkType()).getBuiltinsFileName(sdk);
            File builtins = new File(VfsUtil.urlToPath(builtins_url));
            if (builtins.isFile() && builtins.canRead()) {
              VirtualFile builtins_vfile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(builtins);
              if (builtins_vfile != null) {
                PsiFile builtins_psifile = PsiManager.getInstance(project).findFile(builtins_vfile);
                if (builtins_psifile instanceof PyFile) {
                  instance = new PyBuiltinCache((PyFile)builtins_psifile);
                  ourInstanceCache.put(instance_key, instance);
                  return instance;
                }
              }
            }
          }
        }
      }
    }
    return DUD_INSTANCE; // a non-functional fail-fast instance, for a case when skeletons are not available
  }

  private static final PyBuiltinCache DUD_INSTANCE = new PyBuiltinCache((PyFile)null);

  /**
   * Here we store our instances, keyed either by module or by project (for the module-less case of PyCharm).
   */
  private static final Map<ComponentManager, PyBuiltinCache> ourInstanceCache = new HashMap<ComponentManager, PyBuiltinCache>();

  private Project myProject;
  private PyFile myBuiltinsFile;

  public PyBuiltinCache(final Project project) {
    myProject = project;
  }

  private PyBuiltinCache(@Nullable final PyFile builtins) {
    myBuiltinsFile = builtins;
  }

  @Nullable
  public PyFile getBuiltinsFile() {
    return myBuiltinsFile;
  }

  /**
   * Looks for a top-level named item. (Package builtins does not contain any sensible nested names anyway.)
   * @param name to look for
   * @param type to look for and cast to (most often, PyFunction or PyClass)
   * @return found element, or null.
   */
  @Nullable
  public <T extends PsiNamedElement> T getByName(@NonNls String name, @NotNull Class<T> type) {
    if (myBuiltinsFile != null) {
      for(PsiElement element: myBuiltinsFile.getChildren()) {
        if (type.isInstance(element)) {
          final T cast = type.cast(element);
          if ((name != null) && name.equals(cast.getName())) {
            return cast;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public PyClass getClass(@NonNls String name) {
    return getByName(name, PyClass.class);
  }

  /**
   * Stores the most often used types, returned by getNNNType().
   */
  private Map<String,PyClassType> myTypeCache = new HashMap<String, PyClassType>();
  
  /**
  @return 
  */
  @Nullable
  private PyClassType _getObjectType(@NonNls String name) {
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
    return _getObjectType("object");
  }
  
  @Nullable
  public PyClassType getListType() {
    return _getObjectType("list");
  }
  
  @Nullable
  public PyClassType getDictType() {
    return _getObjectType("dict");
  }

  @Nullable
  public PyClassType getIntType() {
    return _getObjectType("int");
  }

  @Nullable
  public PyClassType getFloatType() {
    return _getObjectType("float");
  }

  @Nullable
  public PyClassType getComplexType() {
    return _getObjectType("complex");
  }

  @Nullable
  public PyClassType getStrType() {
    return _getObjectType("str");
  }

  @Nullable
  public PyClassType getOldstyleClassobjType() {
    return _getObjectType("___Classobj");
  }


  /**
   * @param target an element to check.
   * @return true iff target is inside the __builtins__.py 
   */
  public static boolean hasInBuiltins(@Nullable PsiElement target) {
    if (target == null) return false;
    if (! target.isValid()) return false;
    final PsiFile the_file = target.getContainingFile();
    if (the_file == null) {
      return false;
    }
    return PyBuiltinCache.BUILTIN_FILE.equals(the_file.getName()); // TODO: make it check against the actual file; unmake it static
  }
}
