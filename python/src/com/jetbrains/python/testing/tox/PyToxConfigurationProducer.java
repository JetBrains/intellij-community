/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.testing.tox;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author Ilya.Kazakevich
 */
public final class PyToxConfigurationProducer extends RunConfigurationProducer<PyToxConfiguration> {
  private static final String TOX_FILE_NAME = "tox.ini";

  public PyToxConfigurationProducer() {
    super(PyToxConfigurationFactory.INSTANCE);
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
