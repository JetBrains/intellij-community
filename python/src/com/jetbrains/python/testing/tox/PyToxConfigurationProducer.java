// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.tox;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
public final class PyToxConfigurationProducer extends LazyRunConfigurationProducer<PyToxConfiguration> {
  private static final String TOX_FILE_NAME = "tox.ini";

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return PyToxConfigurationFactory.INSTANCE;
  }

  @Override
  public boolean isConfigurationFromContext(final PyToxConfiguration configuration, final ConfigurationContext context) {
    final Location<?> location = context.getLocation();
    if (location == null) {
      return false;
    }
    final VirtualFile virtualFile = location.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    final String currentPath = virtualFile.getParent().getCanonicalPath();
    final String directoryPath = configuration.getWorkingDirectory();

    if (currentPath != null && directoryPath != null && ! directoryPath.equals(currentPath)) {
      return false; // Tox but for different dir
    }


    return TOX_FILE_NAME.equals(virtualFile.getName());
  }

  @Override
  protected boolean setupConfigurationFromContext(final PyToxConfiguration configuration,
                                                  final ConfigurationContext context,
                                                  final Ref<PsiElement> sourceElement) {
    final PsiFile file = sourceElement.get().getContainingFile();
    if (file == null) {
      return false;
    }

    final String envName = PyToxTestLocator.getEnvNameFromElement(file);
    if (envName != null) {
      configuration.setRunOnlyEnvs(envName);
      configuration.setName(String.format("Tox: run on %s", envName));
      return true;
    }


    final PsiDirectory directory = file.getContainingDirectory();
    if (directory == null) {
      return false;
    }
    configuration.setWorkingDirectory(directory.getVirtualFile().getCanonicalPath());
    if (TOX_FILE_NAME.equals(file.getName())) {
      configuration.setName("Tox");
      return true;
    }
    return false;
  }
}
