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
package com.jetbrains.python.newProject;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

public abstract class PythonProjectGenerator {
  private final List<SettingsListener> myListeners = ContainerUtil.newArrayList();

  @Nullable
  public JComponent getSettingsPanel(File baseDir) throws ProcessCanceledException {
    return null;
  }

  @Nullable
  public JPanel extendBasePanel() throws ProcessCanceledException {
    return null;
  }

  public Object getProjectSettings() {
    return new PyNewProjectSettings();
  }

  public ValidationResult warningValidation(@Nullable final Sdk sdk) {
    return ValidationResult.OK;
  }

  public void addSettingsStateListener(@NotNull SettingsListener listener) {
    myListeners.add(listener);
  }

  public void locationChanged(@NotNull final String newLocation) {}

  public interface SettingsListener {
    void stateChanged();
  }

  public void fireStateChanged() {
    for (SettingsListener listener : myListeners) {
      listener.stateChanged();
    }
  }

  @Nullable
  public BooleanFunction<PythonProjectGenerator> beforeProjectGenerated(@NotNull final Sdk sdk) {
    return null;
  }
}
