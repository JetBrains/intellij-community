// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run.docutils;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.python.reStructuredText.run.RestRunConfiguration;
import com.intellij.python.reStructuredText.run.RestRunConfigurationType;
import com.intellij.python.reStructuredText.RestFileType;
import org.jetbrains.annotations.NotNull;

public final class DocutilsConfigurationProducer extends LazyRunConfigurationProducer<RestRunConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return RestRunConfigurationType.getInstance().DOCUTILS_FACTORY;
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull RestRunConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    assert (configuration instanceof DocutilsRunConfiguration);
    PsiFile script = sourceElement.get().getContainingFile();
    if (script == null || script.getFileType() != RestFileType.INSTANCE) {
      return false;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(script);
    final VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) return false;
    configuration.setInputFile(vFile.getPath());
    configuration.setName(script.getName());

    String outputPath = vFile.getPath();
    int index = outputPath.lastIndexOf('.');
    if (index > 0) outputPath = outputPath.substring(0, index);
    outputPath += ".html";
    VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
    if (outputFile == null) {
      configuration.setOutputFile(outputPath);
      configuration.setOpenInBrowser(true);
    }

    if (configuration.getTask().isEmpty()) {
      configuration.setTask("rst2html");
    }
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    configuration.setName(configuration.suggestedName());
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull RestRunConfiguration configuration, @NotNull ConfigurationContext context) {
    final PsiElement element = context.getPsiLocation();
    if (element == null) {
      return false;
    }
    PsiFile script = element.getContainingFile();
    if (script == null) {
      return false;
    }
    final VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) {
      return false;
    }
    String path = vFile.getPath();
    final String scriptName = configuration.getInputFile();
    return FileUtil.toSystemIndependentName(scriptName).equals(FileUtil.toSystemIndependentName(path));
  }
}
