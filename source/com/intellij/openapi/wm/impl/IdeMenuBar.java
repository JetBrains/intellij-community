package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.TimerListener;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.WeakTimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.ex.KeymapManagerListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made non-final public for Fabrique
public class IdeMenuBar extends JMenuBar{
  private final MyTimerListener myTimerListener;
  private final MyKeymapManagerListener myKeymapManagerListener;
  private final DefaultActionGroup myActionGroup;
  private ArrayList myVisibleActions;
  private ArrayList myNewVisibleActions;
  private final PresentationFactory myPresentationFactory;
  private DataManager myDataManager;
  private ActionManager myActionManager;
  private KeymapManager myKeymapManager;

  public IdeMenuBar(ActionManager actionManager, DataManager dataManager, KeymapManager keymapManager){
    myActionManager = actionManager;
    myTimerListener=new MyTimerListener();
    myKeymapManagerListener=new MyKeymapManagerListener();
    myActionGroup = (DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_MAIN_MENU);
    myVisibleActions = new ArrayList();
    myNewVisibleActions = new ArrayList();
    myPresentationFactory = new PresentationFactory();
    myDataManager = dataManager;
    myKeymapManager = keymapManager;
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify(){
    super.addNotify();
    updateActions();
    // Add updater for menus
    final ActionManagerEx actionManager=(ActionManagerEx)myActionManager;
    actionManager.addTimerListener(1000,new WeakTimerListener(actionManager,myTimerListener));
    ((KeymapManagerEx)myKeymapManager).addKeymapManagerListener(myKeymapManagerListener);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public void removeNotify(){
    KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(myKeymapManagerListener);
    super.removeNotify();
  }

  private void updateActions() {
    myNewVisibleActions.clear();
    final DataContext dataContext = ((DataManagerImpl)myDataManager).getDataContextTest(this);

    expandActionGroup(dataContext, myNewVisibleActions, myActionManager);

    if (!myNewVisibleActions.equals(myVisibleActions)) {
      // should rebuild UI

      final boolean changeBarVisibility = myNewVisibleActions.size() == 0 || myVisibleActions.size() == 0;

      final ArrayList temp = myVisibleActions;
      myVisibleActions = myNewVisibleActions;
      myNewVisibleActions = temp;

      removeAll();
      for(int i=0;i<myVisibleActions.size();i++){
        final AnAction action=(AnAction)myVisibleActions.get(i);
        add(new ActionMenu(null, ActionPlaces.MAIN_MENU, (ActionGroup)action, myPresentationFactory));
      }
      updateMnemonicsVisibility();
      validate();

      if (changeBarVisibility) {
        invalidate();
        final JFrame frame = (JFrame)SwingUtilities.getAncestorOfClass(JFrame.class, this);
        if (frame != null) {
          frame.validate();
        }
      }

    }
  }

  private void expandActionGroup(final DataContext context, final ArrayList newVisibleActions, ActionManager actionManager) {
    if (myActionGroup == null) return;
    final AnAction[] children = myActionGroup.getChildren(null);
    for (int i = 0; i < children.length; i++) {
      final AnAction action = children[i];
      if (!(action instanceof ActionGroup)) {
        continue;
      }
      final Presentation presentation=myPresentationFactory.getPresentation(action);
      final AnActionEvent e = new AnActionEvent(null, context, ActionPlaces.MAIN_MENU, presentation,
                                                actionManager,
                                                0);
      action.update(e);
      if(presentation.isVisible()){ // add only visible items
        newVisibleActions.add(action);
      }
    }
  }

  private void updateMnemonicsVisibility(){
    final Keymap keyMap=myKeymapManager.getActiveKeymap();
    final boolean enabled=keyMap.areMnemonicsEnabled();
    for(int i=0;i<getMenuCount();i++){
      ((ActionMenu)getMenu(i)).setMnemonicEnabled(enabled);
    }
  }

  private final class MyTimerListener implements TimerListener{
    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(IdeMenuBar.this);
    }

    public void run(){
      if(!IdeMenuBar.this.isShowing()){
        return;
      }

      // do not update when a popup menu is shown (if popup menu contains action which is also in the menu bar, it should not be enabled/disabled)

      final MenuSelectionManager menuSelectionManager=MenuSelectionManager.defaultManager();
      final MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if(selectedPath.length>0){
        return;
      }

      // don't update toolbar if there is currently active modal dialog

      final Window window=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if(window instanceof Dialog){
        if (((Dialog)window).isModal()){
          return;
        }
      }

      updateActions();
    }
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener{
    public final void activeKeymapChanged(final Keymap keymap){
      updateMnemonicsVisibility();
    }
  }
}
