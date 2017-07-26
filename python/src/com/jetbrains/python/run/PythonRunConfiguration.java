/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
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
  public static final String MIXED_DEBUG_MODE = "MIXED_DEBUG_MODE";
  public static final String DEBUGGABLE_EXTERNAL_LIBS = "DEBUGGABLE_EXTERNAL_LIBS";

  private String myScriptName;
  private String myScriptParameters;
  private boolean myShowCommandLineAfterwards = false;
  private boolean myEmulateTerminal = false;
  private boolean myMixedDebugMode = false;
  private String myDebuggableExternalLibs;

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
      throw new RuntimeConfigurationException(PyBundle.message("runcfg.unittest.no_script_name"));
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

  @Override
  public boolean mixedDebugMode() {
    return myMixedDebugMode;
  }

  @Override
  public void setMixedDebugMode(boolean mixedDebugMode) {
    myMixedDebugMode = mixedDebugMode;
  }

  @Override
  public String getDebuggableExternalLibs() {
    return myDebuggableExternalLibs;
  }

  @Override
  public void setDebuggableExternalLibs(String debuggableExternalLibs) {
    myDebuggableExternalLibs = debuggableExternalLibs;
  }

  public void readExternal(Element element) {
    super.readExternal(element);
    myScriptName = JDOMExternalizerUtil.readField(element, SCRIPT_NAME);
    myScriptParameters = JDOMExternalizerUtil.readField(element, PARAMETERS);
    myShowCommandLineAfterwards = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, SHOW_COMMAND_LINE, "false"));
    myEmulateTerminal = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, EMULATE_TERMINAL, "false"));
    myMixedDebugMode = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, MIXED_DEBUG_MODE, "false"));
    myDebuggableExternalLibs = JDOMExternalizerUtil.readField(element, DEBUGGABLE_EXTERNAL_LIBS);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, SCRIPT_NAME, myScriptName);
    JDOMExternalizerUtil.writeField(element, PARAMETERS, myScriptParameters);
    JDOMExternalizerUtil.writeField(element, SHOW_COMMAND_LINE, Boolean.toString(myShowCommandLineAfterwards));
    JDOMExternalizerUtil.writeField(element, EMULATE_TERMINAL, Boolean.toString(myEmulateTerminal));
    JDOMExternalizerUtil.writeField(element, MIXED_DEBUG_MODE, Boolean.toString(myMixedDebugMode));
    JDOMExternalizerUtil.writeField(element, DEBUGGABLE_EXTERNAL_LIBS, myDebuggableExternalLibs);
  }

  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  public static void copyParams(PythonRunConfigurationParams source, PythonRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setScriptParameters(source.getScriptParameters());
    target.setShowCommandLineAfterwards(source.showCommandLineAfterwards());
    target.setEmulateTerminal(source.emulateTerminal());
    target.setMixedDebugMode(source.mixedDebugMode());
    target.setDebuggableExternalLibs(source.getDebuggableExternalLibs());
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
}
