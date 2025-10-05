// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils;
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementsTxtManager;
import com.jetbrains.python.packaging.setupPy.SetupPyHelpers;
import com.jetbrains.python.packaging.setupPy.SetupPyManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.remote.PyCredentialsContribution;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.sdk.CredentialsTypeExChecker;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor;
import com.jetbrains.python.target.PyTargetAwareAdditionalData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public final class PyPackageUtil {
  public static final String SETUPTOOLS = "setuptools";
  public static final String PIP = "pip";
  public static final String DISTRIBUTE = "distribute";
  private static final Logger LOG = Logger.getInstance(PyPackageUtil.class);

  private static class InterpreterChangeEvents {
    private static final Logger LOG = Logger.getInstance(InterpreterChangeEvents.class);
  }

  private PyPackageUtil() {
  }

  public static boolean hasSetupPy(@NotNull Module module) {
    return findSetupPy(module) != null;
  }

  @ApiStatus.Internal
  public static @Nullable VirtualFile findSetupPyFile(@NotNull Module module) {
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) return null;
    return SetupPyManager.getInstance(module.getProject(), sdk).getDependenciesFile();
  }

  public static @Nullable PyFile findSetupPy(@NotNull Module module) {
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) {
      return SetupPyHelpers.detectSetupPyInModule(module);
    }
    else {
      return SetupPyManager.getInstance(module.getProject(), sdk).getRequirementsPsiFile();
    }
  }

  public static boolean hasRequirementsTxt(@NotNull Module module) {
    return findRequirementsTxt(module) != null;
  }

  @SuppressWarnings("unused")
  public static @Nullable VirtualFile findRequirementsTxt(@NotNull Module module) {
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) {
      return PythonRequirementTxtSdkUtils.detectRequirementsTxtInModule(module);
    }
    else {
      return PythonRequirementsTxtManager.getInstance(module.getProject(), sdk).getDependenciesFile();
    }
  }

  @RequiresReadLock(generateAssertion = false)
  public static @Nullable List<PyRequirement> getRequirementsFromTxt(@NotNull Module module) {
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) return null;

    return PythonRequirementsTxtManager.getInstance(module.getProject(), sdk).getDependencies();
  }


  public static @Nullable List<PyRequirement> findSetupPyRequires(@NotNull Module module) {
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) return null;
    return SetupPyManager.getInstance(module.getProject(), sdk).getDependencies();
  }

  public static @Nullable Map<String, List<PyRequirement>> findSetupPyExtrasRequire(@NotNull Module module) {
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) return null;
    PyFile pyFile = SetupPyManager.getInstance(module.getProject(), sdk).getRequirementsPsiFile();
    if (pyFile == null) return null;
    return SetupPyHelpers.findSetupPyExtrasRequire(pyFile);
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


  public static @Nullable PyCallExpression findSetupCall(@NotNull Module module) {
    PyFile pyFile = findSetupPy(module);
    if (pyFile == null) {
      return null;
    }
    return SetupPyHelpers.findSetupCall(pyFile);
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
    var data = calledFromInspection
               ? (ObjectUtils.tryCast(sdk.getSdkAdditionalData(), PythonSdkAdditionalData.class))
               : PySdkExtKt.getOrCreateAdditionalData(sdk);
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


  public static void addRequirementToTxtOrSetupPy(@NotNull Module module,
                                                  @NotNull String requirementName,
                                                  @NotNull LanguageLevel languageLevel) {
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) return;
    boolean isSuccess = PythonRequirementsTxtManager.getInstance(module.getProject(), sdk).addDependency(requirementName);
    if (isSuccess) {
      return;
    }
    SetupPyManager.getInstance(module.getProject(), sdk).addDependency(requirementName);
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
    result.removeIf(vf -> vf.equals(skeletonsRoot) || PyTypeShed.INSTANCE.isInside(vf));
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
