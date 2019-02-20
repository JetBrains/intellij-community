// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.NullableConsumer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.add.PyAddSdkDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PythonSdkDetailsStep extends BaseListPopupStep<String> {
  @Nullable private final DialogWrapper myShowAll;
  @Nullable private final Project myProject;
  @Nullable private final Module myModule;
  private final Sdk[] myExistingSdks;
  private final NullableConsumer<? super Sdk> mySdkAddedCallback;

  private static final String ADD = PyBundle.message("sdk.details.step.add");
  private static final String ALL = PyBundle.message("sdk.details.step.show.all");

  public static void show(@Nullable final Project project,
                          @Nullable final Module module,
                          @NotNull final Sdk[] existingSdks,
                          @NotNull final DialogWrapper showAllDialog,
                          @NotNull JComponent ownerComponent,
                          @NotNull final Point popupPoint,
                          @NotNull final NullableConsumer<? super Sdk> sdkAddedCallback) {
    final PythonSdkDetailsStep sdkHomesStep = new PythonSdkDetailsStep(project, module, showAllDialog, existingSdks, sdkAddedCallback);
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
    popup.showInScreenCoordinates(ownerComponent, popupPoint);
  }

  public PythonSdkDetailsStep(@Nullable final Project project,
                              @Nullable final Module module,
                              @Nullable final DialogWrapper showAllDialog,
                              @NotNull final Sdk[] existingSdks,
                              @NotNull final NullableConsumer<? super Sdk> sdkAddedCallback) {
    super(null, getAvailableOptions(showAllDialog != null));
    myProject = project;
    myModule = module;
    myShowAll = showAllDialog;
    myExistingSdks = existingSdks;
    mySdkAddedCallback = sdkAddedCallback;
  }

  private static List<String> getAvailableOptions(boolean showAll) {
    final List<String> options = new ArrayList<>();
    options.add(ADD);
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
    if (ADD.equals(selectedValue)) {
      PyAddSdkDialog.show(myProject, myModule, Arrays.asList(myExistingSdks), sdk -> mySdkAddedCallback.consume(sdk));
    }
    else if (myShowAll != null) {
      myShowAll.show();
    }
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
