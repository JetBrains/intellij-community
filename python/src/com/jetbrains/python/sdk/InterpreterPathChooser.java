/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.NullableConsumer;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
* @author yole
*/
public class InterpreterPathChooser extends BaseListPopupStep<String> {
  private final Project myProject;
  private final Component myOwnerComponent;
  private final Sdk[] myExistingSdks;
  private final NullableConsumer<Sdk> myCallback;

  private static final String LOCAL = "Local...";
  private static final String REMOTE = "Remote...";
  private static final String VIRTUALENV = "Create VirtualEnv...";

  public static void show(final Project project,
                          final Sdk[] existingSdks,
                          final RelativePoint popupPoint,
                          final boolean showVirtualEnv,
                          final NullableConsumer<Sdk> callback) {
    ListPopupStep sdkHomesStep = new InterpreterPathChooser(project, popupPoint.getComponent(), existingSdks, showVirtualEnv, callback);
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
    popup.show(popupPoint);
  }

  public InterpreterPathChooser(Project project,
                                Component ownerComponent,
                                Sdk[] existingSdks,
                                boolean showVirtualEnv,
                                NullableConsumer<Sdk> callback) {
    super("Select Interpreter Path", getSuggestedPythonSdkPaths(existingSdks, showVirtualEnv));
    myProject = project;
    myOwnerComponent = ownerComponent;
    myExistingSdks = existingSdks;
    myCallback = callback;
  }

  private static List<String> getSuggestedPythonSdkPaths(Sdk[] existingSdks, boolean showVirtualEnv) {
    List<String> paths = new ArrayList<String>();
    Collection<String> sdkHomes = PythonSdkType.getInstance().suggestHomePaths();
    for (String sdkHome : SdkConfigurationUtil.filterExistingPaths(PythonSdkType.getInstance(), sdkHomes, existingSdks)) {
      paths.add(FileUtil.getLocationRelativeToUserHome(sdkHome));
    }
    paths.add(LOCAL);
    if (PythonRemoteInterpreterManager.getInstance() != null) {
      paths.add(REMOTE);
    }
    if (showVirtualEnv) {
      paths.add(VIRTUALENV);
    }
    return paths;
  }

  @Nullable
  @Override
  public Icon getIconFor(String aValue) {
    if (LOCAL.equals(aValue) || REMOTE.equals(aValue) || VIRTUALENV.equals(aValue)) return null;
    String filePath = aValue;
    if (StringUtil.startsWithChar(filePath, '~')) {
      String home = SystemProperties.getUserHome();
      filePath = home + filePath.substring(1);
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getPlatformIndependentFlavor(filePath);
    return flavor != null ? flavor.getIcon() : PythonSdkType.getInstance().getIcon();
  }

  @NotNull
  @Override
  public String getTextFor(String value) {
    return FileUtil.toSystemDependentName(value);
  }

  private void sdkSelected(final String selectedValue) {
    if (LOCAL.equals(selectedValue)) {
      createLocalSdk();
    }
    else if (REMOTE.equals(selectedValue)) {
      createRemoteSdk();
    }
    else if (VIRTUALENV.equals(selectedValue)) {
      createVirtualEnvSdk();
    }
    else {
      createSdkFromPath(selectedValue);
    }
  }

  private void createLocalSdk() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        SdkConfigurationUtil.createSdk(myProject, myExistingSdks, myCallback, PythonSdkType.getInstance());
      }
    }, ModalityState.any());
  }

  private void createRemoteSdk() {
    PythonRemoteInterpreterManager remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance();
    if (remoteInterpreterManager != null) {
      remoteInterpreterManager.addRemoteSdk(myProject, myOwnerComponent, Lists.newArrayList(myExistingSdks), myCallback);
    }
    else {
      Messages.showErrorDialog("The Remote Hosts Access plugin is missing. Please enable the plugin in " +
                               ShowSettingsUtil.getSettingsMenuName() +
                               " | Plugins.", "Add Remote Interpreter");
    }
  }

  private void createSdkFromPath(String selectedPath) {
    String filePath = selectedPath;
    if (StringUtil.startsWithChar(filePath, '~')) {
      String home = SystemProperties.getUserHome();
      filePath = home + filePath.substring(1);
    }
    Sdk sdk = SdkConfigurationUtil.setupSdk(myExistingSdks,
                                            LocalFileSystem.getInstance().findFileByPath(filePath),
                                            PythonSdkType.getInstance(), false, null, null);
    myCallback.consume(sdk);
  }

  private void createVirtualEnvSdk() {
    final CreateVirtualEnvDialog dialog;
    final List<Sdk> allSdks = Arrays.asList(myExistingSdks);
    if (myProject != null) {
      dialog = new CreateVirtualEnvDialog(myProject, false, allSdks, null);
    }
    else {
      dialog = new CreateVirtualEnvDialog(myOwnerComponent, false, allSdks, null);
    }
    dialog.show();
    if (dialog.isOK()) {
      dialog.createVirtualEnv(allSdks, new CreateVirtualEnvDialog.VirtualEnvCallback() {
        @Override
        public void virtualEnvCreated(Sdk sdk, boolean associateWithProject, boolean setAsProjectInterpreter) {
          myCallback.consume(sdk);
        }
      });
    }
  }

  @Override
  public boolean canBeHidden(String value) {
    return true;
  }

  @Override
  public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
    return doFinalStep(new Runnable() {
      public void run() {
        sdkSelected(selectedValue);
      }
    });
  }
}
