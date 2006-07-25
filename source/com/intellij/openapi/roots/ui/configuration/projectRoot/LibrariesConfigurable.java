/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class LibrariesConfigurable implements NamedConfigurable <String> {
  private static final Icon ICON = IconLoader.getIcon("/modules/libraries.png");

  private String myLibraryTable;
  private LibrariesModifiableModel myModel;

  private PanelWithText myPanel = new PanelWithText();

  protected LibrariesConfigurable(final String libraryTable, final LibrariesModifiableModel model) {
    myLibraryTable = libraryTable;
    myModel = model;
  }

  public void reset() {
    // nothing to implement
  }

  public final void apply() throws ConfigurationException {
  }

  public final void disposeUIResources() {
  }

  public final boolean isModified() {
    return false;
  }

  public final JComponent createComponent() {
    final int choice = getChoice();
    myPanel.setText(choice == 1
                    ? ProjectBundle.message("libraries.node.text.ide")
                    : choice == 2
                      ? ProjectBundle.message("libraries.node.text.application.server")
                      : ProjectBundle.message("libraries.node.text.project"));
    return myPanel;
  }


  public String getDisplayName() {
    return ProjectBundle.message("libraries.node.display.name", getChoice());
  }

  private int getChoice() {
    return Comparing.strEqual(myLibraryTable, LibraryTablesRegistrar.APPLICATION_LEVEL)
                 ? 1
                 : Comparing.strEqual(myLibraryTable, ApplicationServersManager.APPLICATION_SERVER_MODULE_LIBRARIES) ? 2 : 3;
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
    return myLibraryTable;
  }

  public String getBannerSlogan() {
    return getDisplayName();
  }


  public AnAction createAddAction(final Project project){
    return new AnAction(){
      public void actionPerformed(AnActionEvent e) {
        final LibraryTableEditor editor = LibraryTableEditor.editLibraryTable(new LibraryTableModifiableModelProvider() {
          public LibraryTable.ModifiableModel getModifiableModel() {
            return myModel;
          }

          public String getTableLevel() {
            return myLibraryTable;
          }
        }, project);
        editor.createAddLibraryAction(true, myPanel).actionPerformed(null);
        Disposer.dispose(editor);
      }
    };
  }

}
