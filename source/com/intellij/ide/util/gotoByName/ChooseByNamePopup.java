package com.intellij.ide.util.gotoByName;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;

import javax.swing.*;
import java.awt.*;

public class ChooseByNamePopup extends ChooseByNameBase{
  private static final Key<ChooseByNamePopup> CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY = new Key<ChooseByNamePopup>("ChooseByNamePopup");
  private ChooseByNamePopup(final Project project, final ChooseByNameModel model, final String initialText) {
    super(project, model, initialText);
  }

  protected void initUI(final ChooseByNameBase.Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    super.initUI(callback, modalityState, allowMultipleSelection);
    //LaterInvocatorEx.enterModal(myTextFieldPanel);
    if (myInitialText != null) {
      rebuildList(0, 0, null, ModalityState.current());
    }
  }

  protected boolean isCheckboxVisible() {
    return true;
  }

  protected boolean isShowListForEmptyPattern(){
    return false;
  }

  protected boolean isCloseByFocusLost(){
    return true;
  }

  protected void showList() {
    final JLayeredPane layeredPane = myTextField.getRootPane().getLayeredPane();
    final Rectangle bounds = myTextFieldPanel.getBounds();
    bounds.y += myTextFieldPanel.getHeight();
    final Dimension preferredScrollPaneSize = myListScrollPane.getPreferredSize();
    preferredScrollPaneSize.width = Math.max(myTextFieldPanel.getWidth(), preferredScrollPaneSize.width);
    if (bounds.y + preferredScrollPaneSize.height > layeredPane.getHeight()){ // clip scroll pane
      preferredScrollPaneSize.height = layeredPane.getHeight() - bounds.y;
    }

    {
      if(preferredScrollPaneSize.width > layeredPane.getWidth() - bounds.x){
        bounds.x = layeredPane.getX() + Math.max (1, layeredPane.getWidth()-preferredScrollPaneSize.width);
        if(preferredScrollPaneSize.width > layeredPane.getWidth() - bounds.x){
          preferredScrollPaneSize.width = layeredPane.getWidth() - bounds.x;
        }
      }
    }

    myListScrollPane.setBounds(bounds.x, bounds.y, preferredScrollPaneSize.width, preferredScrollPaneSize.height);

    layeredPane.add(myListScrollPane, new Integer(600));
    layeredPane.moveToFront(myListScrollPane);
    myListScrollPane.validate();
    myListScrollPane.setVisible(true);
  }

  protected void hideList() {
    if (myListScrollPane.isVisible()){
      myListScrollPane.setVisible(false);
    }
  }

  protected void close(final boolean isOk) {
    if (myDisposedFlag){
      return;
    }

    if (isOk){
      myModel.saveInitialCheckBoxState(myCheckBox.isSelected());

      final Object[] chosenElements = getChosenElements();
      for (int i = 0; i < chosenElements.length; i++) {
        myActionListener.elementChosen(chosenElements[i]);
      }
      
      if (chosenElements.length > 0){
        final String enteredText = myTextField.getText().toLowerCase();
        if (enteredText.indexOf('*') >= 0) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.wildcards");
        }
        else {
          for (int i = 0; i < chosenElements.length; i++) {
            final String choosenElementText = myModel.getElementName(chosenElements[i]).toLowerCase();
            if (!choosenElementText.startsWith(enteredText)) {
              FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.camelprefix");
              break;
            }
          }
        }
      }
      else{
        return;
      }
    }

    myDisposedFlag = true;
    myAlarm.cancelAllRequests();
    myProject.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, null);

    //LaterInvocatorEx.leaveModal(myTextFieldPanel);

    cleanupUI();
    myActionListener.onClose ();
  }

  private void cleanupUI() {
    JLayeredPane layeredPane;
    try {
      // Return focus back to the previous focused component if we need to do it and
      // previous focused componen is showing.
      if (
        (myPreviouslyFocusedComponent instanceof JComponent) &&
        myPreviouslyFocusedComponent.isShowing()
      ){
        final JComponent _component = (JComponent)myPreviouslyFocusedComponent;
        LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(_component);
      }
      if (myPreviouslyFocusedComponent != null) {
        myPreviouslyFocusedComponent.requestFocus();
      }

      layeredPane = myTextFieldPanel.getRootPane().getLayeredPane();
      layeredPane.remove(myListScrollPane);
      layeredPane.remove(myTextFieldPanel);
    }
    finally {
      LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
    }
    layeredPane.validate();
    layeredPane.repaint();
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model) {
    final ChooseByNamePopup newPopup;
    final ChooseByNamePopup oldPopup = project.getUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY);
    if (oldPopup != null) {
      final String initialText = oldPopup.myTextField.getText();
      oldPopup.close(false);
      newPopup = new ChooseByNamePopup(project, model, initialText);
    } else {
      newPopup = new ChooseByNamePopup(project, model, null);
    }

    project.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, newPopup);
    return newPopup;
  }
}
