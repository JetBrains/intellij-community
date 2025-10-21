// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.backend.shellcheck;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.sh.utils.ProjectUtil;
import com.intellij.ui.EditorNotifications;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public final class ShShellcheckInspection extends LocalInspectionTool implements ExternalAnnotatorBatchInspection {
  public static final @NonNls String SHORT_NAME = "ShellCheck";
  private static final @NonNls String SHELLCHECK_SETTINGS_TAG = "shellcheck_settings";
  private static final String DELIMITER = ",";
  private final Set<@NlsSafe String> myDisabledInspections = new TreeSet<>();
  private JComponent myOptionsPanel;

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void readSettings(@NotNull Element node) {
    String inspectionSettings = JDOMExternalizerUtil.readCustomField(node, SHELLCHECK_SETTINGS_TAG);
    if (StringUtil.isNotEmpty(inspectionSettings)) {
      myDisabledInspections.addAll(StringUtil.split(inspectionSettings, DELIMITER));
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    if (!myDisabledInspections.isEmpty()) {
      String joinedString = StringUtil.join(myDisabledInspections, DELIMITER);
      JDOMExternalizerUtil.writeCustomField(node, SHELLCHECK_SETTINGS_TAG, joinedString);
    }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      Project project = ProjectUtil.getProject(myOptionsPanel);
      EditorNotifications editorNotifications = EditorNotifications.getInstance(project);
      editorNotifications.updateAllNotifications();
    }
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    myOptionsPanel = new ShellcheckOptionsPanel(getDisabledInspections(), this::onInspectionChange).getPanel();
    return myOptionsPanel;
  }

  @VisibleForTesting
  public @NotNull Set<@NlsSafe String> getDisabledInspections() {
    return new HashSet<>(myDisabledInspections);
  }

  public void disableInspection(String inspectionCode) {
    if (StringUtil.isNotEmpty(inspectionCode)) myDisabledInspections.add(inspectionCode);
  }

  private void onInspectionChange(@NotNull String inspectionCode, boolean selected) {
    if (selected) {
      myDisabledInspections.add(inspectionCode);
    }
    else {
      myDisabledInspections.remove(inspectionCode);
    }
  }

  @VisibleForTesting
  public static @NotNull ShShellcheckInspection findShShellcheckInspection(@NotNull PsiElement element) {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    ShShellcheckInspection tool = (ShShellcheckInspection)profile.getUnwrappedTool(SHORT_NAME, element);
    return tool == null ? new ShShellcheckInspection() : tool;
  }
}