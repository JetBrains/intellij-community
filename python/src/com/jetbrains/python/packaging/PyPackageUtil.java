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
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.packaging.setupPy.SetupTaskIntrospector;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.remote.PyCredentialsContribution;
import com.jetbrains.python.sdk.CredentialsTypeExChecker;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author vlan
 */
public class PyPackageUtil {
  public static final String SETUPTOOLS = "setuptools";
  public static final String PIP = "pip";
  public static final String DISTRIBUTE = "distribute";
  private static final Logger LOG = Logger.getInstance(PyPackageUtil.class);

  @NotNull
  private static final String REQUIRES = "requires";

  @NotNull
  private static final String INSTALL_REQUIRES = "install_requires";

  @NotNull
  private static final String[] SETUP_PY_REQUIRES_KWARGS_NAMES = new String[]{
    REQUIRES, INSTALL_REQUIRES, "setup_requires", "tests_require"
  };

  @NotNull
  private static final String DEPENDENCY_LINKS = "dependency_links";

  private PyPackageUtil() {
  }

  public static boolean hasSetupPy(@NotNull Module module) {
    return findSetupPy(module) != null;
  }

  @Nullable
  public static PyFile findSetupPy(@NotNull Module module) {
    for (VirtualFile root : PyUtil.getSourceRoots(module)) {
      final VirtualFile child = root.findChild("setup.py");
      if (child != null) {
        final PsiFile file = PsiManager.getInstance(module.getProject()).findFile(child);
        if (file instanceof PyFile) {
          return (PyFile)file;
        }
      }
    }
    return null;
  }

  public static boolean hasRequirementsTxt(@NotNull Module module) {
    return findRequirementsTxt(module) != null;
  }

  @Nullable
  public static VirtualFile findRequirementsTxt(@NotNull Module module) {
    final String requirementsPath = PyPackageRequirementsSettings.getInstance(module).getRequirementsPath();
    if (!requirementsPath.isEmpty()) {
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(requirementsPath);
      if (file != null) {
        return file;
      }
      final ModuleRootManager manager = ModuleRootManager.getInstance(module);
      for (VirtualFile root : manager.getContentRoots()) {
        final VirtualFile fileInRoot = root.findFileByRelativePath(requirementsPath);
        if (fileInRoot != null) {
          return fileInRoot;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyListLiteralExpression findSetupPyInstallRequires(@NotNull Module module, @Nullable PyCallExpression setupCall) {
    if (setupCall == null) {
      return null;
    }

    return Stream
      .of(REQUIRES, INSTALL_REQUIRES)
      .map(setupCall::getKeywordArgument)
      .map(requires -> resolveRequiresValue(module, requires))
      .filter(requires -> requires != null)
      .findFirst()
      .orElse(null);
  }

  @Nullable
  public static List<PyRequirement> findSetupPyRequires(@NotNull Module module) {
    final PyCallExpression setupCall = findSetupCall(module);

    if (setupCall == null) {
      return null;
    }

    final List<PyRequirement> requirementsFromRequires = getSetupPyRequiresFromArguments(module, setupCall, SETUP_PY_REQUIRES_KWARGS_NAMES);
    final List<PyRequirement> requirementsFromLinks = getSetupPyRequiresFromArguments(module, setupCall, DEPENDENCY_LINKS);

    return mergeSetupPyRequirements(requirementsFromRequires, requirementsFromLinks);
  }

  @NotNull
  private static List<PyRequirement> getSetupPyRequiresFromArguments(@NotNull Module module,
                                                                     @NotNull PyCallExpression setupCall,
                                                                     @NotNull String... argumentNames) {
    return PyRequirement.fromText(
      Stream
        .of(argumentNames)
        .map(setupCall::getKeywordArgument)
        .map(requires -> resolveRequiresValue(module, requires))
        .filter(requires -> requires != null)
        .flatMap(requires -> Stream.of(requires.getElements()))
        .filter(PyStringLiteralExpression.class::isInstance)
        .map(requirement -> ((PyStringLiteralExpression)requirement).getStringValue())
        .collect(Collectors.joining("\n"))
    );
  }

  @NotNull
  private static List<PyRequirement> mergeSetupPyRequirements(@NotNull List<PyRequirement> requirementsFromRequires,
                                                              @NotNull List<PyRequirement> requirementsFromLinks) {
    if (!requirementsFromLinks.isEmpty()) {
      final Map<String, List<PyRequirement>> nameToRequirements =
        requirementsFromRequires.stream().collect(Collectors.groupingBy(PyRequirement::getName, LinkedHashMap::new, Collectors.toList()));

      for (PyRequirement requirementFromLinks : requirementsFromLinks) {
        nameToRequirements.replace(requirementFromLinks.getName(), Collections.singletonList(requirementFromLinks));
      }

      return nameToRequirements.values().stream().flatMap(Collection::stream).collect(Collectors.toCollection(ArrayList::new));
    }

    return requirementsFromRequires;
  }

  @Nullable
  private static PyListLiteralExpression resolveRequiresValue(@NotNull Module module, @Nullable PyExpression requires) {
    if (requires instanceof PyListLiteralExpression) {
      return (PyListLiteralExpression)requires;
    }
    if (requires instanceof PyReferenceExpression) {
      final TypeEvalContext context = TypeEvalContext.deepCodeInsight(module.getProject());
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
      final QualifiedResolveResult result = ((PyReferenceExpression)requires).followAssignmentsChain(resolveContext);
      final PsiElement element = result.getElement();
      if (element instanceof PyListLiteralExpression) {
        return (PyListLiteralExpression)element;
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getPackageNames(@NotNull Module module) {
    // TODO: Cache found module packages, clear cache on module updates
    final List<String> packageNames = new ArrayList<>();
    final Project project = module.getProject();
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (roots.length == 0) {
      roots = ModuleRootManager.getInstance(module).getContentRoots();
    }
    for (VirtualFile root : roots) {
      collectPackageNames(project, root, packageNames);
    }
    return packageNames;
  }

  @NotNull
  public static String requirementsToString(@NotNull List<PyRequirement> requirements) {
    return StringUtil.join(requirements, requirement -> String.format("'%s'", requirement.toString()), ", ");
  }

  @Nullable
  public static PyCallExpression findSetupCall(@NotNull PyFile file) {
    final Ref<PyCallExpression> result = new Ref<>(null);
    file.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        final PyExpression callee = node.getCallee();
        final String name = PyUtil.getReadableRepr(callee, true);
        if ("setup".equals(name)) {
          result.set(node);
        }
      }

      @Override
      public void visitPyElement(PyElement node) {
        if (!(node instanceof ScopeOwner)) {
          super.visitPyElement(node);
        }
      }
    });
    return result.get();
  }

  @Nullable
  public static PyCallExpression findSetupCall(@NotNull Module module) {
    return Optional
      .ofNullable(findSetupPy(module))
      .map(PyPackageUtil::findSetupCall)
      .orElse(null);
  }

  private static void collectPackageNames(@NotNull final Project project,
                                          @NotNull final VirtualFile root,
                                          @NotNull final List<String> results) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!fileIndex.isExcluded(file) && file.isDirectory() && file.findChild(PyNames.INIT_DOT_PY) != null) {
          results.add(VfsUtilCore.getRelativePath(file, root, '.'));
        }
        return true;
      }
    });
  }

  public static boolean packageManagementEnabled(@Nullable Sdk sdk) {
    if (!PythonSdkType.isRemote(sdk)) {
      return true;
    }
    return new CredentialsTypeExChecker() {
      @Override
      protected boolean checkLanguageContribution(PyCredentialsContribution languageContribution) {
        return languageContribution.isPackageManagementEnabled();
      }
    }.withSshContribution(true).withVagrantContribution(true).withWebDeploymentContribution(true).check(sdk);
  }

  /**
   * Refresh the list of installed packages inside the specified SDK if it hasn't been updated yet
   * displaying modal progress bar in the process, return cached packages otherwise.
   * <p>
   * Note that <strong>you shall never call this method from a write action</strong>, since such modal
   * tasks are executed directly on EDT and network operations on the dispatch thread are prohibited
   * (see the implementation of ApplicationImpl#runProcessWithProgressSynchronously() for details).
   */
  @Nullable
  public static List<PyPackage> refreshAndGetPackagesModally(@NotNull Sdk sdk) {

    final Application app = ApplicationManager.getApplication();
    assert !(app.isWriteAccessAllowed()) :
      "This method can't be called on WriteAction because " +
      "refreshAndGetPackages would be called on AWT thread in this case (see runProcessWithProgressSynchronously) " +
      "and may lead to freeze";


    final Ref<List<PyPackage>> packagesRef = Ref.create();
    @SuppressWarnings("ThrowableInstanceNeverThrown") final Throwable callStacktrace = new Throwable();
    LOG.debug("Showing modal progress for collecting installed packages", new Throwable());
    PyUtil.runWithProgress(null, PyBundle.message("sdk.scanning.installed.packages"), true, false, indicator -> {
      indicator.setIndeterminate(true);
      try {
        final PyPackageManager manager = PyPackageManager.getInstance(sdk);
        packagesRef.set(manager.refreshAndGetPackages(false));
      }
      catch (ExecutionException e) {
        if (LOG.isDebugEnabled()) {
          e.initCause(callStacktrace);
          LOG.debug(e);
        }
        else {
          LOG.warn(e.getMessage());
        }
      }
    });
    return packagesRef.get();
  }

  /**
   * Run unconditional update of the list of packages installed in SDK. Normally only one such of updates should run at time.
   * This behavior in enforced by the parameter isUpdating.
   *
   * @param manager    package manager for SDK
   * @param isUpdating flag indicating whether another refresh is already running
   * @return whether packages were refreshed successfully, e.g. this update wasn't cancelled because of another refresh in progress
   */
  public static boolean updatePackagesSynchronouslyWithGuard(@NotNull PyPackageManager manager, @NotNull AtomicBoolean isUpdating) {
    assert !ApplicationManager.getApplication().isDispatchThread();
    if (!isUpdating.compareAndSet(false, true)) {
      return false;
    }
    try {
      if (manager instanceof PyPackageManagerImpl) {
        LOG.info("Refreshing installed packages for SDK " + ((PyPackageManagerImpl)manager).getSdk().getHomePath());
      }
      manager.refreshAndGetPackages(true);
    }
    catch (ExecutionException ignored) {
    }
    finally {
      isUpdating.set(false);
    }
    return true;
  }


  @Nullable
  public static PyPackage findPackage(@NotNull List<PyPackage> packages, @NotNull String name) {
    for (PyPackage pkg : packages) {
      if (name.equalsIgnoreCase(pkg.getName())) {
        return pkg;
      }
    }
    return null;
  }

  public static boolean hasManagement(@NotNull List<PyPackage> packages) {
    return (findPackage(packages, SETUPTOOLS) != null || findPackage(packages, DISTRIBUTE) != null) ||
           findPackage(packages, PIP) != null;
  }

  @Nullable
  public static List<PyRequirement> getRequirementsFromTxt(@NotNull Module module) {
    final VirtualFile requirementsTxt = findRequirementsTxt(module);
    if (requirementsTxt != null) {
      return PyRequirement.fromFile(requirementsTxt);
    }
    return null;
  }

  public static void addRequirementToTxtOrSetupPy(@NotNull Module module,
                                                  @NotNull String requirementName,
                                                  @NotNull LanguageLevel languageLevel) {
    final VirtualFile requirementsTxt = findRequirementsTxt(module);
    if (requirementsTxt != null && requirementsTxt.isWritable()) {
      final Document document = FileDocumentManager.getInstance().getDocument(requirementsTxt);
      if (document != null) {
        document.insertString(0, requirementName + "\n");
      }
      return;
    }

    final PyFile setupPy = findSetupPy(module);
    if (setupPy == null) {
      return;
    }

    final PyCallExpression setupCall = findSetupCall(setupPy);
    final PyListLiteralExpression installRequires = findSetupPyInstallRequires(module, setupCall);
    final PyElementGenerator generator = PyElementGenerator.getInstance(module.getProject());

    if (installRequires != null && installRequires.isWritable()) {
      final String text = String.format("'%s'", requirementName);
      final PyExpression generated = generator.createExpressionFromText(languageLevel, text);
      installRequires.add(generated);

      return;
    }

    if (setupCall != null) {
      final PyArgumentList argumentList = setupCall.getArgumentList();
      final PyKeywordArgument requiresArg = generateRequiresKwarg(setupPy, requirementName, languageLevel, generator);

      if (argumentList != null && requiresArg != null) {
        argumentList.addArgument(requiresArg);
      }
    }
  }

  @Nullable
  private static PyKeywordArgument generateRequiresKwarg(@NotNull PyFile setupPy,
                                                         @NotNull String requirementName,
                                                         @NotNull LanguageLevel languageLevel,
                                                         @NotNull PyElementGenerator generator) {
    final String keyword = SetupTaskIntrospector.usesSetuptools(setupPy) ? INSTALL_REQUIRES : REQUIRES;
    final String text = String.format("foo(%s=['%s'])", keyword, requirementName);
    final PyExpression generated = generator.createExpressionFromText(languageLevel, text);

    if (generated instanceof PyCallExpression) {
      final PyCallExpression callExpression = (PyCallExpression)generated;

      return Stream
        .of(callExpression.getArguments())
        .filter(PyKeywordArgument.class::isInstance)
        .map(PyKeywordArgument.class::cast)
        .filter(kwarg -> keyword.equals(kwarg.getKeyword()))
        .findFirst()
        .orElse(null);
    }

    return null;
  }
}
