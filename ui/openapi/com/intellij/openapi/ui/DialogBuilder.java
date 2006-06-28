/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

public class DialogBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.DialogBuilder");

  @NonNls public static final String REQUEST_FOCUS_ENABLED = "requestFocusEnabled";

  private JComponent myCenterPanel;
  private String myTitle;
  private JComponent myPreferedFocusComponent;
  private String myDimensionServiceKey;
  private ArrayList<ActionDescriptor> myActions = null;
  private final MyDialogWrapper myDialogWrapper;
  private final ArrayList<Disposable> myDisposables = new ArrayList<Disposable>();
  private Runnable myCancelOperation = null;
  private Runnable myOkOperation = null;

  public int show() {
    return showImpl(true).getExitCode();
  }

  public void showNotModal() {
    showImpl(false);
  }

  public DialogBuilder(Project project) {
    myDialogWrapper = new MyDialogWrapper(project, true);
  }

  public DialogBuilder(Component parent) {
    myDialogWrapper = new MyDialogWrapper(parent, true);
  }

  private MyDialogWrapper showImpl(boolean isModal) {
    LOG.assertTrue(myTitle != null && myTitle.trim().length() != 0, String.valueOf(myTitle));
    myDialogWrapper.setTitle(myTitle);
    myDialogWrapper.init();
    myDialogWrapper.setModal(isModal);
    myDialogWrapper.show();
    return myDialogWrapper;
  }

  public void setCenterPanel(JComponent centerPanel) { myCenterPanel = centerPanel; }
  public void setTitle(String title) { myTitle = title; }
  public void setPreferedFocusComponent(JComponent component) { myPreferedFocusComponent = component; }
  public void setDimensionServiceKey(@NonNls String dimensionServiceKey) { myDimensionServiceKey = dimensionServiceKey; }

  public void addAction(Action action) {
    addActionDescriptor(new CustomActionDescriptor(action));
  }

  public <T extends ActionDescriptor> T addActionDescriptor(T actionDescriptor) {
    getActionDescriptors().add(actionDescriptor);
    return actionDescriptor;
  }

  private ArrayList<ActionDescriptor> getActionDescriptors() {
    if (myActions == null) removeAllActions();
    return myActions;
  }

  public void setActionDescriptors(ActionDescriptor[] descriptors) {
    removeAllActions();
    for (ActionDescriptor descriptor : descriptors) {
      myActions.add(descriptor);
    }
  }

  public void removeAllActions() {
    myActions = new ArrayList<ActionDescriptor>();
  }

  public Window getWindow() {
    return myDialogWrapper.getWindow();
  }

  public CustomizableAction addOkAction() {
    return addActionDescriptor(new OkActionDescriptor());
  }

  public CustomizableAction addCancelAction() {
    return addActionDescriptor(new CancelActionDescriptor());
  }

  public CustomizableAction addCloseButton() {
    CustomizableAction closeAction = addOkAction();
    closeAction.setText(CommonBundle.getCloseButtonText());
    return closeAction;
  }

  public void addDisposable(Disposable disposable) {
    myDisposables.add(disposable);
  }

  public void setButtonsAlignment(int alignment) {
    myDialogWrapper.setButtonsAlignment1(alignment);
  }

  public DialogWrapper getDialogWrapper() {
    return myDialogWrapper;
  }

  public void showModal(boolean modal) {
    if (modal) {
      show();
    }
    else {
      showNotModal();
    }
  }

  public void setHelpId(@NonNls String helpId) {
    myDialogWrapper.setHelpId(helpId);
  }

  public void setCancelOperation(Runnable runnable) {
    myCancelOperation = runnable;
  }

  public void setOkOperation(Runnable runnable) {
    myOkOperation = runnable;
  }

  public void setOkActionEnabled(final boolean isEnabled) {
    myDialogWrapper.setOKActionEnabled(isEnabled);
  }

  public CustomizableAction getOkAction() {
    return get(getActionDescriptors(), OkActionDescriptor.class);
  }

  private static CustomizableAction get(final ArrayList<ActionDescriptor> actionDescriptors, final Class aClass) {
    for (ActionDescriptor actionDescriptor : actionDescriptors) {
      if (actionDescriptor.getClass().isAssignableFrom(aClass)) return ((CustomizableAction)actionDescriptor);
    }
    return null;
  }

  public CustomizableAction getCancelAction() {
    return get(getActionDescriptors(), CancelActionDescriptor.class);
  }

  public Component getCenterPanel() {
    return myCenterPanel;
  }

  public interface ActionDescriptor {
    Action getAction(DialogWrapper dialogWrapper);
  }

  public static abstract class DialogActionDescriptor implements ActionDescriptor {
    private final String myName;
    private final Object myMnemonicChar;
    private boolean myIsDeafult = false;

    protected DialogActionDescriptor(String name, int mnemonicChar) {
      myName = name;
      myMnemonicChar = mnemonicChar != -1 ? new Integer(mnemonicChar) : null;
    }

    public Action getAction(DialogWrapper dialogWrapper) {
      Action action = createAction(dialogWrapper);
      action.putValue(Action.NAME, myName);
      if (myMnemonicChar != null) action.putValue(Action.MNEMONIC_KEY, myMnemonicChar);
      if (myIsDeafult) action.putValue(Action.DEFAULT, Boolean.TRUE);
      return action;
    }

    public void setDefault(boolean isDefault) {
      myIsDeafult = isDefault;
    }

    protected abstract Action createAction(DialogWrapper dialogWrapper);
  }

  public static class CloseDialogAction extends DialogActionDescriptor {
    private final int myExitCode;

    public CloseDialogAction(String name, int mnemonicChar, int exitCode) {
      super(name, mnemonicChar);
      myExitCode = exitCode;
    }

    public static CloseDialogAction createDefault(String name, int mnemonicChar, int exitCode) {
      CloseDialogAction closeDialogAction = new CloseDialogAction(name, mnemonicChar, exitCode);
      closeDialogAction.setDefault(true);
      return closeDialogAction;
    }

    protected Action createAction(final DialogWrapper dialogWrapper) {
      return new AbstractAction(){
        public void actionPerformed(ActionEvent e) {
          dialogWrapper.close(myExitCode);
        }
      };
    }
  }

  public interface CustomizableAction {
    void setText(String text);
  }

  public static class CustomActionDescriptor implements ActionDescriptor {
    private final Action myAction;

    public CustomActionDescriptor(Action action) {
      myAction = action;
    }

    public Action getAction(DialogWrapper dialogWrapper) {
      return myAction;
    }
  }

  private static abstract class BuiltinAction implements ActionDescriptor, CustomizableAction {
    protected String myText = null;

    public void setText(String text) {
      myText = text;
    }

    public Action getAction(DialogWrapper dialogWrapper) {
      Action builtinAction = getBuiltinAction((MyDialogWrapper)dialogWrapper);
      if (myText != null) builtinAction.putValue(Action.NAME, myText);
      return builtinAction;
    }

    protected abstract Action getBuiltinAction(MyDialogWrapper dialogWrapper);
  }

  public static class OkActionDescriptor extends BuiltinAction {
    protected Action getBuiltinAction(MyDialogWrapper dialogWrapper) {
      return dialogWrapper.getOKAction();
    }
  }

  public static class CancelActionDescriptor extends BuiltinAction {
    protected Action getBuiltinAction(MyDialogWrapper dialogWrapper) {
      return dialogWrapper.getCancelAction();
    }
  }

  private class MyDialogWrapper extends DialogWrapper {
    private String myHelpId = null;
    public MyDialogWrapper(Project project, boolean canBeParent) {
      super(project, canBeParent);
    }

    public MyDialogWrapper(Component parent, boolean canBeParent) {
      super(parent, canBeParent);
    }

    public void setHelpId(String helpId) {
      myHelpId = helpId;
    }

    public void init() { super.init(); }
    public Action getOKAction() { return super.getOKAction(); } // Make it public
    public Action getCancelAction() { return super.getCancelAction(); } // Make it public
    public void setButtonsAlignment1(int alignment) { setButtonsAlignment(alignment);}
    protected JComponent createCenterPanel() { return myCenterPanel; }

    public void dispose() {
      for (Disposable disposable : myDisposables) {
        disposable.dispose();
      }
      super.dispose();
    }

    public JComponent getPreferredFocusedComponent() {
      if (myPreferedFocusComponent != null) return myPreferedFocusComponent;
      FocusTraversalPolicy focusTraversalPolicy = null;
      Container container = myCenterPanel;
      while (container != null && (focusTraversalPolicy = container.getFocusTraversalPolicy()) == null && !(container instanceof Window)) {
        container = container.getParent();
      }
      if (focusTraversalPolicy == null) return null;
      Component component = focusTraversalPolicy.getDefaultComponent(myCenterPanel);
      while (!(component instanceof JComponent) && component != null) {
        component = focusTraversalPolicy.getComponentAfter(myCenterPanel, component);
      }
      return (JComponent)component;
    }

    protected String getDimensionServiceKey() {
      return myDimensionServiceKey;
    }

    protected JButton createJButtonForAction(Action action) {
      JButton button = super.createJButtonForAction(action);
      Object value = action.getValue(REQUEST_FOCUS_ENABLED);
      if (value instanceof Boolean) button.setRequestFocusEnabled(((Boolean)value).booleanValue());
      return button;
    }

    public void doCancelAction() {
      if (!getCancelAction().isEnabled()) return;
      if (myCancelOperation != null) {
        myCancelOperation.run();
      }
      else {
        super.doCancelAction();
      }
    }

    protected void doOKAction() {
      if (myOkOperation != null) {
        myOkOperation.run();
      }
      else {
        super.doOKAction();
      }
    }

    protected void doHelpAction() {
      if (myHelpId == null) {
        super.doHelpAction();
        return;
      }

      HelpManager.getInstance().invokeHelp(myHelpId);
    }

    protected Action[] createActions() {
      if (myActions == null) return super.createActions();
      ArrayList<Action> actions = new ArrayList<Action>(myActions.size());
      for (ActionDescriptor actionDescriptor : myActions) {
        actions.add(actionDescriptor.getAction(this));
      }
      if (myHelpId != null) actions.add(getHelpAction());
      return actions.toArray(new Action[actions.size()]);
    }
  }
}
