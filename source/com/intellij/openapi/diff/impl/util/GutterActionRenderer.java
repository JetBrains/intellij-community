package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class GutterActionRenderer extends GutterIconRenderer {
  private final AnAction myAction;
  public static final Icon REPLACE_ARROW = IconLoader.getIcon("/diff/arrow.png");
  public static final Icon REMOVE_CROSS = IconLoader.getIcon("/diff/remove.png");

  public GutterActionRenderer(AnAction action) {
    myAction = action;
  }

  public Icon getIcon() { return myAction.getTemplatePresentation().getIcon(); }
  public AnAction getClickAction() { return myAction; }
  public String getTooltipText() { return myAction.getTemplatePresentation().getText(); }
  public boolean isNavigateAction() { return true; }
}
