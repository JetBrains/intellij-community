package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
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
* @author yole
*/
public class ImportResolver implements RootVisitor {
  final boolean myCheckForPackage;
  @Nullable private Module myModule;
  private PsiElement myFoothold;
  private PsiFile myFootholdFile;
  private final @NotNull PyQualifiedName myQualifiedName;
  @NotNull PsiManager myPsiManager;
  final Set<PsiElement> results = Sets.newLinkedHashSet();
  private boolean myAcceptRootAsTopLevelPackage;
  private boolean myVisitAllModules = false;
  private Sdk myWithSdk;

  public ImportResolver(@NotNull PyQualifiedName qName, boolean checkForPackage) {
    myQualifiedName = qName;
    myCheckForPackage = checkForPackage;
  }

  public ImportResolver fromElement(@NotNull PsiElement foothold) {
    myFoothold = foothold;
    myFootholdFile = foothold.getContainingFile();
    myPsiManager = PsiManager.getInstance(foothold.getProject());
    setModule(ModuleUtil.findModuleForPsiElement(myFoothold));
    if (PydevConsoleRunner.isInPydevConsole(foothold)) {
      withAllModules();
    }
    return this;
  }

  public ImportResolver fromModule(@NotNull Module module) {
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

  public ImportResolver withAllModules() {
    myVisitAllModules = true;
    return this;
  }

  public ImportResolver withSdk(Sdk sdk) {
    myWithSdk = sdk;
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
    if (myFoothold != null && !myFoothold.isValid()) {
      return Collections.emptyList();
    }

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
    else if (myFoothold != null) {
      RootVisitorHost.visitSdkRoots(myFoothold, this);
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
  
  @Nullable
  public PsiElement firstResult() {
    final List<PsiElement> results = resultsAsList();
    return results.size() > 0 ? results.get(0) : null;
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
}
