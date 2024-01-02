// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run.sphinx;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.python.reStructuredText.run.RestConfigurationEditor;
import com.intellij.python.reStructuredText.run.RestRunConfiguration;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.intellij.python.reStructuredText.PythonRestBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User : catherine
 */
public class SphinxRunConfiguration extends RestRunConfiguration {
  public SphinxRunConfiguration(final Project project,
                                final ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    final SphinxTasksModel model = new SphinxTasksModel();
    if (!model.contains("pdf") && getSdk() != null) {
      final List<PyPackage> packages = PyPackageManager.getInstance(getSdk()).getPackages();
      if (packages != null) {
        final PyPackage rst2pdf = PyPsiPackageUtil.findPackage(packages, "rst2pdf");
        if (rst2pdf != null) {
          model.add(13, "pdf");
        }
      }
    }

    RestConfigurationEditor editor = new RestConfigurationEditor(getProject(), this, model);
    editor.setConfigurationName("Sphinx task");
    editor.setOpenInBrowserVisible(false);
    editor.setInputDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor());
    editor.setOutputDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor());
    return editor;
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return new SphinxCommandLineState(this, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    if (StringUtil.isEmptyOrSpaces(getInputFile()))
      throw new RuntimeConfigurationError(PythonRestBundle.message("python.rest.specify.input.directory.name"));
    if (StringUtil.isEmptyOrSpaces(getOutputFile()))
      throw new RuntimeConfigurationError(PythonRestBundle.message("python.rest.specify.output.directory.name"));
  }

  @Override
  public String suggestedName() {
    return PythonRestBundle.message("python.rest.sphinx.run.cfg.default.name", getName());
  }
}
