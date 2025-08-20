// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.run.ShRunConfiguration;
import com.intellij.sh.run.ShConfigurationType;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.sh.backend.run.ShRunFileAction.parseInterpreterAndOptions;

final class ShRunConfigurationProducer extends LazyRunConfigurationProducer<ShRunConfiguration> {
  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return ShConfigurationType.getInstance();
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull ShRunConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    PsiFile psiFile = sourceElement.get().getContainingFile();
    if (!(psiFile instanceof ShFile)) return false;
    FileViewProvider viewProvider = psiFile.getViewProvider();
    if (viewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) return false;
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null || virtualFile instanceof LightVirtualFile) return false;

    String defaultShell = ShConfigurationType.getDefaultShell(psiFile.getProject());
    String shebang = ShShebangParserUtil.getShebangExecutable((ShFile)psiFile);
    if (shebang != null) {
      Pair<String, String> result = parseInterpreterAndOptions(shebang);
      configuration.setInterpreterPath(result.first);
      configuration.setInterpreterOptions(result.second);
    } else {
      configuration.setInterpreterPath(defaultShell);
    }
    configuration.setScriptWorkingDirectory(virtualFile.getParent().getPath());
    configuration.setName(virtualFile.getPresentableName());
    configuration.setScriptPath(virtualFile.getPath());
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull ShRunConfiguration configuration, @NotNull ConfigurationContext context) {
    PsiElement psiLocation = context.getPsiLocation();
    if (psiLocation == null) return false;
    PsiFile psiFile = psiLocation.getContainingFile();
    if (!(psiFile instanceof ShFile)) return false;
    FileViewProvider viewProvider = psiFile.getViewProvider();
    if (viewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) return false;
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;

    String scriptPath = configuration.getScriptPath();
    String workingDirectory = configuration.getScriptWorkingDirectory();
    return scriptPath != null && scriptPath.equals(virtualFile.getPath()) &&
           workingDirectory != null && workingDirectory.equals(virtualFile.getParent().getPath());
  }
}
