/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class LibrariesConfigurable extends NamedConfigurable <String> {
  private static final Icon ICON = IconLoader.getIcon("/modules/library.png");
  
  private LibraryTable myLibraryTable;

  private final PanelWithText myPanel = new PanelWithText();

  protected LibrariesConfigurable(final LibraryTable libraryTable) {
    myLibraryTable = libraryTable;
  }

  public void reset() {
    // nothing to implement
  }

  public final void apply() throws ConfigurationException {
  }

  public final void disposeUIResources() {
    myLibraryTable = null;
  }

  public final boolean isModified() {
    return false;
  }


  public JComponent createOptionsPanel() {
    myPanel.setText(myLibraryTable.getPresentation().getDescription());
    return myPanel;
  }


  public String getDisplayName() {
    return myLibraryTable.getPresentation().getDisplayName(true);
  }

  public String getHelpTopic() {
    return "preferences.jdkGlobalLibs";
  }

  public Icon getIcon() {
    return ICON;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public String getEditableObject() {
    return myLibraryTable.getTableLevel();
  }

  public String getBannerSlogan() {
    return getDisplayName();
  }

}
