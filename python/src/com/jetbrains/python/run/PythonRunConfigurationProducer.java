// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
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
public final class PythonRunConfigurationProducer extends LazyRunConfigurationProducer<PythonRunConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return PythonConfigurationType.getInstance().getFactory();
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
    if (parent != null && StringUtil.isEmpty(configuration.getWorkingDirectory())) {
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
      for (RunnableScriptFilter f : RunnableScriptFilter.EP_NAME.getExtensionList()) {
        // Configuration producers always called by user
        if (f.isRunnableScript(script, module, location, TypeEvalContext.userInitiated(location.getProject(), null))) {
          return false;
        }
      }
    }
    return true;
  }

  // Only prefer over other regular config
  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return other.isProducedBy(PythonRunConfigurationProducer.class);
  }
}
