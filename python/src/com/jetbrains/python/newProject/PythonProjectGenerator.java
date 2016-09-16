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
package com.jetbrains.python.newProject;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.List;

/**
 * This class encapsulates remote settings, so one should extend it for any python project that supports remote generation, at least
 * Instead of {@link #generateProject(Project, VirtualFile, PyNewProjectSettings, Module)} inheritor shall use
 * {@link #configureProject(Project, VirtualFile, PyNewProjectSettings, Module)}
 *
 * @param <T> project settings
 */
public abstract class PythonProjectGenerator<T extends PyNewProjectSettings> implements DirectoryProjectGenerator<T> {
  private final List<SettingsListener> myListeners = ContainerUtil.newArrayList();
  @Nullable private MouseListener myErrorLabelMouseListener;

  @Nullable
  public JComponent getSettingsPanel(File baseDir) throws ProcessCanceledException {
    return null;
  }

  @Nullable
  public JPanel extendBasePanel() throws ProcessCanceledException {
    return null;
  }

  @Override
  public final void generateProject(@NotNull final Project project,
                                    @NotNull final VirtualFile baseDir,
                                    @Nullable final T settings,
                                    @NotNull final Module module) {
    /*Instead of this method overwrite ``configureProject``*/

    // If we deal with remote project -- use remote manager to configure it
    final PythonRemoteInterpreterManager remoteManager = PythonRemoteInterpreterManager.getInstance();
    final Sdk sdk = (settings != null ? settings.getSdk() : null);
    if (remoteManager != null && PythonSdkType.isRemote(sdk)) {
      remoteManager.prepareRemoteSettingsIfNeeded(module, sdk);
    }
    configureProject(project, baseDir, settings, module);
  }

  /**
   * Does real work to generate project
   */
  protected abstract void configureProject(@NotNull final Project project,
                                           @NotNull final VirtualFile baseDir,
                                           @Nullable final T settings,
                                           @NotNull final Module module);

  public Object getProjectSettings() {
    return new PyNewProjectSettings();
  }

  public ValidationResult warningValidation(@Nullable final Sdk sdk) {
    return ValidationResult.OK;
  }

  public void addSettingsStateListener(@NotNull SettingsListener listener) {
    myListeners.add(listener);
  }

  public void locationChanged(@NotNull final String newLocation) {
  }

  public interface SettingsListener {
    void stateChanged();
  }

  public void fireStateChanged() {
    for (SettingsListener listener : myListeners) {
      listener.stateChanged();
    }
  }

  @Nullable
  public BooleanFunction<PythonProjectGenerator> beforeProjectGenerated(@Nullable final Sdk sdk) {
    return null;
  }

  public boolean hideInterpreter() {
    return false;
  }

  public void addErrorLabelMouseListener(@NotNull final MouseListener mouseListener) {
    myErrorLabelMouseListener = mouseListener;
  }

  @Nullable
  public MouseListener getErrorLabelMouseListener() {
    return myErrorLabelMouseListener;
  }

  public void createAndAddVirtualEnv(Project project, PyNewProjectSettings settings) {
  }
}
