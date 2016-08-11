/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Consumer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.IronPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.skeletons.PySkeletonGenerator;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class GenerateBinaryStubsFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#" + GenerateBinaryStubsFix.class.getName());

  private final String myQualifiedName;
  private final Sdk mySdk;

  /**
   * Generates pack of fixes available for some unresolved import statement.
   * Be sure to call {@link #isApplicable(com.jetbrains.python.psi.PyImportStatementBase)} first to make sure this statement is supported
   *
   * @param importStatementBase statement to fix
   * @return pack of fixes
   */
  @NotNull
  public static Collection<GenerateBinaryStubsFix> generateFixes(@NotNull final PyImportStatementBase importStatementBase) {
    final List<String> names = importStatementBase.getFullyQualifiedObjectNames();
    final List<GenerateBinaryStubsFix> result = new ArrayList<>(names.size());
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
   * @param qualifiedName       name should be fixed (one of {@link com.jetbrains.python.psi.PyImportStatementBase#getFullyQualifiedObjectNames()})
   */
  private GenerateBinaryStubsFix(@NotNull final PyImportStatementBase importStatementBase, @NotNull final String qualifiedName) {
    myQualifiedName = qualifiedName;
    mySdk = getPythonSdk(importStatementBase);
  }

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("sdk.gen.stubs.for.binary.modules", myQualifiedName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Generate binary stubs";
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
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
  @NotNull
  public Backgroundable getFixTask(@NotNull final PsiFile fileToRunTaskIn) {
    final Project project = fileToRunTaskIn.getProject();
    final String folder = fileToRunTaskIn.getContainingDirectory().getVirtualFile().getCanonicalPath();
    return new Task.Backgroundable(project, "Generating skeletons for binary module", false) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);


        final List<String> assemblyRefs = new ReadAction<List<String>>() {
          @Override
          protected void run(@NotNull Result<List<String>> result) throws Throwable {
            result.setResult(collectAssemblyReferences(fileToRunTaskIn));
          }
        }.execute().getResultObject();


        try {
          final PySkeletonRefresher refresher = new PySkeletonRefresher(project, null, mySdk, null, null, folder);

          if (needBinaryList(myQualifiedName)) {
            if (!generateSkeletonsForList(refresher, indicator, folder)) return;
          }
          else {
            //noinspection unchecked
            refresher.generateSkeleton(myQualifiedName, "", assemblyRefs, Consumer.EMPTY_CONSUMER);
          }
          final VirtualFile skeletonDir;
          skeletonDir = LocalFileSystem.getInstance().findFileByPath(refresher.getSkeletonsPath());
          if (skeletonDir != null) {
            skeletonDir.refresh(true, true);
          }
        }
        catch (InvalidSdkException e) {
          LOG.error(e);
        }
      }
    };
  }

  private boolean generateSkeletonsForList(@NotNull final PySkeletonRefresher refresher,
                                           ProgressIndicator indicator,
                                           @Nullable final String currentBinaryFilesPath) throws InvalidSdkException {
    final PySkeletonGenerator generator = new PySkeletonGenerator(refresher.getSkeletonsPath(), mySdk, currentBinaryFilesPath);
    indicator.setIndeterminate(false);
    final String homePath = mySdk.getHomePath();
    if (homePath == null) return false;
    GeneralCommandLine cmd = PythonHelper.EXTRA_SYSPATH.newCommandLine(homePath, Lists.newArrayList(myQualifiedName));
    final ProcessOutput runResult = PySdkUtil.getProcessOutput(cmd,
                                                               new File(homePath).getParent(),
                                                               PythonSdkType.getVirtualEnvExtraEnv(homePath), 5000
    );
    if (runResult.getExitCode() == 0 && !runResult.isTimeout()) {
      final String extraPath = runResult.getStdout();
      final PySkeletonGenerator.ListBinariesResult binaries = generator.listBinaries(mySdk, extraPath);
      final List<String> names = Lists.newArrayList(binaries.modules.keySet());
      Collections.sort(names);
      final int size = names.size();
      for (int i = 0; i != size; ++i) {
        final String name = names.get(i);
        indicator.setFraction((double)i / size);
        if (needBinaryList(name)) {
          indicator.setText2(name);
          final PySkeletonRefresher.PyBinaryItem item = binaries.modules.get(name);
          final String modulePath = item != null ? item.getPath() : "";
          //noinspection unchecked
          refresher.generateSkeleton(name, modulePath, new ArrayList<>(), Consumer.EMPTY_CONSUMER);
        }
      }
    }
    return true;
  }

  private static boolean needBinaryList(@NotNull final String qualifiedName) {
    return qualifiedName.startsWith("gi.repository");
  }

  private List<String> collectAssemblyReferences(PsiFile file) {
    if (!(PythonSdkFlavor.getFlavor(mySdk) instanceof IronPythonSdkFlavor)) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<>();
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        super.visitPyCallExpression(node);
        // TODO: What if user loads it not by literal? We need to ask user for list of DLLs
        if (node.isCalleeText("AddReference", "AddReferenceByPartialName", "AddReferenceByName")) {
          final PyExpression[] args = node.getArguments();
          if (args.length == 1 && args[0] instanceof PyStringLiteralExpression) {
            result.add(((PyStringLiteralExpression)args[0]).getStringValue());
          }
        }
      }
    });
    return result;
  }

  /**
   * Checks if this fix can help you to generate binary stubs
   *
   * @param importStatementBase statement to fix
   * @return true if this fix could work
   */
  public static boolean isApplicable(@NotNull final PyImportStatementBase importStatementBase) {
    if (importStatementBase.getFullyQualifiedObjectNames().isEmpty() &&
        !(importStatementBase instanceof PyFromImportStatement && ((PyFromImportStatement)importStatementBase).isStarImport())) {
      return false;
    }
    final Sdk sdk = getPythonSdk(importStatementBase);
    if (sdk == null) {
      return false;
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
    if (flavor instanceof IronPythonSdkFlavor) {
      return true;
    }
    return isGtk(importStatementBase);
  }

  private static boolean isGtk(@NotNull final PyImportStatementBase importStatementBase) {
    if (importStatementBase instanceof PyFromImportStatement) {
      final QualifiedName qName = ((PyFromImportStatement)importStatementBase).getImportSourceQName();
      if (qName != null && qName.matches("gi", "repository")) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static Sdk getPythonSdk(@NotNull final PsiElement element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return (module == null) ? null : PythonSdkType.findPythonSdk(module);
  }
}
