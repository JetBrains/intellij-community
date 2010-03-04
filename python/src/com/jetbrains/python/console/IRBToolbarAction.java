package com.jetbrains.python.console;

import com.intellij.openapi.actionSystem.AnAction;

import javax.swing.*;

/**
* @author oleg
*/
public abstract class IRBToolbarAction extends AnAction {
// This action will be called after adding this action to IRBToolBar
public abstract void initialize();

public IRBToolbarAction(String text, String description, Icon icon) {
  super(text, description, icon);
}
}
