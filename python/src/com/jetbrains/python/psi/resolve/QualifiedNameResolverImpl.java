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
package com.jetbrains.python.psi.resolve;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.PyUtil.turnDirIntoInit;

/**
 * Resolves the specified qualified name in the specified context (module, all modules or a file) to a file or directory.
 *
 * @author yole
 */
public class QualifiedNameResolverImpl implements RootVisitor, QualifiedNameResolver {
  boolean myCheckForPackage = true;
  private final QualifiedNameResolveContext myContext = new QualifiedNameResolveContext();
  private final @NotNull QualifiedName myQualifiedName;
  final Set<PsiElement> mySourceResults = Sets.newLinkedHashSet();
  final Set<PsiElement> myLibResults = Sets.newLinkedHashSet();
  final Set<PsiElement> myForeignResults = Sets.newLinkedHashSet();
  private boolean myVisitAllModules = false;
  private int myRelativeLevel = -1;
  private boolean myWithoutRoots;
  private boolean myWithoutForeign;
  private boolean myWithMembers;

  public QualifiedNameResolverImpl(@NotNull String qNameString) {
    myQualifiedName = QualifiedName.fromDottedString(qNameString);
  }

  /**
   * @param qName the empty name means that all found roots will be traversed.
   */
  public QualifiedNameResolverImpl(@NotNull QualifiedName qName) {
    myQualifiedName = qName;
  }

  @Override
  public QualifiedNameResolver withContext(QualifiedNameResolveContext context) {
    myContext.copyFrom(context);
    return this;
  }

  @Override
  public QualifiedNameResolver fromElement(@NotNull PsiElement foothold) {
    checkAccess();
    myContext.setFromElement(foothold);
    if (PydevConsoleRunner.isInPydevConsole(foothold) || PyUtil.isInScratchFile(foothold)) {
      withAllModules();
      Sdk sdk = PydevConsoleRunner.getConsoleSdk(foothold);
      if (sdk != null) {
        myContext.setSdk(sdk);
      }
    }
    return this;
  }

  @Override
  public QualifiedNameResolver fromModule(@NotNull Module module) {
    PyPsiUtils.assertValid(module);
    checkAccess();
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
    if (module != null) {
      Facet[] facets = FacetManager.getInstance(module).getAllFacets();
      for (Facet facet : facets) {
        if (facet instanceof PythonPathContributingFacet && ((PythonPathContributingFacet)facet).acceptRootAsTopLevelPackage()) {
          return true;
        }
      }
    }
    return false;
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

  @Override
  public QualifiedNameResolver withoutForeign() {
    myWithoutForeign = true;
    return this;
  }

  @Override
  public QualifiedNameResolver withMembers() {
    myWithMembers = true;
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

  public boolean visitRoot(final VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource) {
    checkAccess();
    if (!root.isValid()) {
      return true;
    }
    if (root.equals(PyUserSkeletonsUtil.getUserSkeletonsDirectory())) {
      return true;
    }
    PsiElement resolveResult = resolveInRoot(root);
    if (resolveResult != null) {
      addRoot(resolveResult, isModuleSource);
    }

    if (isAcceptRootAsTopLevelPackage() && myQualifiedName.matchesPrefix(QualifiedName.fromDottedString(root.getName()))) {
      resolveResult = resolveInRoot(root.getParent());
      if (resolveResult != null) {
        addRoot(resolveResult, isModuleSource);
      }
    }

    return true;
  }

  private void addRoot(PsiElement resolveResult, boolean isModuleSource) {
    final Set<PsiElement> results = isModuleSource ? mySourceResults : myLibResults;
    final boolean allNamespacePackages = allNamespacePackages(results);
    if (allNamespacePackages) {
      if (!isNamespacePackage(resolveResult)) {
        results.clear();
      }
    }
    if (allNamespacePackages || results.isEmpty() || myQualifiedName.getComponentCount() == 0) {
      results.add(resolveResult);
    }
  }

  private static boolean isNamespacePackage(@NotNull PsiElement element) {
    final PsiDirectory dir = as(element, PsiDirectory.class);
    if (dir != null) {
      final LanguageLevel level = PyUtil.getLanguageLevelForVirtualFile(dir.getProject(), dir.getVirtualFile());
      if (level.isAtLeast(LanguageLevel.PYTHON33)) {
        return turnDirIntoInit(dir) == null;
      }
    }
    return false;
  }

  private static boolean allNamespacePackages(@NotNull Collection<PsiElement> elements) {
    for (PsiElement element : elements) {
      if (!isNamespacePackage(element)) {
        return false;
      }
    }
    return true;
  }

  @Override
  @NotNull
  public List<PsiElement> resultsAsList() {
    checkAccess();
    if (!myContext.isValid()) {
      return Collections.emptyList();
    }

    final PsiFile footholdFile = myContext.getFootholdFile();
    checkValidForTests(footholdFile);
    boolean foundRelativeImport = false;
    if (myRelativeLevel >= 0 && footholdFile != null && !PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(footholdFile)) {
      PsiDirectory dir = footholdFile.getContainingDirectory();
      checkValidForTests(dir);
      if (myRelativeLevel > 0) {
        dir = ResolveImportUtil.stepBackFrom(footholdFile, myRelativeLevel);
        checkValidForTests(dir);
      }

      PsiElement module = resolveModuleAt(dir);
      checkValidForTests(module);
      if (module != null) {
        foundRelativeImport = isRelativeImportResult(dir, module);
        addRoot(module, true);
      }
    }

    final PythonPathCache cache = findMyCache();
    final boolean mayCache = cache != null && !myWithoutRoots && !myWithoutForeign && !foundRelativeImport;
    if (mayCache) {
      final List<PsiElement> cachedResults = cache.get(myQualifiedName);
      if (cachedResults != null) {
        cachedResults.stream().forEach(QualifiedNameResolverImpl::checkValidForTests);
        mySourceResults.addAll(cachedResults);
        return Lists.newArrayList(mySourceResults);
      }
    }

    if (!myWithoutRoots) {
      addResultsFromRoots();
    }
    else if (footholdFile != null) {
      addRelativeImportResultsFromSkeletons(footholdFile);
    }

    if (mySourceResults.isEmpty() || myQualifiedName.getComponentCount() == 0) {
      mySourceResults.addAll(myLibResults);
      myLibResults.clear();
    }

    if (!myWithoutForeign) {
      if (mySourceResults.isEmpty() || myQualifiedName.getComponentCount() == 0) {
        for (PyImportResolver resolver : Extensions.getExtensions(PyImportResolver.EP_NAME)) {
          PsiElement foreign = resolver.resolveImportReference(myQualifiedName, myContext, !myWithoutRoots);
          if (foreign != null) {
            myForeignResults.add(foreign);
          }
        }
        mySourceResults.addAll(myForeignResults);
        myForeignResults.clear();
      }
    }

    final ArrayList<PsiElement> results = Lists.newArrayList(mySourceResults);
    if (mayCache) {
      cache.put(myQualifiedName, results);
    }
    return results;
  }

  
  private boolean isRelativeImportResult(@NotNull PsiDirectory rootDirectory, @NotNull PsiElement result) {
    if (myRelativeLevel > 0) {
      return true;
    }
    final boolean isPython2 = LanguageLevel.forElement(rootDirectory).isOlderThan(LanguageLevel.PYTHON30);
    if (myRelativeLevel == 0 && isPython2 && PyUtil.isPackage(rootDirectory, false, null)) {
      // Candidate for implicit relative import doesn't necessarily means that the same module cannot be imported absolutely 
      final PsiFileSystemItem moduleOrPackage = as(result, PsiFileSystemItem.class);
      if (moduleOrPackage != null && !myQualifiedName.equals(QualifiedNameFinder.findShortestImportableQName(moduleOrPackage))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolve relative imports from sdk root to the skeleton dir
   */
  private void addRelativeImportResultsFromSkeletons(@NotNull final PsiFile foothold) {
    final VirtualFile vFile = foothold.getVirtualFile();
    if (vFile == null || FileIndexFacade.getInstance(foothold.getProject()).isInContent(vFile)) {
      return;
    }
    PsiDirectory containingDirectory = foothold.getContainingDirectory();
    if (myRelativeLevel > 0) {
      containingDirectory = ResolveImportUtil.stepBackFrom(foothold, myRelativeLevel);
    }
    if (containingDirectory != null) {
      final QualifiedName containingQName = QualifiedNameFinder.findCanonicalImportPath(containingDirectory, null);
      if (containingQName != null && containingQName.getComponentCount() > 0) {
        final QualifiedName absoluteQName = containingQName.append(myQualifiedName.toString());
        final QualifiedNameResolverImpl absoluteVisitor =
          (QualifiedNameResolverImpl)new QualifiedNameResolverImpl(absoluteQName).fromElement(foothold);

        final Sdk sdk = PythonSdkType.getSdk(foothold);
        if (sdk == null) return;
        final VirtualFile skeletonsDir = PySdkUtil.findSkeletonsDir(sdk);
        if (skeletonsDir == null) return;
        final PsiDirectory directory = myContext.getPsiManager().findDirectory(skeletonsDir);
        final PsiElement psiElement = absoluteVisitor.resolveModuleAt(directory);
        if (psiElement != null) {
          addRoot(psiElement, false);
        }
      }
    }
  }

  private void addResultsFromRoots() {
    if (myVisitAllModules) {
      for (Module mod : ModuleManager.getInstance(myContext.getProject()).getModules()) {
        RootVisitorHost.visitRoots(mod, true, this);
      }

      if (myContext.getSdk() != null) {
        RootVisitorHost.visitSdkRoots(myContext.getSdk(), this);
      }
      else if (myContext.getFootholdFile() != null) {
        RootVisitorHost.visitSdkRoots(myContext.getFootholdFile(), this);
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
  }

  @Override
  @Nullable
  public PsiElement firstResult() {
    checkAccess();
    final List<PsiElement> results = resultsAsList();
    return results.size() > 0 ? results.get(0) : null;
  }

  @Override
  @NotNull
  public <T extends PsiElement> List<T> resultsOfType(Class<T> clazz) {
    checkAccess();
    List<T> result = new ArrayList<>();
    for (PsiElement element : resultsAsList()) {
      if (clazz.isInstance(element)) {
        //noinspection unchecked
        result.add((T)element);
      }
    }
    return result;
  }

  @Override
  @Nullable
  public <T extends PsiElement> T firstResultOfType(Class<T> clazz) {
    checkAccess();
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
  private PsiElement resolveInRoot(VirtualFile root) {
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
  @Contract("null -> null")
  @Nullable
  public PsiElement resolveModuleAt(@Nullable PsiDirectory directory) {
    // prerequisites
    checkAccess();
    PyPsiUtils.assertValid(directory);
    if (directory == null || !directory.isValid()) return null;

    PsiElement seeker = directory;
    for (String name : myQualifiedName.getComponents()) {
      if (name == null) {
        return null;
      }
      seeker = ResolveImportUtil.resolveChild(seeker, name, myContext.getFootholdFile(), !myWithMembers, myCheckForPackage);
    }
    return seeker;
  }

  @Override
  public Module getModule() {
    return myContext.getModule();
  }

  @Nullable
  @Override
  public <T extends PsiNamedElement> T resolveTopLevelMember(@NotNull final Class<T> aClass) {
    checkAccess();
    Preconditions.checkState(getModule() != null, "Module is not set");
    final String memberName = myQualifiedName.getLastComponent();
    if (memberName == null) {
      return null;
    }
    final PyFile file =
      new QualifiedNameResolverImpl(myQualifiedName.removeLastComponent()).fromModule(getModule()).firstResultOfType(PyFile.class);
    if (file == null) {
      return null;
    }
    checkValidForTests(file);
    for (final T element : PsiTreeUtil.getChildrenOfTypeAsList(file, aClass)) {
      checkValidForTests(element);
      if (memberName.equals(element.getName())) {
        return element;
      }
    }
    return null;
  }

  private static void checkValidForTests(@Nullable final PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      PyPsiUtils.assertValid(element);
    }
  }

  private static void checkAccess() {
    Preconditions.checkState(ApplicationManager.getApplication().isReadAccessAllowed(), "This method requires read access");
  }
}
