/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.wizard;

interface WizardCallback {

  void onStepChanged();
  void onWizardGoalDropped();
  void onWizardGoalAchieved();

  WizardCallback EMPTY = new WizardCallback() {
    public void onStepChanged() {
    }

    public void onWizardGoalDropped() {
    }

    public void onWizardGoalAchieved() {
    }
  };


}