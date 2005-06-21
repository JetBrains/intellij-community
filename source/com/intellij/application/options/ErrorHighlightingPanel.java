package com.intellij.application.options;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

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
    super(DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().getName(), null);
    add(getAutoreparsePanel(), BorderLayout.NORTH);
  }


  protected void initDescriptors() {
    super.initDescriptors();
    InspectionTool[] tools = mySelectedProfile.getInspectionTools();
    for (int i = 0; i < tools.length; i++) {
      final InspectionTool tool = tools[i];
      if (tool instanceof LocalInspectionToolWrapper) {
        myDescriptors.add(new Descriptor(tool, mySelectedProfile));
      }
    }
    addGeneralDescriptors();
  }

  private void addGeneralDescriptors() {
    myDescriptors.add(new Descriptor(HighlightDisplayKey.DEPRECATED_SYMBOL, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.UNUSED_IMPORT, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.UNUSED_SYMBOL, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.UNUSED_THROWS_DECL, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.SILLY_ASSIGNMENT, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.ILLEGAL_DEPENDENCY, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.JAVADOC_ERROR, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG, mySelectedProfile));
    
    myDescriptors.add(new Descriptor(HighlightDisplayKey.UNKNOWN_HTML_ATTRIBUTES, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.UNKNOWN_HTML_TAG, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.REQUIRED_HTML_ATTRIBUTE, mySelectedProfile));
    
    myDescriptors.add(new Descriptor(HighlightDisplayKey.EJB_ERROR, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.EJB_WARNING, mySelectedProfile));
    myDescriptors.add(new Descriptor(HighlightDisplayKey.UNCHECKED_WARNING, mySelectedProfile));
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
    settings.setInspectionProfile((InspectionProfileImpl)mySelectedProfile.getParentProfile());

    settings.AUTOREPARSE_DELAY = getAutoReparseDelay();
    settings.setImportHintEnabled(myCbShowImportPopup.isSelected());
    settings.ERROR_STRIPE_MARK_MIN_HEIGHT = getErrorStripeMarkMinHeight();

    settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = myNextErrorGoesToErrorsFirst.isSelected();

    settings.SUPPRESS_WARNINGS = mySuppressWay.isSelected();

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      DaemonCodeAnalyzer.getInstance(projects[i]).settingsChanged();
    }

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
