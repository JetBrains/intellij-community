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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
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
import com.jetbrains.python.packaging.PyCondaPackageService;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PythonSdkDetailsStep extends BaseListPopupStep<String> {
  @Nullable private DialogWrapper myMore;
  private final Project myProject;
  private final Component myOwnerComponent;
  private final Sdk[] myExistingSdks;
  private final NullableConsumer<Sdk> mySdkAddedCallback;

  private static final String LOCAL = PyBundle.message("sdk.details.step.add.local");
  private static final String REMOTE = PyBundle.message("sdk.details.step.add.remote");
  private static final String VIRTUALENV = PyBundle.message("sdk.details.step.create.virtual.env");
  private static final String CONDA = PyBundle.message("sdk.details.step.create.conda.env");
  private static final String MORE = PyBundle.message("sdk.details.step.show.more");
  private boolean myNewProject;

  public static void show(final Project project,
                          final Sdk[] existingSdks,
                          @Nullable final DialogWrapper moreDialog,
                          JComponent ownerComponent, final Point popupPoint,
                          final NullableConsumer<Sdk> sdkAddedCallback) {
    show(project, existingSdks, moreDialog, ownerComponent, popupPoint, sdkAddedCallback, false);

  }

  public static void show(final Project project,
                          final Sdk[] existingSdks,
                          @Nullable final DialogWrapper moreDialog,
                          JComponent ownerComponent, final Point popupPoint,
                          final NullableConsumer<Sdk> sdkAddedCallback, boolean isNewProject) {
    final PythonSdkDetailsStep sdkHomesStep = new PythonSdkDetailsStep(project, moreDialog, ownerComponent, existingSdks, sdkAddedCallback);
    sdkHomesStep.setNewProject(isNewProject);
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
    popup.showInScreenCoordinates(ownerComponent, popupPoint);
  }

  private void setNewProject(boolean isNewProject) {
    myNewProject = isNewProject;
  }

  public PythonSdkDetailsStep(@Nullable final Project project,
                              @Nullable final DialogWrapper moreDialog, @NotNull final Component ownerComponent,
                              @NotNull final Sdk[] existingSdks,
                              @NotNull final NullableConsumer<Sdk> sdkAddedCallback) {
    super(null, getAvailableOptions(moreDialog != null));
    myProject = project;
    myMore = moreDialog;
    myOwnerComponent = ownerComponent;
    myExistingSdks = existingSdks;
    mySdkAddedCallback = sdkAddedCallback;
  }

  private static List<String> getAvailableOptions(boolean showMore) {
    final List<String> options = new ArrayList<>();
    options.add(LOCAL);
    if (PythonRemoteInterpreterManager.getInstance() != null) {
      options.add(REMOTE);
    }
    options.add(VIRTUALENV);
    if (PyCondaPackageService.getSystemCondaExecutable() != null) {
      options.add(CONDA);
    }

    if (showMore) {
      options.add(MORE);
    }
    return options;
  }

  @Nullable
  @Override
  public ListSeparator getSeparatorAbove(String value) {
    return MORE.equals(value) ? new ListSeparator() : null;
  }

  private void optionSelected(final String selectedValue) {
    if (!MORE.equals(selectedValue) && myMore != null)
      Disposer.dispose(myMore.getDisposable());
    if (LOCAL.equals(selectedValue)) {
      createLocalSdk();
    }
    else if (REMOTE.equals(selectedValue)) {
      createRemoteSdk();
    }
    else if (VIRTUALENV.equals(selectedValue)) {
      createVirtualEnvSdk();
    }
    else if (CONDA.equals(selectedValue)) {
      createCondaEnvSdk();
    }
    else if (myMore != null) {
      myMore.show();
    }
  }

  private void createLocalSdk() {
    SdkConfigurationUtil.createSdk(myProject, myExistingSdks, mySdkAddedCallback, false, PythonSdkType.getInstance());
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

  private void createVirtualEnvSdk() {
    AbstractCreateVirtualEnvDialog.VirtualEnvCallback callback = getVEnvCallback();

    final CreateVirtualEnvDialog dialog;
    final List<Sdk> allSdks = Lists.newArrayList(myExistingSdks);
    Iterables.removeIf(allSdks, new Predicate<Sdk>() {
      @Override
      public boolean apply(Sdk sdk) {
        return !(sdk.getSdkType() instanceof PythonSdkType);
      }
    });
    final List<PythonSdkFlavor> flavors = PythonSdkFlavor.getApplicableFlavors(false);
    for (PythonSdkFlavor flavor : flavors) {
      final Collection<String> strings = flavor.suggestHomePaths();
      for (String string : SdkConfigurationUtil.filterExistingPaths(PythonSdkType.getInstance(), strings, myExistingSdks)) {
        allSdks.add(new PyDetectedSdk(string));
      }
    }
    final Set<String> sdks = PySdkService.getInstance().getAddedSdks();
    for (String string : SdkConfigurationUtil.filterExistingPaths(PythonSdkType.getInstance(), sdks, myExistingSdks)) {
      allSdks.add(new PyDetectedSdk(string));
    }
    if (myProject != null) {
      dialog = new CreateVirtualEnvDialog(myProject, allSdks);
    }
    else {
      dialog = new CreateVirtualEnvDialog(myOwnerComponent, allSdks);
    }
    if (dialog.showAndGet()) {
      dialog.createVirtualEnv(callback);
    }
  }

  @NotNull
  private AbstractCreateVirtualEnvDialog.VirtualEnvCallback getVEnvCallback() {
    return new CreateVirtualEnvDialog.VirtualEnvCallback() {
        @Override
        public void virtualEnvCreated(Sdk sdk, boolean associateWithProject) {
          if (associateWithProject) {
            SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
            if (additionalData == null) {
              additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));
              ((ProjectJdkImpl)sdk).setSdkAdditionalData(additionalData);
            }
            if (myNewProject) {
              ((PythonSdkAdditionalData)additionalData).associateWithNewProject();
            }
            else {
              ((PythonSdkAdditionalData)additionalData).associateWithProject(myProject);
            }
          }
          mySdkAddedCallback.consume(sdk);
        }
      };
  }

  private void createCondaEnvSdk() {
    AbstractCreateVirtualEnvDialog.VirtualEnvCallback callback = getVEnvCallback();

    final CreateCondaEnvDialog dialog;
    if (myProject != null) {
      dialog = new CreateCondaEnvDialog(myProject);
    }
    else {
      dialog = new CreateCondaEnvDialog(myOwnerComponent);
    }
    if (dialog.showAndGet()) {
      dialog.createVirtualEnv(callback);
    }
  }

  @Override
  public boolean canBeHidden(String value) {
    return true;
  }

  @Override
  public void canceled() {
    if (getFinalRunnable() == null && myMore != null)
      Disposer.dispose(myMore.getDisposable());
  }

  @Override
  public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
    return doFinalStep(() -> optionSelected(selectedValue));
  }
}
