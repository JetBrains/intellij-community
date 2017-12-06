/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.jetbrains.python.PyBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonRunConfiguration extends AbstractPythonRunConfiguration
  implements AbstractPythonRunConfigurationParams, PythonRunConfigurationParams, RefactoringListenerProvider {
  public static final String SCRIPT_NAME = "SCRIPT_NAME";
  public static final String PARAMETERS = "PARAMETERS";
  public static final String MULTIPROCESS = "MULTIPROCESS";
  public static final String SHOW_COMMAND_LINE = "SHOW_COMMAND_LINE";
  public static final String EMULATE_TERMINAL = "EMULATE_TERMINAL";
  public static final String MODULE_MODE = "MODULE_MODE";

  private String myScriptName;
  private String myScriptParameters;
  private boolean myShowCommandLineAfterwards = false;
  private boolean myEmulateTerminal = false;
  private boolean myModuleMode = false;
  private final Pattern myQualifiedNameRegex = Pattern.compile("^[a-zA-Z0-9._]+[a-zA-Z0-9_]$");

  protected PythonRunConfiguration(Project project, ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
    setUnbufferedEnv();
  }

  @Override
  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    return new PythonRunConfigurationEditor(this);
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonScriptCommandLineState(this, env);
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myScriptName)) {
      throw new RuntimeConfigurationException(
        PyBundle.message(isModuleMode() ? "runcfg.unittest.no_module_name" : "runcfg.unittest.no_script_name"));
    }
    else {
      if (isModuleMode() && !myQualifiedNameRegex.matcher(myScriptName).matches()) {
        throw new RuntimeConfigurationWarning("Provide a qualified name of a module");
      }
    }
  }

  public String suggestedName() {
    final String scriptName = getScriptName();
    if (scriptName == null) return null;
    String name = new File(scriptName).getName();
    if (name.endsWith(".py")) {
      return name.substring(0, name.length() - 3);
    }
    return name;
  }

  public String getScriptName() {
    return myScriptName;
  }

  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  public String getScriptParameters() {
    return myScriptParameters;
  }

  public void setScriptParameters(String scriptParameters) {
    myScriptParameters = scriptParameters;
  }

  public boolean showCommandLineAfterwards() {
    return myShowCommandLineAfterwards;
  }

  public void setShowCommandLineAfterwards(boolean showCommandLineAfterwards) {
    myShowCommandLineAfterwards = showCommandLineAfterwards;
  }

  @Override
  public boolean emulateTerminal() {
    return myEmulateTerminal;
  }

  @Override
  public void setEmulateTerminal(boolean emulateTerminal) {
    myEmulateTerminal = emulateTerminal;
  }

  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    myScriptName = JDOMExternalizerUtil.readField(element, SCRIPT_NAME);
    myScriptParameters = JDOMExternalizerUtil.readField(element, PARAMETERS);
    myShowCommandLineAfterwards = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, SHOW_COMMAND_LINE, "false"));
    myEmulateTerminal = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, EMULATE_TERMINAL, "false"));
    myModuleMode = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, MODULE_MODE, "false"));
  }

  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, SCRIPT_NAME, myScriptName);
    JDOMExternalizerUtil.writeField(element, PARAMETERS, myScriptParameters);
    JDOMExternalizerUtil.writeField(element, SHOW_COMMAND_LINE, Boolean.toString(myShowCommandLineAfterwards));
    JDOMExternalizerUtil.writeField(element, EMULATE_TERMINAL, Boolean.toString(myEmulateTerminal));
    JDOMExternalizerUtil.writeField(element, MODULE_MODE, Boolean.toString(myModuleMode));
  }

  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  public static void copyParams(PythonRunConfigurationParams source, PythonRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setModuleMode(source.isModuleMode());
    target.setScriptName(source.getScriptName());
    target.setScriptParameters(source.getScriptParameters());
    target.setShowCommandLineAfterwards(source.showCommandLineAfterwards());
    target.setEmulateTerminal(source.emulateTerminal());
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null && Comparing.equal(new File(virtualFile.getPath()).getAbsolutePath(),
                                                 new File(myScriptName).getAbsolutePath())) {
        return new RefactoringElementAdapter() {
          @Override
          public void elementRenamedOrMoved(@NotNull PsiElement newElement) {
            VirtualFile virtualFile = ((PsiFile)newElement).getVirtualFile();
            if (virtualFile != null) {
              updateScriptName(virtualFile.getPath());
            }
          }

          @Override
          public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
            updateScriptName(oldQualifiedName);
          }

          private void updateScriptName(String path) {
            myScriptName = FileUtil.toSystemDependentName(path);
          }
        };
      }
    }
    return null;
  }

  @Override
  public boolean isModuleMode() {
    return myModuleMode;
  }

  @Override
  public void setModuleMode(boolean moduleMode) {
    myModuleMode = moduleMode;
  }
}
