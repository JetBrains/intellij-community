package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Activatable;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;

public final class ShadowAction {

  private AnAction myAction;
  private AnAction myCopyFromAction;
  private JComponent myComponent;

  private KeymapManagerListener myKeymapManagerListener;

  private ShortcutSet myShortcutSet;
  private String myActionId;

  private Keymap.Listener myKeymapListener;
  private Keymap myKeymap;

  private Presentation myPresentation;

  public ShadowAction(AnAction action, AnAction copyFromAction, JComponent component, Presentation presentation) {
    this(action, copyFromAction, component);
    myPresentation = presentation;
  }

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
    final KeymapManager mgr = getKeymapManager();
    if (mgr == null) return;


    mgr.addKeymapManagerListener(myKeymapManagerListener);
    rebound();
  }

  private void disconnect() {
    final KeymapManager mgr = getKeymapManager();
    if (mgr == null) return;


    mgr.removeKeymapManagerListener(myKeymapManagerListener);
    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(myKeymapListener);
    }
  }

  private void rebound() {
    final KeymapManager mgr = getKeymapManager();
    if (mgr == null) return;

    myActionId = ActionManager.getInstance().getId(myCopyFromAction);
    if (myPresentation == null) {
      myAction.copyFrom(myCopyFromAction);
    } else {
      myAction.getTemplatePresentation().copyFrom(myPresentation);
      myAction.copyShortcutFrom(myCopyFromAction);
    }

    if (myShortcutSet != null) {
      myAction.unregisterCustomShortcutSet(myComponent);
    }

    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(myKeymapListener);
    }

    myKeymap = mgr.getActiveKeymap();
    myKeymap.addShortcutChangeListener(myKeymapListener);

    if (myActionId == null) return;

    final Shortcut[] shortcuts = myKeymap.getShortcuts(myActionId);
    myShortcutSet = new CustomShortcutSet(shortcuts);
    myAction.registerCustomShortcutSet(myShortcutSet, myComponent);
  }

  private static @Nullable
  KeymapManager getKeymapManager() {
    if (ApplicationManager.getApplication().isDisposed()) return null;
    return KeymapManager.getInstance();
  }

}
