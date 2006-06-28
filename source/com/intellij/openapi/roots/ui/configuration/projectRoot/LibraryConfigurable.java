/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
public class LibraryConfigurable implements NamedConfigurable<Library> {
  private static final Icon ICON = IconLoader.getIcon("/modules/libraries.png");

  private LibraryTableEditor myLibraryEditor;
  private Library myLibrary;
  private String myLibraryName;
  private LibraryTableModifiableModelProvider myModel;
  private Project myProject;

  protected LibraryConfigurable(final LibraryTableModifiableModelProvider libraryTable,
                                final Library library,
                                final String libraryName,
                                final Project project) {
    this(libraryTable, library, project);
    myLibraryName = libraryName;
  }

  protected LibraryConfigurable(final LibraryTableModifiableModelProvider libraryTable,
                                final Library library,
                                final Project project) {
    myModel = libraryTable;
    myProject = project;
    myLibrary = library;
    myLibraryName = myLibrary.getName();
  }

  public JComponent createComponent() {
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
      myLibraryEditor = null;
    }
  }

  public void setDisplayName(final String name) {
    if (myLibrary.getTable() != null){
      final LibraryEditor libraryEditor
        = ((LibrariesModifiableModel)myModel.getModifiableModel()).getLibraryEditor(myLibrary);
      libraryEditor.setName(name);
      myLibraryName = name;
    }
  }

  public Library getEditableObject() {
    return myLibrary;
  }

  public String getBannerSlogan() {
    return myLibraryName;
  }

  public String getDisplayName() {
    return myLibraryName;
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "preferences.jdkGlobalLibs";  //todo
  }

  public AnAction createAddAction() {
    return new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        final LibraryTableEditor editor = LibraryTableEditor.editLibraryTable(myModel, myProject);
        editor.createAddLibraryAction(true, myLibraryEditor.getComponent()).actionPerformed(null);
        Disposer.dispose(editor);
      }
    };
  }
}
