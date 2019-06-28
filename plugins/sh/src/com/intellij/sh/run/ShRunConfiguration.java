// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.sh.psi.ShFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.util.text.StringUtilRt.notNullize;

public class ShRunConfiguration extends LocatableConfigurationBase implements RefactoringListenerProvider {
  private static final String SCRIPT_PATH_TAG = "SCRIPT_PATH";
  private static final String SCRIPT_OPTIONS_TAG = "SCRIPT_OPTIONS";
  private static final String INTERPRETER_PATH_TAG = "INTERPRETER_PATH";
  private static final String INTERPRETER_OPTIONS_TAG = "INTERPRETER_OPTIONS";

  private String myScriptPath = "";
  private String myScriptOptions = "";
  private String myInterpreterPath = "";
  private String myInterpreterOptions = "";

  ShRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
    super(project, factory, name);
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new ShRunConfigurationEditor(getProject());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (!FileUtil.exists(myScriptPath)) {
      throw new RuntimeConfigurationError("Shell script not found");
    }
    if (StringUtil.isNotEmpty(myInterpreterPath) || !new File(myScriptPath).canExecute()) {
      if (!FileUtil.exists(myInterpreterPath)) throw new RuntimeConfigurationError("Interpreter not found");
      if (!new File(myInterpreterPath).canExecute()) throw new RuntimeConfigurationError("Interpreter should be executable file");
    }
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return new ShRunConfigurationProfileState(getProject(), this);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);

    JDOMExternalizerUtil.writeField(element, SCRIPT_PATH_TAG, myScriptPath);
    JDOMExternalizerUtil.writeField(element, SCRIPT_OPTIONS_TAG, myScriptOptions);
    JDOMExternalizerUtil.writeField(element, INTERPRETER_PATH_TAG, myInterpreterPath);
    JDOMExternalizerUtil.writeField(element, INTERPRETER_OPTIONS_TAG, myInterpreterOptions);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);

    myScriptPath = notNullize(JDOMExternalizerUtil.readField(element, SCRIPT_PATH_TAG), "");
    myScriptOptions = notNullize(JDOMExternalizerUtil.readField(element, SCRIPT_OPTIONS_TAG), "");
    myInterpreterPath = notNullize(JDOMExternalizerUtil.readField(element, INTERPRETER_PATH_TAG), "");
    myInterpreterOptions = notNullize(JDOMExternalizerUtil.readField(element, INTERPRETER_OPTIONS_TAG), "");
  }

  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (StringUtil.isEmpty(myScriptPath) || !(element instanceof ShFile) || !myScriptPath.equals(getPathByElement(element))) return null;

    return new RefactoringElementAdapter() {
      @Override
      protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
        if (newElement instanceof ShFile) {
          setScriptPath(((ShFile)newElement).getVirtualFile().getPath());
        }
      }

      @Override
      public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
        elementRenamedOrMoved(newElement);
      }
    };
  }

  @Nullable
  private static String getPathByElement(@NotNull PsiElement element) {
    VirtualFile vfile = PsiUtilCore.getVirtualFile(element);
    if (vfile == null) return null;
    return vfile.getPath();
  }

  String getScriptPath() {
    return myScriptPath;
  }

  void setScriptPath(@NotNull String scriptPath) {
    myScriptPath = scriptPath.trim();
  }

  String getScriptOptions() {
    return myScriptOptions;
  }

  void setScriptOptions(@NotNull String scriptOptions) {
    myScriptOptions = scriptOptions.trim();
  }

  String getInterpreterPath() {
    return myInterpreterPath;
  }

  void setInterpreterPath(@NotNull String interpreterPath) {
    myInterpreterPath = interpreterPath.trim();
  }

  String getInterpreterOptions() {
    return myInterpreterOptions;
  }

  void setInterpreterOptions(@NotNull String interpreterOptions) {
    myInterpreterOptions = interpreterOptions.trim();
  }
}
