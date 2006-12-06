package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;

/**
 *  @author dsl
 */
public class ProjectLibraryTable extends LibraryTableBase implements ProjectComponent {
  private static final LibraryTablePresentation PROJECT_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    public String getDisplayName(boolean plural) {
      return ProjectBundle.message("project.library.display.name", plural ? 2 : 1);
    }

    public String getDescription() {
      return ProjectBundle.message("libraries.node.text.project");
    }

    public String getLibraryTableEditorTitle() {
      return ProjectBundle.message("library.configure.project.title");
    }
  };

  ProjectLibraryTable() {

  }
  public static LibraryTable getInstance(Project project) {
    return project.getComponent(LibraryTable.class);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getTableLevel() {
    return LibraryTablesRegistrar.PROJECT_LEVEL;
  }

  public LibraryTablePresentation getPresentation() {
    return PROJECT_LIBRARY_TABLE_PRESENTATION;
  }

  public boolean isEditable() {
    return true;
  }

}
