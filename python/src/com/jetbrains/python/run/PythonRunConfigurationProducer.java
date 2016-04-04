/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class PythonRunConfigurationProducer extends RunConfigurationProducer<PythonRunConfiguration> {

  public PythonRunConfigurationProducer() {
    super(PythonConfigurationType.getInstance().getFactory());
  }

  @Override
  protected boolean setupConfigurationFromContext(PythonRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {

    final Location location = context.getLocation();
    if (location == null) return false;
    final PsiFile script = location.getPsiElement().getContainingFile();
    if (!isAvailable(location, script)) return false;

    final VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) return false;
    configuration.setScriptName(vFile.getPath());
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(script);
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    configuration.setName(configuration.suggestedName());
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(PythonRunConfiguration configuration, ConfigurationContext context) {
    final Location location = context.getLocation();
    if (location == null) return false;
    final PsiFile script = location.getPsiElement().getContainingFile();
    if (!isAvailable(location, script)) return false;
    final VirtualFile virtualFile = script.getVirtualFile();
    if (virtualFile == null) return false;
    if (virtualFile instanceof LightVirtualFile) return false;
    final String workingDirectory = configuration.getWorkingDirectory();
    final String scriptName = configuration.getScriptName();
    final String path = virtualFile.getPath();
    return scriptName.equals(path) || path.equals(new File(workingDirectory, scriptName).getAbsolutePath());
  }

  private static boolean isAvailable(@NotNull final Location location, @Nullable final PsiFile script) {
    if (script == null || script.getFileType() != PythonFileType.INSTANCE) {
      return false;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(script);
    if (module != null) {
      for (RunnableScriptFilter f : Extensions.getExtensions(RunnableScriptFilter.EP_NAME)) {
        // Configuration producers always called by user
        if (f.isRunnableScript(script, module, location, TypeEvalContext.userInitiated(location.getProject(), null))) {
          return false;
        }
      }
    }
    return true;
  }
  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return other.isProducedBy(PythonRunConfigurationProducer.class);
  }
}
