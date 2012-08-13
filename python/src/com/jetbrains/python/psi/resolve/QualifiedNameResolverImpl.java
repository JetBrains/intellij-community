package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
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
public class QualifiedNameResolverImpl implements RootVisitor, QualifiedNameResolver {
  boolean myCheckForPackage = true;
  private final QualifiedNameResolveContext myContext = new QualifiedNameResolveContext();
  private final @NotNull PyQualifiedName myQualifiedName;
  final Set<PsiElement> results = Sets.newLinkedHashSet();
  private boolean myVisitAllModules = false;
  private int myRelativeLevel = -1;
  private boolean myWithoutRoots;

  public QualifiedNameResolverImpl(@NotNull String qNameString) {
    myQualifiedName = PyQualifiedName.fromDottedString(qNameString);
  }

  public QualifiedNameResolverImpl(@NotNull PyQualifiedName qName) {
    myQualifiedName = qName;
  }

  @Override
  public QualifiedNameResolver withContext(QualifiedNameResolveContext context) {
    myContext.copyFrom(context);
    return this;
  }

  @Override
  public QualifiedNameResolver fromElement(@NotNull PsiElement foothold) {
    myContext.setFromElement(foothold);
    if (PydevConsoleRunner.isInPydevConsole(foothold)) {
      withAllModules();
    }
    return this;
  }

  @Override
  public QualifiedNameResolver fromModule(@NotNull Module module) {
    myContext.setFromModule(module);
    return this;
  }

  @Override
  public QualifiedNameResolver fromSdk(@NotNull Project project, @NotNull Sdk sdk) {
    myContext.setFromSdk(project, sdk);
    return this;
  }

  private boolean isAcceptRootAsTopLevelPackage() {
    Module module = myContext.getModule();
    return module != null && FacetManager.getInstance(module).getFacetByType(DjangoFacetType.ID) != null;
  }

  @Override
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
  @Override
  public QualifiedNameResolver withSdk(Sdk sdk) {
    myContext.setSdk(sdk);
    return this;
  }

  /**
   * Specifies whether we should attempt to resolve imports relative to the current file.
   * 
   * @param relativeLevel if >= 0, we try to resolve at the specified number of levels above the current file.
   * @return this
   */
  @Override
  public QualifiedNameResolver withRelative(int relativeLevel) {
    myRelativeLevel = relativeLevel;
    return this;
  }

  /**
   * Specifies that we should only try to resolve relative to the current file, not in roots.
   *
   * @return this
   */
  @Override
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
  @Override
  public QualifiedNameResolver withPlainDirectories() {
    myCheckForPackage = false;
    return this;
  }
  
  public boolean visitRoot(final VirtualFile root, @Nullable Module module, @Nullable Sdk sdk) {
    if (!root.isValid()) {
      return true;
    }
    PsiFileSystemItem resolveResult = resolveInRoot(root);
    if (resolveResult != null) {
      results.add(resolveResult);
    }

    if (isAcceptRootAsTopLevelPackage() && myQualifiedName.matchesPrefix(PyQualifiedName.fromDottedString(root.getName()))) {
      resolveResult = resolveInRoot(root.getParent());
      if (resolveResult != null) {
        results.add(resolveResult);
      }
    }

    return true;
  }

  @Override
  @NotNull
  public List<PsiElement> resultsAsList() {
    if (!myContext.isValid()) {
      return Collections.emptyList();
    }

    if (myRelativeLevel >= 0) {
      PsiFile footholdFile = myContext.getFootholdFile();
      assert footholdFile != null;
      PsiDirectory dir = footholdFile.getContainingDirectory();
      if (myRelativeLevel > 0) {
        dir = ResolveImportUtil.stepBackFrom(footholdFile, myRelativeLevel);
      }

      PsiFileSystemItem module = resolveModuleAt(dir);
      if (module != null) {
        results.add(module);
      }
    }

    if (!myWithoutRoots) {
      results.addAll(resolveInRoots());
    }

    return Lists.newArrayList(results);
  }

  private List<PsiElement> resolveInRoots() {
    PythonPathCache cache = findMyCache();
    if (cache != null) {
      final List<PsiElement> cachedResults = cache.get(myQualifiedName);
      if (cachedResults != null) {
        return cachedResults;
      }
    }

    if (myVisitAllModules) {
      for (Module mod : ModuleManager.getInstance(myContext.getProject()).getModules()) {
        RootVisitorHost.visitRoots(mod, false, this);
      }
    }
    else if (myContext.getModule() != null) {
      final boolean otherSdk = withOtherSdk();
      RootVisitorHost.visitRoots(myContext.getModule(), otherSdk, this);
      if (otherSdk) {
        RootVisitorHost.visitSdkRoots(myContext.getSdk(), this);
      }
    }
    else if (myContext.getFootholdFile() != null) {
      RootVisitorHost.visitSdkRoots(myContext.getFootholdFile(), this);
    }
    else if (myContext.getSdk() != null) {
      RootVisitorHost.visitSdkRoots(myContext.getSdk(), this);
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

  @Override
  @Nullable
  public PsiElement firstResult() {
    final List<PsiElement> results = resultsAsList();
    return results.size() > 0 ? results.get(0) : null;
  }

  @Override
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

  @Override
  @Nullable
  public <T extends PsiElement> T firstResultOfType(Class<T> clazz) {
    final List<T> list = resultsOfType(clazz);
    return list.size() > 0 ? list.get(0) : null;
  } 

  private boolean withOtherSdk() {
    return myContext.getSdk() != null && myContext.getSdk() != PythonSdkType.findPythonSdk(myContext.getModule());
  }

  @Nullable
  private PythonPathCache findMyCache() {
    if (myVisitAllModules) {
      return null;
    }
    if (myContext.getModule() != null) {
      return withOtherSdk() ? null : PythonModulePathCache.getInstance(myContext.getModule());
    }
    if (myContext.getFootholdFile() != null) {
      final Sdk sdk = PyBuiltinCache.findSdkForNonModuleFile(myContext.getFootholdFile());
      if (sdk != null) {
        return PythonSdkPathCache.getInstance(myContext.getProject(), sdk);
      }
    }
    return null;
  }

  @Nullable
  private PsiFileSystemItem resolveInRoot(VirtualFile root) {
    if (!root.isDirectory()) {
      // if we have added a file as a root, it's unlikely that we'll be able to resolve anything under it in 'files only' resolve mode
      return null;
    }
    return resolveModuleAt(myContext.getPsiManager().findDirectory(root));
  }

  /**
   * Searches for a module at given directory, unwinding qualifiers and traversing directories as needed.
   *
   * @param directory where to start from; top qualifier will be searched for here.
   */
  @Nullable
  private PsiFileSystemItem resolveModuleAt(@Nullable PsiDirectory directory) {
    // prerequisites
    if (directory == null || !directory.isValid()) return null;

    PsiFileSystemItem seeker = directory;
    for (String name : myQualifiedName.getComponents()) {
      if (name == null) {
        return null;
      }
      seeker = (PsiFileSystemItem) ResolveImportUtil.resolveChild(seeker, name, myContext.getFootholdFile(), true, myCheckForPackage);
    }
    return seeker;
  }

}
