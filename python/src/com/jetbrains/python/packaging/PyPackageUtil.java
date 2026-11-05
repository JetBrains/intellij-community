// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiManager;
import com.intellij.python.requirements.parser.PyRequirementParser;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.configuration.PyModuleInterpreterReader;
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils;
import com.jetbrains.python.packaging.requirementsTxt.RequirementsTxtManipulationHelper;
import com.jetbrains.python.packaging.setupPy.SetupPyHelpers;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.remote.PyCredentialsContribution;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.sdk.CredentialsTypeExChecker;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.SdkExtKt;
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.packaging.setupPy.SetupPyHelpers.SETUP_PY;

@ApiStatus.Internal
public final class PyPackageUtil {
  public static final String SETUPTOOLS = PyPackageNames.SETUPTOOLS;
  public static final String PIP = PyPackageNames.PIP;
  public static final String DISTRIBUTE = PyPackageNames.DISTRIBUTE;
  private static final Logger LOG = Logger.getInstance(PyPackageUtil.class);

  private static class InterpreterChangeEvents {
    private static final Logger LOG = Logger.getInstance(InterpreterChangeEvents.class);
  }

  private PyPackageUtil() {
  }

  @RequiresReadLock
  public static boolean hasSetupPy(@NotNull Module module) {
    return findSetupPy(module) != null;
  }

  @ApiStatus.Internal
  public static @Nullable VirtualFile findSetupPyFile(@NotNull Module module) {
    var contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile root : contentRoots) {
      VirtualFile setupPy = VfsUtil.findRelativeFile(root, SETUP_PY);
      if (setupPy != null) {
        return setupPy;
      }
    }
    return null;
  }

  @RequiresReadLock
  public static @Nullable PyFile findSetupPy(@NotNull Module module) {
    Sdk sdk = PyModuleInterpreterReader.findModuleSdkIfReady(module);
    if (sdk == null) {
      return SetupPyHelpers.detectSetupPyInModule(module);
    }
    else {
      return findSetupPyPsiFileForSdk(module);
    }
  }

  public static boolean hasRequirementsTxt(@NotNull Module module) {
    return findRequirementsTxt(module) != null;
  }

  @SuppressWarnings("unused")
  public static @Nullable VirtualFile findRequirementsTxt(@NotNull Module module) {
    Sdk sdk = PyModuleInterpreterReader.findModuleSdkIfReady(module);
    if (sdk != null) {
      VirtualFile stored = PythonRequirementTxtSdkUtils.resolvePersistedRequirementsFile(sdk);
      if (stored != null) {
        return stored;
      }
    }
    return PythonRequirementTxtSdkUtils.detectRequirementsTxtInModule(module);
  }

  @RequiresReadLock(generateAssertion = false)
  public static @Nullable List<PyRequirement> getRequirementsFromTxt(@NotNull Module module) {
    VirtualFile requirementsFile = findRequirementsTxt(module);
    if (requirementsFile == null) return null;
    List<PyRequirement> requirements = ReadAction.compute(() -> PyRequirementParser.fromFile(requirementsFile));
    return requirements;
  }

  @RequiresReadLock
  public static @Nullable List<PyRequirement> findSetupPyRequires(@NotNull Module module) {
    PyFile pyFile = findSetupPyPsiFileForSdk(module);
    if (pyFile == null) return null;
    return SetupPyHelpers.parseSetupPy(pyFile);
  }

  @RequiresReadLock
  public static @Nullable Map<String, List<PyRequirement>> findSetupPyExtrasRequire(@NotNull Module module) {
    PyFile pyFile = findSetupPyPsiFileForSdk(module);
    if (pyFile == null) return null;
    return SetupPyHelpers.findSetupPyExtrasRequire(pyFile);
  }

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  private static @Nullable PyFile findSetupPyPsiFileForSdk(@NotNull Module module) {
    Sdk sdk = PyModuleInterpreterReader.findModuleSdkIfReady(module);
    if (sdk == null) return null;

    VirtualFile setupPyVFile = findSetupPyFile(module);
    if (setupPyVFile == null) return null;

    Project project = module.getProject();
    return ReadAction.compute(() -> {
      if (!setupPyVFile.isValid()) return null;
      var psiFile = PsiManager.getInstance(project).findFile(setupPyVFile);
      return (psiFile instanceof PyFile pyFile) ? pyFile : null;
    });
  }

  @RequiresReadLock
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


  @RequiresReadLock
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
   *                             See {@link SdkExtKt#getOrCreateAdditionalData(Sdk)}
   */
  public static boolean packageManagementEnabled(@Nullable Sdk sdk, boolean newUi, boolean calledFromInspection) {
    if (sdk == null) {
      return false;
    }
    // Temporary fix because old UI doesn't support non-local conda
    var data = calledFromInspection
               ? (ObjectUtils.tryCast(sdk.getSdkAdditionalData(), PythonSdkAdditionalData.class))
               : PySdkExtKt.getOrCreateAdditionalData(sdk);
    if (!calledFromInspection
        && !newUi
        && data.getFlavor() instanceof CondaEnvSdkFlavor
        && SdkExtKt.getTargetEnvConfiguration(sdk) != null) {
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

  @RequiresBackgroundThread(generateAssertion = false)
  public static void addRequirementToTxtOrSetupPy(@NotNull Module module,
                                                  @NotNull String requirementName,
                                                  @NotNull LanguageLevel languageLevel) {
    VirtualFile requirementsFile = findRequirementsTxt(module);
    if (requirementsFile != null) {
      RequirementsTxtManipulationHelper.addToRequirementsTxt(
        module.getProject(), requirementsFile, requirementName);
      return;
    }

    PyFile setupPyFile = ReadAction.compute(() -> findSetupPy(module));
    if (setupPyFile != null) {
      SetupPyHelpers.addRequirementsToSetupPy(setupPyFile, requirementName, languageLevel);
    }
  }
}
