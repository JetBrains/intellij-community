package com.intellij.ide.util.gotoByName;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ChooseByNamePopup extends ChooseByNameBase implements ChooseByNamePopupComponent{
  private static final Key<ChooseByNamePopup> CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY = new Key<ChooseByNamePopup>("ChooseByNamePopup");
  private Component myOldFocusOwner = null;
  private ChooseByNamePopup(final Project project, final ChooseByNameModel model, final ChooseByNamePopup oldPopup) {
    super(project, model, oldPopup != null ? oldPopup.myTextField.getText() : null);
    if (oldPopup != null) { //inherit old focus owner
      myOldFocusOwner = oldPopup.myPreviouslyFocusedComponent;
    }
  }

  protected void initUI(final ChooseByNamePopupComponent.Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    super.initUI(callback, modalityState, allowMultipleSelection);
    //LaterInvocator.enterModal(myTextFieldPanel);
    if (myInitialText != null) {
      rebuildList(0, 0, null, ModalityState.current());
    }
    if (myOldFocusOwner != null){
      myPreviouslyFocusedComponent = myOldFocusOwner;
      myOldFocusOwner = null;
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

    if (preferredScrollPaneSize.width > layeredPane.getWidth() - bounds.x) {
      bounds.x = layeredPane.getX() + Math.max(1, layeredPane.getWidth() - preferredScrollPaneSize.width);
      if (preferredScrollPaneSize.width > layeredPane.getWidth() - bounds.x) {
        preferredScrollPaneSize.width = layeredPane.getWidth() - bounds.x;
        final JScrollBar horizontalScrollBar = myListScrollPane.getHorizontalScrollBar();
        if (horizontalScrollBar != null){
          preferredScrollPaneSize.height += horizontalScrollBar.getPreferredSize().getHeight();
        }
      }
    }

    Rectangle prefferedBounds = new Rectangle(bounds.x, bounds.y, preferredScrollPaneSize.width, preferredScrollPaneSize.height);

    if (myListScrollPane.isVisible()) {
      myListScrollPane.setBounds(prefferedBounds);
    }

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

      final List<Object> chosenElements = getChosenElements();
      if (chosenElements != null) {
        for (Object element : chosenElements) {
          myActionListener.elementChosen(element);
        }
      } else {
        return;
      }

      if (chosenElements.size() > 0){
        final String enteredText = myTextField.getText();
        if (enteredText.indexOf('*') >= 0) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.wildcards");
        }
        else {
          for (Object element : chosenElements) {
            final String name = myModel.getElementName(element);
            if (name != null) {
              if (!StringUtil.startsWithIgnoreCase(name, enteredText)) {
                FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.camelprefix");
                break;
              }
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

    //LaterInvocator.leaveModal(myTextFieldPanel);

    cleanupUI();
    myActionListener.onClose ();
  }

  private void cleanupUI() {
    JLayeredPane layeredPane = null;
    try {
      // Return focus back to the previous focused component if we need to do it and
      // previous focused componen is showing.
      if (
        myPreviouslyFocusedComponent instanceof JComponent &&
        myPreviouslyFocusedComponent.isShowing()
      ){
        final JComponent _component = (JComponent)myPreviouslyFocusedComponent;
        LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(_component);
      }
      if (myPreviouslyFocusedComponent != null) {
        myPreviouslyFocusedComponent.requestFocus();
      }

      final JRootPane rootPane = myTextFieldPanel.getRootPane();
      if (rootPane != null) {
        layeredPane = rootPane.getLayeredPane();
        layeredPane.remove(myListScrollPane);
        layeredPane.remove(myTextFieldPanel);
      }
    }
    finally {
      LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
    }

    if (layeredPane != null) {
      layeredPane.validate();
      layeredPane.repaint();
    }
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model) {
    final ChooseByNamePopup newPopup;
    final ChooseByNamePopup oldPopup = project.getUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY);
    if (oldPopup != null) {
      oldPopup.close(false);
      newPopup = new ChooseByNamePopup(project, model, oldPopup);
    } else {
      newPopup = new ChooseByNamePopup(project, model, null);
    }

    project.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, newPopup);
    return newPopup;
  }
}
