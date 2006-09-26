package com.intellij.ui.popup.mock;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;

import java.awt.*;

/**
 * User: Sergey.Vasiliev
 * Date: Nov 21, 2004
 */
public class MockConfirmation extends ListPopupImpl {
  String myOnYesText;
  public MockConfirmation(ListPopupStep aStep, String onYesText) {
    super(aStep);
    myOnYesText = onYesText;
  }

  public void showInCenterOf(Component aContainer) {
    getStep().onChosen(myOnYesText, true);
  }

  public void showUnderneathOf(Component aComponent) {
    getStep().onChosen(myOnYesText, true);
  }
}
