package com.intellij.application.options;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInspection.ex.InspectionToolsPanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;

import javax.swing.*;
import java.awt.*;

public class ErrorHighlightingPanel extends InspectionToolsPanel {
  private JTextField myAutoreparseDelayField;
  private JCheckBox myCbShowImportPopup;
  private JTextField myMarkMinHeight;
  private JPanel myPanel;
  private JCheckBox myNextErrorGoesToErrorsFirst;
  private JCheckBox mySuppressWay;

  public ErrorHighlightingPanel() {
    super(InspectionProfileManager.getInstance().getRootProfile().getName(), null);
    add(getAutoreparsePanel(), BorderLayout.NORTH);
  }

  private JPanel getAutoreparsePanel() {
    return myPanel;
  }

  public void reset() {
    super.reset();
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    myAutoreparseDelayField.setText(Integer.toString(settings.AUTOREPARSE_DELAY));

    myCbShowImportPopup.setSelected(settings.isImportHintEnabled());

    myMarkMinHeight.setText(Integer.toString(settings.ERROR_STRIPE_MARK_MIN_HEIGHT));
    myNextErrorGoesToErrorsFirst.setSelected(settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST);
    mySuppressWay.setSelected(settings.SUPPRESS_WARNINGS);
  }

  public void apply() throws ConfigurationException {
    super.apply();
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();

    settings.AUTOREPARSE_DELAY = getAutoReparseDelay();
    settings.setImportHintEnabled(myCbShowImportPopup.isSelected());
    settings.ERROR_STRIPE_MARK_MIN_HEIGHT = getErrorStripeMarkMinHeight();

    settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = myNextErrorGoesToErrorsFirst.isSelected();

    settings.SUPPRESS_WARNINGS = mySuppressWay.isSelected();

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
    InspectionProfileManager.getInstance().setRootProfile(mySelectedProfile.getName());
  }

  private int getErrorStripeMarkMinHeight() {
    return parseInteger(myMarkMinHeight);
  }

  public boolean isModified() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean isModified = settings.AUTOREPARSE_DELAY != getAutoReparseDelay();
    isModified |= myCbShowImportPopup.isSelected() != settings.isImportHintEnabled();
    isModified |= getErrorStripeMarkMinHeight() != settings.ERROR_STRIPE_MARK_MIN_HEIGHT;
    isModified |= myNextErrorGoesToErrorsFirst.isSelected() != settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST;
    isModified |= mySuppressWay.isSelected() != settings.SUPPRESS_WARNINGS;
    if (isModified) return true;
    return super.isModified();
  }


  private int getAutoReparseDelay() {
    return parseInteger(myAutoreparseDelayField);
  }

  private static int parseInteger(final JTextField textField) {
    try {
      int delay = Integer.parseInt(textField.getText());
      if (delay < 0) {
        delay = 0;
      }
      return delay;
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }
}
