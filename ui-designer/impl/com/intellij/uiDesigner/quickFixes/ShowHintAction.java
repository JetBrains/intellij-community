package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.uiDesigner.propertyInspector.PropertyInspector;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ShowHintAction extends AnAction{
  private final QuickFixManager myManager;

  public ShowHintAction(final QuickFixManager manager, final JComponent component) {
    if(manager == null){
      throw new IllegalArgumentException();
    }
    if(component == null){
      throw new IllegalArgumentException();
    }
    myManager = manager;
    registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
      component
    );
  }

  public void actionPerformed(final AnActionEvent e) {
    // 1. Show light bulb
    myManager.showIntentionHint();

    // 2. Commit possible non committed value and show popup
    final PropertyInspector propertyInspector = myManager.getEditor().getPropertyInspector();
    if(propertyInspector.isEditing()){
      propertyInspector.stopEditing();
    }
    myManager.showIntentionPopup();
  }

  public void update(AnActionEvent e) {
    // Alt-Enter hotkey for editor takes precedence over this action
    e.getPresentation().setEnabled(e.getDataContext().getData(DataConstants.EDITOR) == null);
  }
}
