/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
public class LibraryConfigurable extends NamedConfigurable<Library> {
  private static final Icon ICON = IconLoader.getIcon("/modules/library.png");

  private LibraryTableEditor myLibraryEditor;
  private final Library myLibrary;
  private final LibraryTableModifiableModelProvider myModel;
  private final Project myProject;

  protected LibraryConfigurable(final LibraryTableModifiableModelProvider libraryTable,
                                final Library library,
                                final Project project,
                                final Runnable updateTree) {
    super(true, updateTree);
    myModel = libraryTable;
    myProject = project;
    myLibrary = library;
  }

  public JComponent createOptionsPanel() {
    myLibraryEditor = LibraryTableEditor.editLibrary(myModel, myLibrary, myProject);
    return myLibraryEditor.getComponent();
  }

  public boolean isModified() {
    return myLibraryEditor != null && myLibraryEditor.hasChanges();
  }

  public void apply() throws ConfigurationException {
    //do nothing
  }

  public void reset() {
    //do nothing
  }

  public void disposeUIResources() {
    if (myLibraryEditor != null) {
      myLibraryEditor.cancelChanges();
      Disposer.dispose(myLibraryEditor);
      myLibraryEditor = null;
    }
  }

  public void setDisplayName(final String name) {
    final LibraryEditor libraryEditor = ((LibrariesModifiableModel)myModel.getModifiableModel()).getLibraryEditor(myLibrary);
    libraryEditor.setName(name);
  }

  public Library getEditableObject() {
    return myLibrary;
  }

  public String getBannerSlogan() {
    final LibraryTable libraryTable = myLibrary.getTable();
    String libraryType = libraryTable == null
                         ? ProjectBundle.message("module.library.display.name", 1)
                         : libraryTable.getPresentation().getDisplayName(false);
    return ProjectBundle.message("project.roots.library.banner.text", getDisplayName(), libraryType);
  }

  public String getDisplayName() {
    final LibraryEditor libraryEditor = ((LibrariesModifiableModel)myModel.getModifiableModel()).getLibraryEditor(myLibrary);
    return libraryEditor.getName();
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "preferences.jdkGlobalLibs";  //todo
  }
}
