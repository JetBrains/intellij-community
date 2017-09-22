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
package com.jetbrains.python.sdk;

import com.google.common.collect.Lists;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.NullableConsumer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.add.PyAddSdkDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PythonSdkDetailsStep extends BaseListPopupStep<String> {
  @Nullable private DialogWrapper myShowAll;
  private final Project myProject;
  private final Component myOwnerComponent;
  private final Sdk[] myExistingSdks;
  private final NullableConsumer<Sdk> mySdkAddedCallback;

  private static final String LOCAL = PyBundle.message("sdk.details.step.add.local");
  private static final String REMOTE = PyBundle.message("sdk.details.step.add.remote");
  private static final String ALL = PyBundle.message("sdk.details.step.show.all");
  @Nullable private String myNewProjectPath;

  public static void show(@Nullable final Project project,
                          @NotNull final Sdk[] existingSdks,
                          @Nullable final DialogWrapper showAllDialog,
                          @NotNull JComponent ownerComponent,
                          @NotNull final Point popupPoint,
                          @Nullable String newProjectPath,
                          @NotNull final NullableConsumer<Sdk> sdkAddedCallback) {
    final PythonSdkDetailsStep sdkHomesStep = new PythonSdkDetailsStep(project, showAllDialog, ownerComponent, existingSdks,
                                                                       sdkAddedCallback);
    sdkHomesStep.myNewProjectPath = newProjectPath;
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
    popup.showInScreenCoordinates(ownerComponent, popupPoint);
  }

  public PythonSdkDetailsStep(@Nullable final Project project,
                              @Nullable final DialogWrapper showAllDialog,
                              @NotNull final Component ownerComponent,
                              @NotNull final Sdk[] existingSdks,
                              @NotNull final NullableConsumer<Sdk> sdkAddedCallback) {
    super(null, getAvailableOptions(showAllDialog != null));
    myProject = project;
    myShowAll = showAllDialog;
    myOwnerComponent = ownerComponent;
    myExistingSdks = existingSdks;
    mySdkAddedCallback = sdkAddedCallback;
  }

  private static List<String> getAvailableOptions(boolean showAll) {
    final List<String> options = new ArrayList<>();
    options.add(LOCAL);
    if (PythonRemoteInterpreterManager.getInstance() != null) {
      options.add(REMOTE);
    }
    if (showAll) {
      options.add(ALL);
    }
    return options;
  }

  @Nullable
  @Override
  public ListSeparator getSeparatorAbove(String value) {
    return ALL.equals(value) ? new ListSeparator() : null;
  }

  private void optionSelected(final String selectedValue) {
    if (!ALL.equals(selectedValue) && myShowAll != null)
      Disposer.dispose(myShowAll.getDisposable());
    if (LOCAL.equals(selectedValue)) {
      createLocalSdk();
    }
    else if (REMOTE.equals(selectedValue)) {
      createRemoteSdk();
    }
    else if (myShowAll != null) {
      myShowAll.show();
    }
  }

  private void createLocalSdk() {
    final Project project = myNewProjectPath != null ? null : myProject;
    final PyAddSdkDialog dialog = new PyAddSdkDialog(project, Arrays.asList(myExistingSdks), myNewProjectPath);
    final Sdk sdk = dialog.showAndGet() ? dialog.getOrCreateSdk() : null;
    mySdkAddedCallback.consume(sdk);
  }

  private void createRemoteSdk() {
    PythonRemoteInterpreterManager remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance();
    if (remoteInterpreterManager != null) {
      remoteInterpreterManager.addRemoteSdk(myProject, myOwnerComponent, Lists.newArrayList(myExistingSdks), mySdkAddedCallback);
    }
    else {
      final String pathToPluginsPage = ShowSettingsUtil.getSettingsMenuName() + " | Plugins";
      Messages.showErrorDialog(PyBundle.message("remote.interpreter.error.plugin.missing", pathToPluginsPage),
                               PyBundle.message("remote.interpreter.add.title"));
    }
  }

  @Override
  public boolean canBeHidden(String value) {
    return true;
  }

  @Override
  public void canceled() {
    if (getFinalRunnable() == null && myShowAll != null)
      Disposer.dispose(myShowAll.getDisposable());
  }

  @Override
  public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
    return doFinalStep(() -> optionSelected(selectedValue));
  }
}
