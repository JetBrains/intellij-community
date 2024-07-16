// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @Nullable DialogWrapper myShowAll;
  private final @Nullable Project myProject;
  private final @Nullable Module myModule;
  private final Sdk[] myExistingSdks;
  private final NullableConsumer<? super Sdk> mySdkAddedCallback;

  public static void show(final @Nullable Project project,
                          final @Nullable Module module,
                          final Sdk @NotNull [] existingSdks,
                          final @NotNull DialogWrapper showAllDialog,
                          @NotNull JComponent ownerComponent,
                          final @NotNull Point popupPoint,
                          final @NotNull NullableConsumer<? super Sdk> sdkAddedCallback) {
    final PythonSdkDetailsStep sdkHomesStep = new PythonSdkDetailsStep(project, module, showAllDialog, existingSdks, sdkAddedCallback);
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
    popup.showInScreenCoordinates(ownerComponent, popupPoint);
  }

  public PythonSdkDetailsStep(final @Nullable Project project,
                              final @Nullable Module module,
                              final @Nullable DialogWrapper showAllDialog,
                              final Sdk @NotNull [] existingSdks,
                              final @NotNull NullableConsumer<? super Sdk> sdkAddedCallback) {
    super(null, getAvailableOptions(showAllDialog != null));
    myProject = project;
    myModule = module;
    myShowAll = showAllDialog;
    myExistingSdks = existingSdks;
    mySdkAddedCallback = sdkAddedCallback;
  }

  private static List<String> getAvailableOptions(boolean showAll) {
    final List<String> options = new ArrayList<>();
    options.add(getAdd());
    if (showAll) {
      options.add(getAll());
    }
    return options;
  }

  @Override
  public @Nullable ListSeparator getSeparatorAbove(String value) {
    return getAll().equals(value) ? new ListSeparator() : null;
  }

  private void optionSelected(final String selectedValue) {
    if (!getAll().equals(selectedValue) && myShowAll != null)
      Disposer.dispose(myShowAll.getDisposable());
    if (getAdd().equals(selectedValue)) {
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

  private static String getAdd() {
    return PyBundle.message("sdk.details.step.add");
  }

  private static String getAll() {
    return PyBundle.message("sdk.details.step.show.all");
  }
}
