// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorNotifications;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ShShellcheckInspection extends LocalInspectionTool implements ExternalAnnotatorBatchInspection {
  @NonNls public static final String SHORT_NAME = "ShellCheck";
  @NonNls private static final String SHELLCHECK_SETTINGS_TAG = "shellcheck_settings";
  private static final String DELIMITER = ",";
  private final Set<String> myDisabledInspections = new TreeSet<>();
  private JComponent myOptionsPanel;

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getShortName() {
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

    Project project = ProjectUtil.guessCurrentProject(myOptionsPanel);
    EditorNotifications editorNotifications = EditorNotifications.getInstance(project);
    if (editorNotifications != null) {
      editorNotifications.updateAllNotifications();
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    myOptionsPanel = new ShellcheckOptionsPanel(getDisabledInspections(), this::onInspectionChange).getPanel();
    return myOptionsPanel;
  }

  @NotNull
  public Set<String> getDisabledInspections() {
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

  @NotNull
  public static ShShellcheckInspection findShShellcheckInspection(@NotNull PsiElement element) {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    ShShellcheckInspection tool = (ShShellcheckInspection)profile.getUnwrappedTool(SHORT_NAME, element);
    return tool == null ? new ShShellcheckInspection() : tool;
  }
}