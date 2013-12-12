/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Consumer;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyImportReference;
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
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class GenerateBinaryStubsFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#" + GenerateBinaryStubsFix.class.getName());

  private String myQualifiedName;
  private Sdk mySdk;

  public GenerateBinaryStubsFix(PsiReference reference) {
    myQualifiedName = getReferenceText(reference);
    mySdk = getPythonSdk(reference);
  }

  private static String getReferenceText(PsiReference reference) {
    PsiElement element = reference.getElement();
    while (element.getParent() instanceof PyReferenceExpression) {
      element = element.getParent();
    }
    final String elementText = element.getText();

    final PyFromImportStatement importStatementBase = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
    if (importStatementBase != null) {
      final QualifiedName qName = importStatementBase.getImportSourceQName();
      if (qName != null) {
        return qName.append(elementText).toString();
      }
    }

    return elementText;
  }

  @NotNull
  public String getName() {
    return "Generate stubs for binary module " + myQualifiedName;
  }

  @NotNull
  public String getFamilyName() {
    return "Generate binary stubs";
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final Task.Backgroundable backgroundable = new Task.Backgroundable(project, "Generating skeletons for binary module", false) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        List<String> assemblyRefs = collectAssemblyReferences(descriptor.getPsiElement().getContainingFile());

        try {
          final PySkeletonRefresher refresher = new PySkeletonRefresher(project, null, mySdk, null, null);

          if (needBinaryList(myQualifiedName)) {
            if (!generateSkeletonsForList(refresher, indicator)) return;
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
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(backgroundable, new BackgroundableProcessIndicator(backgroundable));
  }

  private boolean generateSkeletonsForList(@NotNull final PySkeletonRefresher refresher, ProgressIndicator indicator) throws InvalidSdkException {
    final PySkeletonGenerator generator = new PySkeletonGenerator(refresher.getSkeletonsPath());
    indicator.setIndeterminate(false);
    final String homePath = mySdk.getHomePath();
    if (homePath == null) return false;
    final ProcessOutput runResult = PySdkUtil.getProcessOutput(
      new File(homePath).getParent(),
      new String[]{
        homePath,
        PythonHelpersLocator.getHelperPath("extra_syspath.py"), myQualifiedName},
      PythonSdkType.getVirtualEnvAdditionalEnv(homePath), 5000
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
          //noinspection unchecked
          refresher.generateSkeleton(name, "", new ArrayList<String>(), Consumer.EMPTY_CONSUMER);
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
    final List<String> result = new ArrayList<String>();
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        super.visitPyCallExpression(node);
        if (node.isCalleeText("AddReference", "AddReferenceByPartialName")) {
          final PyExpression[] args = node.getArguments();
          if (args.length == 1 && args [0] instanceof PyStringLiteralExpression) {
            result.add(((PyStringLiteralExpression) args [0]).getStringValue());
          }
        }
      }
    });
    return result;
  }

  public static boolean isApplicable(@NotNull final PsiReference ref) {
    if (!(ref instanceof PyImportReference)) {
      return false;
    }
    final Sdk sdk = getPythonSdk(ref);
    if (sdk == null) {
      return false;
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
    if (flavor instanceof IronPythonSdkFlavor) {
      return getReferenceText(ref).matches("[A-Z][A-Za-z0-9]+(\\.[A-Z][A-Za-z0-9]+)*");
    }
    return isGtk(ref);
  }

  private static boolean isGtk(@NotNull final PsiReference ref) {
    final PyFromImportStatement importStatementBase = PsiTreeUtil.getParentOfType(ref.getElement(), PyFromImportStatement.class);
    if (importStatementBase != null) {
      final QualifiedName qName = importStatementBase.getImportSourceQName();
      if (qName != null && qName.matches("gi", "repository"))
        return true;
    }
    return false;
  }

  @Nullable
  private static Sdk getPythonSdk(@NotNull final PsiReference ref) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(ref.getElement());
    return module == null ? null : PythonSdkType.findPythonSdk(module);
  }
}
