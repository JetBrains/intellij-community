// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.run.docutils;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.rest.RestFileType;
import com.jetbrains.rest.run.RestRunConfiguration;
import com.jetbrains.rest.run.RestRunConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : catherine
 */
public class DocutilsConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  public DocutilsConfigurationProducer() {
    super(RestRunConfigurationType.getInstance().DOCUTILS_FACTORY);
  }

  @Override
  public PsiElement getSourceElement() {
    return restoreSourceElement();
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    PsiFile script = location.getPsiElement().getContainingFile();
    if (script == null || script.getFileType() != RestFileType.INSTANCE) {
      return null;
    }
    Module module = ModuleUtil.findModuleForPsiElement(script);
    storeSourceElement(script);

    final Project project = script.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    DocutilsRunConfiguration configuration = (DocutilsRunConfiguration) settings.getConfiguration();
    final VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) return null;
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

    if (configuration.getTask().isEmpty())
      configuration.setTask("rst2html");
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    configuration.setName(configuration.suggestedName());
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
    PsiFile script = location.getPsiElement().getContainingFile();
    if (script == null) {
      return null;
    }
    final VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) {
      return null;
    }
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
