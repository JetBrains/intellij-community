// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveResult;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.remote.PyCredentialsContribution;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.sdk.CredentialsTypeExChecker;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor;
import com.jetbrains.python.target.PyTargetAwareAdditionalData;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
@ApiStatus.Internal

public final class PyPackageUtil {
  public static final String SETUPTOOLS = "setuptools";
  public static final String PIP = "pip";
  public static final String DISTRIBUTE = "distribute";
  private static final Logger LOG = Logger.getInstance(PyPackageUtil.class);

  private static class InterpreterChangeEvents {
    private static final Logger LOG = Logger.getInstance(InterpreterChangeEvents.class);
  }

  private static final @NotNull String REQUIRES = "requires";

  private static final @NotNull String INSTALL_REQUIRES = "install_requires";

  private static final String @NotNull [] SETUP_PY_REQUIRES_KWARGS_NAMES = new String[]{
    REQUIRES, INSTALL_REQUIRES, "setup_requires", "tests_require"
  };

  private static final @NotNull String DEPENDENCY_LINKS = "dependency_links";

  private PyPackageUtil() {
  }

  public static boolean hasSetupPy(@NotNull Module module) {
    return findSetupPy(module) != null;
  }

  public static @Nullable PyFile findSetupPy(@NotNull Module module) {
    for (VirtualFile root : PyUtil.getSourceRoots(module)) {
      final VirtualFile child = root.findChild("setup.py");
      if (child != null) {
        final PsiFile file = ReadAction.compute(() -> PsiManager.getInstance(module.getProject()).findFile(child));
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

  public static @Nullable VirtualFile findRequirementsTxt(@NotNull Module module) {
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

  private static @Nullable PsiElement findSetupPyInstallRequires(@Nullable PyCallExpression setupCall) {
    if (setupCall == null) return null;

    return StreamEx
      .of(REQUIRES, INSTALL_REQUIRES)
      .map(setupCall::getKeywordArgument)
      .map(PyPackageUtil::resolveValue)
      .findFirst(Objects::nonNull)
      .orElse(null);
  }

  public static @Nullable List<PyRequirement> findSetupPyRequires(@NotNull Module module) {
    final PyCallExpression setupCall = findSetupCall(module);
    if (setupCall == null) return null;

    final List<PyRequirement> requirementsFromRequires = getSetupPyRequiresFromArguments(setupCall, SETUP_PY_REQUIRES_KWARGS_NAMES);
    final List<PyRequirement> requirementsFromLinks = getSetupPyRequiresFromArguments(setupCall, DEPENDENCY_LINKS);

    return mergeSetupPyRequirements(requirementsFromRequires, requirementsFromLinks);
  }

  public static @Nullable Map<String, List<PyRequirement>> findSetupPyExtrasRequire(@NotNull Module module) {
    final PyCallExpression setupCall = findSetupCall(module);
    if (setupCall == null) return null;

    final PyDictLiteralExpression extrasRequire =
      PyUtil.as(resolveValue(setupCall.getKeywordArgument("extras_require")), PyDictLiteralExpression.class);
    if (extrasRequire == null) return null;

    final Map<String, List<PyRequirement>> result = new HashMap<>();

    for (PyKeyValueExpression extraRequires : extrasRequire.getElements()) {
      final Pair<String, List<PyRequirement>> extraResult = getExtraRequires(extraRequires.getKey(), extraRequires.getValue());
      if (extraResult != null) {
        result.put(extraResult.first, extraResult.second);
      }
    }

    return result;
  }

  private static @Nullable Pair<String, List<PyRequirement>> getExtraRequires(@NotNull PyExpression extra, @Nullable PyExpression requires) {
    if (extra instanceof PyStringLiteralExpression) {
      final List<String> requiresValue = resolveRequiresValue(requires);

      if (requiresValue != null) {
        return Pair.createNonNull(((PyStringLiteralExpression)extra).getStringValue(),
                                  PyRequirementParser.fromText(StringUtil.join(requiresValue, "\n")));
      }
    }

    return null;
  }

  private static @NotNull List<PyRequirement> getSetupPyRequiresFromArguments(@NotNull PyCallExpression setupCall,
                                                                              String @NotNull ... argumentNames) {
    return PyRequirementParser.fromText(
      StreamEx
        .of(argumentNames)
        .map(setupCall::getKeywordArgument)
        .flatCollection(PyPackageUtil::resolveRequiresValue)
        .joining("\n")
    );
  }

  private static @NotNull List<PyRequirement> mergeSetupPyRequirements(@NotNull List<PyRequirement> requirementsFromRequires,
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

  /**
   * @param expression expression to resolve
   * @return {@code expression} if it is not a reference or element that is found by following assignments chain.
   * <em>Note: if result is {@link PyExpression} then parentheses around will be flattened.</em>
   */
  private static @Nullable PsiElement resolveValue(@Nullable PyExpression expression) {
    final PsiElement elementToAnalyze = PyPsiUtils.flattenParens(expression);

    if (elementToAnalyze instanceof PyReferenceExpression) {
      final TypeEvalContext context = TypeEvalContext.deepCodeInsight(elementToAnalyze.getProject());
      final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

      return StreamEx
        .of(((PyReferenceExpression)elementToAnalyze).multiFollowAssignmentsChain(resolveContext))
        .map(ResolveResult::getElement)
        .findFirst(Objects::nonNull)
        .map(e -> e instanceof PyExpression ? PyPsiUtils.flattenParens((PyExpression)e) : e)
        .orElse(null);
    }

    return elementToAnalyze;
  }

  private static @Nullable List<String> resolveRequiresValue(@Nullable PyExpression expression) {
    final PsiElement elementToAnalyze = resolveValue(expression);

    if (elementToAnalyze instanceof PyStringLiteralExpression) {
      return Collections.singletonList(((PyStringLiteralExpression)elementToAnalyze).getStringValue());
    }
    else if (elementToAnalyze instanceof PyListLiteralExpression || elementToAnalyze instanceof PyTupleExpression) {
      return StreamEx
        .of(((PySequenceExpression)elementToAnalyze).getElements())
        .map(PyPackageUtil::resolveValue)
        .select(PyStringLiteralExpression.class)
        .map(PyStringLiteralExpression::getStringValue)
        .toList();
    }

    return null;
  }

  public static @NotNull List<String> getPackageNames(@NotNull Module module) {
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

  public static @NotNull String requirementsToString(@NotNull List<? extends PyRequirement> requirements) {
    return StringUtil.join(requirements, requirement -> String.format("'%s'", requirement.getPresentableText()), ", ");
  }

  private static @Nullable PyCallExpression findSetupCall(@NotNull PyFile file) {
    final Ref<PyCallExpression> result = new Ref<>(null);
    file.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(@NotNull PyCallExpression node) {
        final PyExpression callee = node.getCallee();
        final String name = PyUtil.getReadableRepr(callee, true);
        if ("setup".equals(name)) {
          result.set(node);
        }
      }

      @Override
      public void visitPyElement(@NotNull PyElement node) {
        if (!(node instanceof ScopeOwner)) {
          super.visitPyElement(node);
        }
      }
    });
    return result.get();
  }

  public static @Nullable PyCallExpression findSetupCall(@NotNull Module module) {
    return Optional
      .ofNullable(findSetupPy(module))
      .map(PyPackageUtil::findSetupCall)
      .orElse(null);
  }

  private static void collectPackageNames(final @NotNull Project project,
                                          final @NotNull VirtualFile root,
                                          final @NotNull List<String> results) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.equals(root)) {
          return true;
        }
        if (!fileIndex.isExcluded(file) && file.isDirectory() && file.findChild(PyNames.INIT_DOT_PY) != null) {
          results.add(VfsUtilCore.getRelativePath(file, root, '.'));
          return true;
        }
        return false;
      }
    });
  }

  /**
   * @param newUi                set only for new toolwindow
   * @param calledFromInspection when so, we can't change anything, and if sdk lacks of additional data we do not add it.
   *                             See {@link PySdkExtKt#getOrCreateAdditionalData(Sdk)}
   */
  public static boolean packageManagementEnabled(@Nullable Sdk sdk, boolean newUi, boolean calledFromInspection) {
    if (sdk == null) {
      return false;
    }
    // Temporary fix because old UI doesn't support non-local conda
    var data = calledFromInspection ? (ObjectUtils.tryCast(sdk.getSdkAdditionalData(), PythonSdkAdditionalData.class)) :  PySdkExtKt.getOrCreateAdditionalData(sdk);
    if (!newUi
        && data != null
        && data.getFlavor() instanceof CondaEnvSdkFlavor
        && PySdkExtKt.getTargetEnvConfiguration(sdk) != null) {
      LOG.warn("Remote Conda package manager is disabled");
      return false;
    }
    Boolean supported = PythonInterpreterTargetEnvironmentFactory.isPackageManagementSupported(sdk);
    if (supported != null) {
      return supported;
    }
    if (!PythonSdkUtil.isRemote(sdk)) {
      return true;
    }
    return new CredentialsTypeExChecker() {
      @Override
      protected boolean checkLanguageContribution(PyCredentialsContribution languageContribution) {
        return languageContribution.isPackageManagementEnabled();
      }
    }.check(sdk);
  }

  /**
   * Refresh the list of installed packages inside the specified SDK if it hasn't been updated yet
   * displaying modal progress bar in the process, return cached packages otherwise.
   * <p>
   * Note that <strong>you shall never call this method from a write action</strong>, since such modal
   * tasks are executed directly on EDT and network operations on the dispatch thread are prohibited
   * (see the implementation of ApplicationImpl#runProcessWithProgressSynchronously() for details).
   */
  public static @NotNull List<PyPackage> refreshAndGetPackagesModally(@NotNull Sdk sdk) {

    final Application app = ApplicationManager.getApplication();
    assert !(app.isWriteAccessAllowed()) :
      "This method can't be called on WriteAction because " +
      "refreshAndGetPackages would be called on AWT thread in this case (see runProcessWithProgressSynchronously) " +
      "and may lead to freeze";


    final Ref<List<PyPackage>> packagesRef = Ref.create();
    final Throwable callStacktrace = new Throwable();
    LOG.debug("Showing modal progress for collecting installed packages", new Throwable());
    PyUtil.runWithProgress(null, PyBundle.message("sdk.scanning.installed.packages"), true, false, indicator -> {
      if (PythonSdkUtil.isDisposed(sdk)) {
        packagesRef.set(Collections.emptyList());
        return;
      }

      indicator.setIndeterminate(true);
      try {
        final PyPackageManager manager = PyPackageManager.getInstance(sdk);
        packagesRef.set(manager.refreshAndGetPackages(false));
      }
      catch (ExecutionException e) {
        packagesRef.set(Collections.emptyList());
        e.initCause(callStacktrace);
        LOG.warn(e);
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
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (!isUpdating.compareAndSet(false, true)) {
      return false;
    }
    try {
      if (manager instanceof PyPackageManagerImpl) {
        LOG.info("Refreshing installed packages for SDK " + PyPackageManager.getSdk(manager).getHomePath());
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


  public static boolean hasManagement(@NotNull List<PyPackage> packages) {
    return (PyPsiPackageUtil.findPackage(packages, SETUPTOOLS) != null || PyPsiPackageUtil.findPackage(packages, DISTRIBUTE) != null) ||
           PyPsiPackageUtil.findPackage(packages, PIP) != null;
  }

  public static @Nullable List<PyRequirement> getRequirementsFromTxt(@NotNull Module module) {
    final VirtualFile requirementsTxt = findRequirementsTxt(module);
    if (requirementsTxt != null) {
      return PyRequirementParser.fromFile(requirementsTxt);
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
    if (setupPy == null) return;

    final PyCallExpression setupCall = findSetupCall(setupPy);
    if (setupCall == null) return;

    final PsiElement installRequires = findSetupPyInstallRequires(setupCall);
    if (installRequires != null) {
      addRequirementToInstallRequires(installRequires, requirementName, languageLevel);
    }
    else {
      final PyArgumentList argumentList = setupCall.getArgumentList();
      final PyKeywordArgument requiresArg = generateRequiresKwarg(setupPy, requirementName, languageLevel);

      if (argumentList != null && requiresArg != null) {
        argumentList.addArgument(requiresArg);
      }
    }
  }

  private static void addRequirementToInstallRequires(@NotNull PsiElement installRequires,
                                                      @NotNull String requirementName,
                                                      @NotNull LanguageLevel languageLevel) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(installRequires.getProject());
    final PyExpression newRequirement = generator.createExpressionFromText(languageLevel, "'" + requirementName + "'");

    if (installRequires instanceof PyListLiteralExpression) {
      installRequires.add(newRequirement);
    }
    else if (installRequires instanceof PyTupleExpression) {
      final String newInstallRequiresText = StreamEx
        .of(((PyTupleExpression)installRequires).getElements())
        .append(newRequirement)
        .map(PyExpression::getText)
        .joining(",", "(", ")");

      final PyExpression expression = generator.createExpressionFromText(languageLevel, newInstallRequiresText);

      Optional
        .ofNullable(PyUtil.as(expression, PyParenthesizedExpression.class))
        .map(PyParenthesizedExpression::getContainedExpression)
        .map(e -> PyUtil.as(e, PyTupleExpression.class))
        .ifPresent(e -> installRequires.replace(e));
    }
    else if (installRequires instanceof PyStringLiteralExpression) {
      final PyListLiteralExpression newInstallRequires = generator.createListLiteral();

      newInstallRequires.add(installRequires);
      newInstallRequires.add(newRequirement);

      installRequires.replace(newInstallRequires);
    }
  }

  private static @Nullable PyKeywordArgument generateRequiresKwarg(@NotNull PyFile setupPy,
                                                                   @NotNull String requirementName,
                                                                   @NotNull LanguageLevel languageLevel) {
    final String keyword = PyPsiUtils.containsImport(setupPy, "setuptools") ? INSTALL_REQUIRES : REQUIRES;
    final String text = String.format("foo(%s=['%s'])", keyword, requirementName);
    final PyExpression generated = PyElementGenerator.getInstance(setupPy.getProject()).createExpressionFromText(languageLevel, text);

    if (generated instanceof PyCallExpression callExpression) {

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

  /**
   * Execute the given executable on a pooled thread whenever there is a VFS event happening under some of the roots of the SDK.
   *
   * @param sdk              SDK those roots need to be watched. It must be disposed not later than "parentDisposable"
   * @param parentDisposable disposable for the registered event listeners. It must not outlive sdk
   * @param runnable         executable that's going to be executed
   */
  public static void runOnChangeUnderInterpreterPaths(@NotNull Sdk sdk,
                                                      @NotNull Disposable parentDisposable,
                                                      @NotNull Runnable runnable) {
    final Application app = ApplicationManager.getApplication();
    VirtualFileManager.getInstance().addAsyncFileListener(new AsyncFileListener() {
      @Override
      public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (sdk instanceof Disposable && Disposer.isDisposed((Disposable)sdk)) {
          throw new AlreadyDisposedException("SDK " + sdk + " (" + sdk.getClass() + ") is already disposed");
        }
        final Set<VirtualFile> roots = getPackagingAwareSdkRoots(sdk);
        if (roots.isEmpty()) return null;
        allEvents:
        for (VFileEvent event : events) {
          if (event instanceof VFileContentChangeEvent || event instanceof VFilePropertyChangeEvent) continue;
          // In case of create event getFile() returns null as the file hasn't been created yet
          VirtualFile parent = null;
          if (event instanceof VFileCreateEvent) {
            parent = ((VFileCreateEvent)event).getParent();
          }
          else {
            VirtualFile file = event.getFile();
            if (file != null) parent = file.getParent();
          }

          if (parent != null && roots.contains(parent)) {
            InterpreterChangeEvents.LOG.debug("Interpreter change in " + parent + " indicated by " + event +
                                              " (all events: " + events + ")");
            app.executeOnPooledThread(runnable);
            break allEvents;
          }
        }
        // No continuation in write action is needed
        return null;
      }
    }, parentDisposable);
  }

  private static @NotNull Set<VirtualFile> getPackagingAwareSdkRoots(@NotNull Sdk sdk) {
    final Set<VirtualFile> result = Sets.newHashSet(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
    var targetAdditionalData = PySdkExtKt.getTargetAdditionalData(sdk);
    if (targetAdditionalData != null) {
      // For targets that support VFS we are interested not only in local dirs, but also for VFS on target
      // When user changes something on WSL FS for example, we still need to trigger path updates
      for (var remoteSourceToVfs : getRemoteSourceToVfsMapping(targetAdditionalData).entrySet()) {
        if (result.contains(remoteSourceToVfs.getKey())) {
          result.add(remoteSourceToVfs.getValue());
        }
      }
    }
    final String skeletonsPath = PythonSdkUtil.getSkeletonsPath(PathManager.getSystemPath(), sdk.getHomePath());
    final VirtualFile skeletonsRoot = LocalFileSystem.getInstance().findFileByPath(skeletonsPath);
    result.removeIf(vf -> vf.equals(skeletonsRoot) ||
                          vf.equals(PyUserSkeletonsUtil.getUserSkeletonsDirectory()) ||
                          PyTypeShed.INSTANCE.isInside(vf));
    return result;
  }

  /**
   * If target provides access to its FS using VFS, rerun all mappings in format [path-to-"remote_sources" -> vfs-on-target]
   * i.e: "c:\remote_sources -> \\wsl$\..."
   */
  private static @NotNull Map<@NotNull VirtualFile, @NotNull VirtualFile> getRemoteSourceToVfsMapping(@NotNull PyTargetAwareAdditionalData additionalData) {
    var configuration = additionalData.getTargetEnvironmentConfiguration();
    if (configuration == null) return Collections.emptyMap();
    var vfsMapper = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(configuration);
    if (vfsMapper == null) return Collections.emptyMap();
    var vfs = LocalFileSystem.getInstance();
    var result = new HashMap<@NotNull VirtualFile, @NotNull VirtualFile>();
    for (var remoteSourceAndVfs : ContainerUtil.map(additionalData.getPathMappings().getPathMappings(),
                                                    m -> Pair.create(
                                                      vfs.findFileByPath(m.getLocalRoot()),
                                                      vfsMapper.getVfsFromTargetPath(m.getRemoteRoot())))) {
      var remoteSourceDir = remoteSourceAndVfs.first;
      var vfsDir = remoteSourceAndVfs.second;
      if (remoteSourceDir != null && vfsDir != null) {
        result.put(remoteSourceDir, vfsDir);
      }
    }
    return result;
  }
}
