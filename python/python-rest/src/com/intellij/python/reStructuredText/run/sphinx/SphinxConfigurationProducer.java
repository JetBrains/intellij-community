// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run.sphinx;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.python.reStructuredText.run.RestRunConfiguration;
import com.intellij.python.reStructuredText.run.RestRunConfigurationType;
import com.intellij.python.reStructuredText.RestFile;
import org.jetbrains.annotations.NotNull;

public final class SphinxConfigurationProducer extends LazyRunConfigurationProducer<RestRunConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return RestRunConfigurationType.getInstance().SPHINX_FACTORY;
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull RestRunConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    assert (configuration instanceof SphinxRunConfiguration);
    PsiElement element = sourceElement.get();
    if (!(element instanceof PsiDirectory directory)) return false;

    boolean hasRstFile = false;
    boolean hasConf = false;
    for (PsiFile file : directory.getFiles()) {
      if ("conf.py".equals(file.getName())) {
        hasConf = true;
      }
      if (file instanceof RestFile) {
        hasRstFile = true;
      }
    }
    if (!hasRstFile || !hasConf) return false;
    final VirtualFile vFile = directory.getVirtualFile();
    configuration.setInputFile(vFile.getPath());

    configuration.setName(((PsiDirectory)element).getName());
    if (configuration.getTask().isEmpty()) {
      configuration.setTask("html");
    }
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    configuration.setName(configuration.suggestedName());
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull RestRunConfiguration configuration, @NotNull ConfigurationContext context) {
    PsiElement element = context.getPsiLocation();
    if (!(element instanceof PsiDirectory)) return false;
    final VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
    String path = vFile.getPath();
    final String scriptName = configuration.getInputFile();
    return FileUtil.toSystemIndependentName(scriptName).equals(FileUtil.toSystemIndependentName(path));
  }
}
