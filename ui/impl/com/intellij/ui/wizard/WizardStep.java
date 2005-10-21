/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.wizard;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public abstract class WizardStep {

  public static final WizardStep FORCED_GOAL_DROPPED = new Empty();
  public static final WizardStep FORCED_GOAL_ACHIEVED = new Empty();

  private String myTitle = "";
  private String myExplanation = "";
//todo:
  private Icon myIcon = IconLoader.getIcon("/newprojectwizard.png");
  private String myHelpId;

  protected WizardStep() {
  }

  public WizardStep(String title) {
    myTitle = title;
  }

  public WizardStep(String title, String explanation) {
    myTitle = title;
    myExplanation = explanation;
  }

  public WizardStep(String title, String explanation, Icon icon) {
    myTitle = title;
    myExplanation = explanation;
    myIcon = icon;
  }

  public WizardStep(String title, String explanation, Icon icon, String helpId) {
    myTitle = title;
    myExplanation = explanation;
    myIcon = icon;
    myHelpId = helpId;
  }

  public String getTitle() {
    return myTitle;
  }

  public String getExplanation() {
    return myExplanation;
  }

  public abstract JComponent prepare(WizardNavigationState state);

  public WizardStep onNext(WizardModel model) {
    return model.getNextFor(this);
  }

  public WizardStep onPrevious(WizardModel model) {
    return model.getPreviousFor(this);
  }

  public boolean onCancel() {
    return true;
  }

  public boolean onFinish() {
    return true;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }

  public static class Empty extends WizardStep {
    public JComponent prepare(WizardNavigationState state) {
      return null;
    }
  }

}