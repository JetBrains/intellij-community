package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Activatable;

import javax.swing.*;

public final class ShadowAction {

  private AnAction myAction;
  private AnAction myCopyFromAction;
  private JComponent myComponent;

  private KeymapManagerListener myKeymapManagerListener;

  private ShortcutSet myShortcutSet;
  private String myActionId;

  private Keymap.Listener myKeymapListener;
  private Keymap myKeymap;

  public ShadowAction(AnAction action, AnAction copyFromAction, JComponent component) {
    myAction = action;

    myCopyFromAction = copyFromAction;
    myComponent = component;
    myActionId = ActionManager.getInstance().getId(myCopyFromAction);

    myKeymapListener = new Keymap.Listener() {
      public void onShortcutChanged(final String actionId) {
        if (myActionId == null || actionId.equals(myActionId)) {
          rebound();
        }
      }
    };

    myKeymapManagerListener = new KeymapManagerListener() {
      public void activeKeymapChanged(final Keymap keymap) {
        rebound();
      }
    };

    new UiNotifyConnector(myComponent, new Activatable() {
      public void showNotify() {
        _connect();
      }

      public void hideNotify() {
        disconnect();
      }
    });
  }

  private void _connect() {
    disconnect();
    getKeymapManager().addKeymapManagerListener(myKeymapManagerListener);
    rebound();
  }

  private void disconnect() {
    getKeymapManager().removeKeymapManagerListener(myKeymapManagerListener);
    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(myKeymapListener);
    }
  }

  private void rebound() {
    myActionId = ActionManager.getInstance().getId(myCopyFromAction);
    myAction.copyFrom(myCopyFromAction);

    if (myShortcutSet != null) {
      myAction.unregisterCustomShortcutSet(myComponent);
    }

    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(myKeymapListener);
    }

    myKeymap = getKeymapManager().getActiveKeymap();
    myKeymap.addShortcutChangeListener(myKeymapListener);

    if (myActionId == null) return;

    final Shortcut[] shortcuts = myKeymap.getShortcuts(myActionId);
    myShortcutSet = new CustomShortcutSet(shortcuts);
    myAction.registerCustomShortcutSet(myShortcutSet, myComponent);
  }

  private static KeymapManager getKeymapManager() {
    return KeymapManager.getInstance();
  }

}
