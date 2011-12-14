package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.django.facet.DjangoFacetType;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Resolves the specified qualified name in the specified context (module, all modules or a file) to a file or directory.
 *
 * @author yole
 */
public class QualifiedNameResolver implements RootVisitor {
  boolean myCheckForPackage = true;
  @Nullable private Module myModule;
  private PsiFile myFootholdFile;
  private final @NotNull PyQualifiedName myQualifiedName;
  @NotNull PsiManager myPsiManager;
  final Set<PsiElement> results = Sets.newLinkedHashSet();
  private boolean myAcceptRootAsTopLevelPackage;
  private boolean myVisitAllModules = false;
  private int myRelativeLevel = -1;
  private boolean myWithoutRoots;
  private Sdk myWithSdk;

  public QualifiedNameResolver(@NotNull String qNameString) {
    myQualifiedName = PyQualifiedName.fromDottedString(qNameString);
  }

  public QualifiedNameResolver(@NotNull PyQualifiedName qName) {
    myQualifiedName = qName;
  }

  public QualifiedNameResolver fromElement(@NotNull PsiElement foothold) {
    myFootholdFile = foothold.getContainingFile().getOriginalFile();
    myPsiManager = PsiManager.getInstance(foothold.getProject());
    setModule(ModuleUtil.findModuleForPsiElement(myFootholdFile));
    if (PydevConsoleRunner.isInPydevConsole(foothold)) {
      withAllModules();
    }
    return this;
  }

  public QualifiedNameResolver fromModule(@NotNull Module module) {
    setModule(module);
    myPsiManager = PsiManager.getInstance(module.getProject());
    return this;
  }

  private void setModule(Module module) {
    myModule = module;
    if (module != null && FacetManager.getInstance(module).getFacetByType(DjangoFacetType.ID) != null) {
      myAcceptRootAsTopLevelPackage = true;
    }
  }

  public QualifiedNameResolver withAllModules() {
    myVisitAllModules = true;
    return this;
  }

  /**
   * Specifies that we need to look for the name in the specified SDK (instead of the SDK assigned to the module, if any).
   *
   * @param sdk the SDK in which the name should be searched.
   * @return this
   */
  public QualifiedNameResolver withSdk(Sdk sdk) {
    myWithSdk = sdk;
    return this;
  }

  /**
   * Specifies whether we should attempt to resolve imports relative to the current file.
   * 
   * @param relativeLevel if >= 0, we try to resolve at the specified number of levels above the current file.
   * @return this
   */
  public QualifiedNameResolver withRelative(int relativeLevel) {
    myRelativeLevel = relativeLevel;
    return this;
  }

  /**
   * Specifies that we should only try to resolve relative to the current file, not in roots.
   *
   * @return this
   */
  public QualifiedNameResolver withoutRoots() {
    myWithoutRoots = true;
    return this;
  }

  /**
   * Specifies that we're looking for a file in a directory hierarchy, not a module in the Python package hierarchy
   * (so we don't need to check for existence of __init__.py)
   *
   * @return
   */
  public QualifiedNameResolver withPlainDirectories() {
    myCheckForPackage = false;
    return this;
  }
  
  public boolean visitRoot(final VirtualFile root) {
    if (!root.isValid()) {
      return true;
    }
    PsiElement module = resolveInRoot(root);
    if (module != null) {
      results.add(module);
    }

    if (myAcceptRootAsTopLevelPackage && myQualifiedName.matchesPrefix(PyQualifiedName.fromDottedString(root.getName()))) {
      module = resolveInRoot(root.getParent());
      if (module != null) {
        results.add(module);
      }
    }

    return true;
  }

  @NotNull
  public List<PsiElement> resultsAsList() {
    if (myFootholdFile != null && !myFootholdFile.isValid()) {
      return Collections.emptyList();
    }

    if (myRelativeLevel >= 0) {
      assert myFootholdFile != null;
      PsiDirectory dir = myFootholdFile.getContainingDirectory();
      if (myRelativeLevel > 0) {
        dir = ResolveImportUtil.stepBackFrom(myFootholdFile, myRelativeLevel);
        
      }
      PsiElement module = resolveModuleAt(dir);
      if (module != null) {
        results.add(module);
      }
    }

    if (!myWithoutRoots) {
      PythonPathCache cache = findMyCache();
      if (cache != null) {
        final List<PsiElement> cachedResults = cache.get(myQualifiedName);
        if (cachedResults != null) {
          return cachedResults;
        }
      }

      if (myVisitAllModules) {
        for (Module mod : ModuleManager.getInstance(myPsiManager.getProject()).getModules()) {
          RootVisitorHost.visitRoots(mod, false, this);
        }
      }
      else if (myModule != null) {
        final boolean otherSdk = withOtherSdk();
        RootVisitorHost.visitRoots(myModule, otherSdk, this);
        if (otherSdk) {
          RootVisitorHost.visitSdkRoots(myWithSdk, this);
        }
      }
      else if (myFootholdFile != null) {
        RootVisitorHost.visitSdkRoots(myFootholdFile, this);
      }
      else {
        throw new IllegalStateException();
      }

      final ArrayList<PsiElement> resultList = Lists.newArrayList(results);
      if (cache != null) {
        cache.put(myQualifiedName, resultList);
      }
      return resultList;
    }

    return Lists.newArrayList(results);
  }
  
  @Nullable
  public PsiElement firstResult() {
    final List<PsiElement> results = resultsAsList();
    return results.size() > 0 ? results.get(0) : null;
  }

  @NotNull
  public <T extends PsiElement> List<T> resultsOfType(Class<T> clazz) {
    List<T> result = new ArrayList<T>();
    for (PsiElement element : resultsAsList()) {
      if (clazz.isInstance(element)) {
        //noinspection unchecked
        result.add((T) element);
      }
    }
    return result;
  } 

  @Nullable
  public <T extends PsiElement> T firstResultOfType(Class<T> clazz) {
    final List<T> list = resultsOfType(clazz);
    return list.size() > 0 ? list.get(0) : null;
  } 

  private boolean withOtherSdk() {
    return myWithSdk != null && myWithSdk != PythonSdkType.findPythonSdk(myModule);
  }

  @Nullable
  private PythonPathCache findMyCache() {
    if (myVisitAllModules) {
      return null;
    }
    if (myModule != null) {
      return withOtherSdk() ? null : PythonModulePathCache.getInstance(myModule);
    }
    if (myFootholdFile != null) {
      final Sdk sdk = PyBuiltinCache.findSdkForFile(myFootholdFile);
      if (sdk != null) {
        return PythonSdkPathCache.getInstance(myPsiManager.getProject(), sdk);
      }
    }
    return null;
  }

  @Nullable
  private PsiElement resolveInRoot(VirtualFile root) {
    PsiElement module = root.isDirectory() ? myPsiManager.findDirectory(root) : myPsiManager.findFile(root);
    if (module == null) return null;
    for (String component : myQualifiedName.getComponents()) {
      if (component == null) {
        module = null;
        break;
      }
      module = ResolveImportUtil.resolveChild(module, component, myFootholdFile, root, true, myCheckForPackage); // only files, we want a module
    }
    return module;
  }

  /**
   * Searches for a module at given directory, unwinding qualifiers and traversing directories as needed.
   *
   * @param directory     where to start from; top qualifier will be searched for here.
   * @return module's file, or null.
   */
  @Nullable
  private PsiElement resolveModuleAt(@Nullable PsiDirectory directory) {
    // prerequisites
    if (directory == null || !directory.isValid()) return null;

    PsiElement seeker = directory;
    for (String name : myQualifiedName.getComponents()) {
      if (name == null) {
        return null;
      }
      seeker = ResolveImportUtil.resolveChild(seeker, name, myFootholdFile, null, true, true);
    }
    return seeker;
  }

}
