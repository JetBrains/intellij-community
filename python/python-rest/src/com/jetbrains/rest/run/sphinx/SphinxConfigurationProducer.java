// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.run.sphinx;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.run.RestRunConfiguration;
import com.jetbrains.rest.run.RestRunConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : catherine
 */
public class SphinxConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  public SphinxConfigurationProducer() {
    super(RestRunConfigurationType.getInstance().SPHINX_FACTORY);
  }

  @Override
  public PsiElement getSourceElement() {
    return restoreSourceElement();
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiDirectory)) return null;

    storeSourceElement(element);
    PsiDirectory directory = (PsiDirectory)element;
    boolean hasRstFile = false;
    boolean hasConf = false;
    for (PsiFile file : directory.getFiles()) {
      if ("conf.py".equals(file.getName()))
        hasConf = true;
      if (file instanceof RestFile) {
        hasRstFile = true;
      }
    }
    if (!hasRstFile || !hasConf) return null;
    final Project project = directory.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    SphinxRunConfiguration configuration = (SphinxRunConfiguration) settings.getConfiguration();
    final VirtualFile vFile = directory.getVirtualFile();
    configuration.setInputFile(vFile.getPath());

    configuration.setName(((PsiDirectory)element).getName());
    if (configuration.getTask().isEmpty())
      configuration.setTask("html");
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    configuration.setName(configuration.suggestedName());
    Module module = ModuleUtil.findModuleForPsiElement(element);
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    return settings;
  }

  @Nullable
  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull List<RunnerAndConfigurationSettings> existingConfigurations,
                                                                 ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiDirectory)) return null;
    final VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
    String path = vFile.getPath();
    for (RunnerAndConfigurationSettings configuration : existingConfigurations) {
      final String scriptName = ((RestRunConfiguration)configuration.getConfiguration()).getInputFile();
      if (FileUtil.toSystemIndependentName(scriptName).equals(FileUtil.toSystemIndependentName(path))) {
        return configuration;
      }
    }
    return null;
  }

  @Override
  public int compareTo(final Object o) {
    return PREFERED;
  }
}
