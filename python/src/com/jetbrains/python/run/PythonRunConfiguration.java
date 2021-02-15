// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.InputRedirectAware;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonRunConfiguration extends AbstractPythonRunConfiguration
  implements AbstractPythonRunConfigurationParams, PythonRunConfigurationParams, RefactoringListenerProvider, InputRedirectAware {
  public static final String SCRIPT_NAME = "SCRIPT_NAME";
  public static final String PARAMETERS = "PARAMETERS";
  public static final String MULTIPROCESS = "MULTIPROCESS";
  public static final String SHOW_COMMAND_LINE = "SHOW_COMMAND_LINE";
  public static final String EMULATE_TERMINAL = "EMULATE_TERMINAL";
  public static final String MODULE_MODE = "MODULE_MODE";
  public static final String REDIRECT_INPUT = "REDIRECT_INPUT";
  public static final String INPUT_FILE = "INPUT_FILE";
  private static final Pattern QUALIFIED_NAME = Pattern.compile("^[\\p{javaJavaIdentifierPart}-.]*\\p{javaJavaIdentifierPart}$");

  private String myScriptName;
  private String myScriptParameters;
  private boolean myShowCommandLineAfterwards = false;
  private boolean myEmulateTerminal = false;
  private boolean myModuleMode = false;
  @NotNull private String myInputFile = "";
  private boolean myRedirectInput = false;

  protected PythonRunConfiguration(Project project, ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
    setUnbufferedEnv();
  }

  @Override
  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    return new PythonRunConfigurationEditor(this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonScriptCommandLineState(this, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myScriptName)) {
      throw new RuntimeConfigurationException(
        PyBundle.message(isModuleMode() ? "runcfg.unittest.no_module_name" : "runcfg.unittest.no_script_name"));
    }
    else {
      if (isModuleMode() && !QUALIFIED_NAME.matcher(myScriptName).matches()) {
        throw new RuntimeConfigurationWarning(PyBundle.message("python.provide.a.qualified.name.of.a.module"));
      }
    }
    if (isRedirectInput() && !new File(myInputFile).exists()) {
      throw new RuntimeConfigurationWarning(PyBundle.message("python.input.file.doesn.t.exist"));
    }
  }

  @Override
  public String suggestedName() {
    final String scriptName = getScriptName();
    if (scriptName == null) return null;
    String name = new File(scriptName).getName();
    if (name.endsWith(".py")) {
      return name.substring(0, name.length() - 3);
    }
    return name;
  }

  @Override
  public String getScriptName() {
    return myScriptName;
  }

  @Override
  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  @Override
  public String getScriptParameters() {
    return myScriptParameters;
  }

  @Override
  public void setScriptParameters(String scriptParameters) {
    myScriptParameters = scriptParameters;
  }

  @Override
  public boolean showCommandLineAfterwards() {
    return myShowCommandLineAfterwards;
  }

  @Override
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
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    myScriptName = JDOMExternalizerUtil.readField(element, SCRIPT_NAME);
    myScriptParameters = JDOMExternalizerUtil.readField(element, PARAMETERS);
    myShowCommandLineAfterwards = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, SHOW_COMMAND_LINE, "false"));
    myEmulateTerminal = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, EMULATE_TERMINAL, "false"));
    myModuleMode = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, MODULE_MODE, "false"));
    myRedirectInput = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, REDIRECT_INPUT, "false"));
    myInputFile  = JDOMExternalizerUtil.readField(element, INPUT_FILE, "");
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, SCRIPT_NAME, myScriptName);
    JDOMExternalizerUtil.writeField(element, PARAMETERS, myScriptParameters);
    JDOMExternalizerUtil.writeField(element, SHOW_COMMAND_LINE, Boolean.toString(myShowCommandLineAfterwards));
    JDOMExternalizerUtil.writeField(element, EMULATE_TERMINAL, Boolean.toString(myEmulateTerminal));
    JDOMExternalizerUtil.writeField(element, MODULE_MODE, Boolean.toString(myModuleMode));
    JDOMExternalizerUtil.writeField(element, REDIRECT_INPUT, Boolean.toString(myRedirectInput));
    JDOMExternalizerUtil.writeField(element, INPUT_FILE, myInputFile);
  }

  @Override
  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  @Override
  public @NotNull InputRedirectOptions getInputRedirectOptions() {
    return new InputRedirectOptions() {
      @Override
      public boolean isRedirectInput() {
        return PythonRunConfiguration.this.isRedirectInput();
      }

      @Override
      public void setRedirectInput(boolean value) {
        PythonRunConfiguration.this.setRedirectInput(value);
      }

      @Override
      public @Nullable String getRedirectInputPath() {
        return StringUtil.nullize(getInputFile());
      }

      @Override
      public void setRedirectInputPath(String value) {
        setInputFile(StringUtil.notNullize(value));
      }
    };
  }

  public static void copyParams(PythonRunConfigurationParams source, PythonRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setModuleMode(source.isModuleMode());
    target.setScriptName(source.getScriptName());
    target.setScriptParameters(source.getScriptParameters());
    target.setShowCommandLineAfterwards(source.showCommandLineAfterwards());
    target.setEmulateTerminal(source.emulateTerminal());
    target.setRedirectInput(source.isRedirectInput());
    target.setInputFile(source.getInputFile());
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null &&
          Objects.equals(new File(virtualFile.getPath()).getAbsolutePath(), new File(myScriptName).getAbsolutePath())) {
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

  @NotNull
  @Override
  public String getInputFile() {
    return myInputFile;
  }

  @Override
  public void setInputFile(@NotNull String inputFile) {
    myInputFile = inputFile;
  }

  @Override
  public boolean isRedirectInput() {
    return myRedirectInput;
  }

  @Override
  public void setRedirectInput(boolean isRedirectInput) {
    myRedirectInput = isRedirectInput;
  }
}
