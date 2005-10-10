/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.mock.MockConfirmation;
import com.intellij.ui.popup.tree.TreePopupImpl;

public class PopupFactoryImpl extends PopupFactory {

  private static final Runnable EMPTY = new Runnable() {
    public void run() {
    }
  };

  public PopupFactoryImpl() {
  }

  public ListPopup createConfirmation(String title, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, "&Yes", "&No", onYes, defaultOptionIndex);
  }

  public ListPopup createConfirmation(String title, final String yesText, String noText, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, yesText, noText, onYes, EMPTY, defaultOptionIndex);
  }

  public ListPopup createConfirmation(String title, final String yesText, String noText, final Runnable onYes, final Runnable onNo, int defaultOptionIndex) {

      final BaseListPopupStep step = new BaseListPopupStep(title, new String[]{yesText, noText}) {
        public PopupStep onChosen(Object selectedValue) {
          if (selectedValue.equals(yesText)) {
            onYes.run();
          }
          else {
            onNo.run();
          }
          return FINAL_CHOICE;
        }

        public void canceled() {
          onNo.run();
        }

        public boolean isMnemonicsNavigationEnabled() {
          return true;
        }
      };
      step.setDefaultOptionIndex(defaultOptionIndex);

    if (!ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
      return new ListPopupImpl(step);
    } else {
      return new MockConfirmation(step, yesText);
    }
  }

  public ListPopup createWizardStep(PopupStep step) {
    return new ListPopupImpl(step);
  }

  public TreePopup createTree(Popup parent, TreePopupStep aStep, Object parentValue) {
    return new TreePopupImpl(parent, aStep, parentValue);
  }

  public TreePopup createTree(TreePopupStep aStep) {
    return new TreePopupImpl(aStep);
  }

}
