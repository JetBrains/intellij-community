// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.skeletons.PySkeletonGenerator;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public final class GenerateBinaryStubsFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(GenerateBinaryStubsFix.class);

  private final String myQualifiedName;
  private final Sdk mySdk;

  /**
   * Generates pack of fixes available for some unresolved import statement.
   * Be sure to call {@link #isApplicable(PyImportStatementBase)} first to make sure this statement is supported
   *
   * @param importStatementBase statement to fix
   * @return pack of fixes
   */
  public static @NotNull List<LocalQuickFix> generateFixes(final @NotNull PyImportStatementBase importStatementBase) {
    final List<String> names = importStatementBase.getFullyQualifiedObjectNames();
    final List<LocalQuickFix> result = new ArrayList<>(names.size());
    if (importStatementBase instanceof PyFromImportStatement && names.isEmpty()) {
      final QualifiedName qName = ((PyFromImportStatement)importStatementBase).getImportSourceQName();
      if (qName != null) {
        result.add(new GenerateBinaryStubsFix(importStatementBase, qName.toString()));
      }
    }
    for (final String qualifiedName : names) {
      result.add(new GenerateBinaryStubsFix(importStatementBase, qualifiedName));
    }
    return result;
  }

  /**
   * @param importStatementBase statement to fix
   * @param qualifiedName       name should be fixed (one of {@link PyImportStatementBase#getFullyQualifiedObjectNames()})
   */
  private GenerateBinaryStubsFix(final @NotNull PyImportStatementBase importStatementBase, final @NotNull String qualifiedName) {
    myQualifiedName = qualifiedName;
    mySdk = getPythonSdk(importStatementBase);
  }

  @Override
  public @NotNull String getName() {
    return PyBundle.message("sdk.gen.stubs.for.binary.modules", myQualifiedName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyBundle.message("QFIX.generate.binary.stubs");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    final PsiFile file = descriptor.getPsiElement().getContainingFile();
    final Backgroundable backgroundable = getFixTask(file);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(backgroundable, new BackgroundableProcessIndicator(backgroundable));
  }


  /**
   * Returns fix task that is used to generate stubs
   *
   * @param fileToRunTaskIn file where task should run
   * @return task itself
   */
  public @NotNull Backgroundable getFixTask(final @NotNull PsiFile fileToRunTaskIn) {
    final Project project = fileToRunTaskIn.getProject();
    final String folder = fileToRunTaskIn.getContainingDirectory().getVirtualFile().getCanonicalPath();
    return new Task.Backgroundable(project, PyBundle.message("QFIX.generating.skeletons.for.binary.module"), false) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        try {
          final PySkeletonRefresher refresher = new PySkeletonRefresher(project, null, mySdk, null, null, folder);

          if (isFromGiRepository(myQualifiedName)) {
            if (!generateSkeletonsForGiRepository(refresher, indicator, folder)) return;
          }
          else {
            refresher.getGenerator()
              .commandBuilder()
              .targetModule(myQualifiedName, null)
              .runGeneration(indicator);
          }
          final VirtualFile skeletonDir;
          skeletonDir = LocalFileSystem.getInstance().findFileByPath(refresher.getSkeletonsPath());
          if (skeletonDir != null) {
            skeletonDir.refresh(true, true);
          }
        }
        catch (InvalidSdkException | ExecutionException e) {
          LOG.error(e);
        }
      }
    };
  }

  private boolean generateSkeletonsForGiRepository(@NotNull PySkeletonRefresher refresher,
                                                   @NotNull ProgressIndicator indicator,
                                                   @Nullable String currentBinaryFilesPath) throws InvalidSdkException, ExecutionException {
    final String homePath = mySdk.getHomePath();
    if (homePath == null) return false;
    GeneralCommandLine cmd = PythonHelper.EXTRA_SYSPATH.newCommandLine(homePath, Lists.newArrayList(myQualifiedName));
    final ProcessOutput runResult = PySdkUtil.getProcessOutput(cmd,
                                                               new File(homePath).getParent(),
                                                               PySdkUtil.activateVirtualEnv(mySdk), 5000
    );
    if (runResult.checkSuccess(LOG)) {
      final PySkeletonGenerator.Builder builder = refresher.getGenerator()
        .commandBuilder()
        .extraSysPath(StringUtil.split(runResult.getStdout(), File.pathSeparator))
        .extraArgs("--name-pattern", "gi.repository.*");

      if (currentBinaryFilesPath != null) {
        builder.workingDir(currentBinaryFilesPath);
      }

      builder.runGeneration(indicator);
    }
    return true;
  }

  private static boolean isFromGiRepository(final @NotNull String qualifiedName) {
    return qualifiedName.startsWith("gi.repository");
  }

  /**
   * Checks if this fix can help you to generate binary stubs
   *
   * @param importStatementBase statement to fix
   * @return true if this fix could work
   */
  public static boolean isApplicable(final @NotNull PyImportStatementBase importStatementBase) {
    if (importStatementBase.getFullyQualifiedObjectNames().isEmpty() &&
        !(importStatementBase instanceof PyFromImportStatement && ((PyFromImportStatement)importStatementBase).isStarImport())) {
      return false;
    }

    return isGtk(importStatementBase);
  }

  private static boolean isGtk(final @NotNull PyImportStatementBase importStatementBase) {
    if (importStatementBase instanceof PyFromImportStatement) {
      final QualifiedName qName = ((PyFromImportStatement)importStatementBase).getImportSourceQName();
      if (qName != null && qName.matches("gi", "repository")) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable Sdk getPythonSdk(final @NotNull PsiElement element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return (module == null) ? null : PythonSdkUtil.findPythonSdk(module);
  }
}
