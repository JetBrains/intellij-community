// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.PydevConsoleRunnerUtil;
import com.jetbrains.python.console.PydevDocumentationProvider;
import com.jetbrains.python.console.completion.PydevConsoleReference;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.documentation.PyRuntimeDocstringFormatter;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

public class PythonRuntimeServiceImpl extends PythonRuntimeService {
  @Override
  public boolean isInPydevConsole(@NotNull PsiElement file) {
    return PydevConsoleRunnerUtil.isInPydevConsole(file);
  }

  @Override
  public boolean isInScratchFile(@NotNull PsiElement element) {
    return ScratchUtil.isScratch(PsiUtilCore.getVirtualFile(element));
  }

  @Nullable
  @Override
  public Sdk getConsoleSdk(@NotNull PsiElement foothold) {
    return PydevConsoleRunnerUtil.getConsoleSdk(foothold);
  }

  @Override
  public String createPydevDoc(PsiElement element, PsiElement originalElement) {
    return PydevDocumentationProvider.createDoc(element, originalElement);
  }

  @NotNull
  @Override
  public LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  @Override
  public PsiPolyVariantReference getPydevConsoleReference(@NotNull PyReferenceExpression element,
                                                          @NotNull PyResolveContext context) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      final ConsoleCommunication communication = file.getCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY);
      if (communication != null) {
        PyExpression qualifier = element.getQualifier();
        final String prefix = qualifier == null ? "" : qualifier.getText() + ".";
        return new PydevConsoleReference(element, communication, prefix, context.allowRemote());
      }
    }
    return null;
  }

  @Override
  public PythonConsoleData getPythonConsoleData(@Nullable ASTNode node) {
    return PydevConsoleRunnerUtil.getPythonConsoleData(node);
  }

  @Override
  public String formatDocstring(@NotNull Module module,
                                @NotNull DocStringFormat format,
                                @NotNull String input,
                                @NotNull List<String> formatterFlags) {
    return PyRuntimeDocstringFormatter.runExternalTool(module, format, input, formatterFlags);
  }

  @Override
  public String mapToRemote(@NotNull String localRoot, @NotNull Sdk sdk) {
    final RemoteSdkAdditionalData remoteSdkData = as(sdk.getSdkAdditionalData(), RemoteSdkAdditionalData.class);
    if (remoteSdkData != null) {
      return remoteSdkData.getPathMappings().convertToRemote(localRoot);
    }
    return localRoot;
  }
}
