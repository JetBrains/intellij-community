package com.intellij.openapi.roots.ui.configuration.packaging;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
*/
public abstract class AddPackagingElementAction {
  private String myText;
  private Icon myIcon;

  protected AddPackagingElementAction(final String text, final Icon icon) {
    myText = text;
    myIcon = icon;
  }

  public String getText() {
    return myText;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public abstract boolean isEnabled(@NotNull PackagingEditor editor);

  public abstract void perform(final PackagingEditor packagingEditor);
}
