package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
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
  private ArrayList<AnAction> myVisibleActions;
  private ArrayList<AnAction> myNewVisibleActions;
  private final PresentationFactory myPresentationFactory;
  private DataManager myDataManager;
  private ActionManager myActionManager;
  private KeymapManager myKeymapManager;

  public IdeMenuBar(ActionManager actionManager, DataManager dataManager, KeymapManager keymapManager){
    myActionManager = actionManager;
    myTimerListener=new MyTimerListener();
    myKeymapManagerListener=new MyKeymapManagerListener();
    //(DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_MAIN_MENU);
    myVisibleActions = new ArrayList<AnAction>();
    myNewVisibleActions = new ArrayList<AnAction>();
    myPresentationFactory = new PresentationFactory();
    myDataManager = dataManager;
    myKeymapManager = keymapManager;
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify(){
    super.addNotify();
    updateMenuActions();
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

  void updateMenuActions() {
    myNewVisibleActions.clear();
    final DataContext dataContext = ((DataManagerImpl)myDataManager).getDataContextTest(this);

    expandActionGroup(dataContext, myNewVisibleActions, myActionManager);

    if (!myNewVisibleActions.equals(myVisibleActions)) {
      // should rebuild UI

      final boolean changeBarVisibility = myNewVisibleActions.size() == 0 || myVisibleActions.size() == 0;

      final ArrayList<AnAction> temp = myVisibleActions;
      myVisibleActions = myNewVisibleActions;
      myNewVisibleActions = temp;

      removeAll();
      Color background = null;
      for (final AnAction action : myVisibleActions) {
        final ActionMenu menu = new ActionMenu(null, ActionPlaces.MAIN_MENU, (ActionGroup)action, myPresentationFactory);
        add(menu);
        background = menu.getBackground();
      }

      if (background != null) {
        setBackground(background);
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

  /**
   * Hacks a problem under Alloy LaF which draws menubar in different background menu items are drawn in.
   */
  @Override public void updateUI() {
    super.updateUI();
    if (getMenuCount() > 0) {
      final JMenu menu = getMenu(0);
      menu.updateUI();
      setBackground(menu.getBackground());
    }
  }

  private void expandActionGroup(final DataContext context,
                                 final ArrayList<AnAction> newVisibleActions,
                                 ActionManager actionManager) {
    final ActionGroup mainActionGroup = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU);
    if (mainActionGroup == null) return;
    final AnAction[] children = mainActionGroup.getChildren(null);
    for (final AnAction action : children) {
      if (!(action instanceof ActionGroup)) {
        continue;
      }
      final Presentation presentation = myPresentationFactory.getPresentation(action);
      final AnActionEvent e = new AnActionEvent(null, context, ActionPlaces.MAIN_MENU, presentation,
                                                actionManager,
                                                0);
      e.setInjectedContext(action.isInInjectedContext());
      action.update(e);
      if (presentation.isVisible()) { // add only visible items
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

      Window mywindow = SwingUtilities.windowForComponent(IdeMenuBar.this);
      if (mywindow != null && !mywindow.isActive()) return;

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

      updateMenuActions();
    }
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener{
    public final void activeKeymapChanged(final Keymap keymap){
      updateMnemonicsVisibility();
    }
  }
}
