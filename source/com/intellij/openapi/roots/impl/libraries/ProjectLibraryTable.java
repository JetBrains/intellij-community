package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;

/**
 *  @author dsl
 */
public class ProjectLibraryTable extends LibraryTableBase implements ProjectComponent {
  ProjectLibraryTable (Project project) {

  }
  public static LibraryTable getInstance(Project project) {
    return project.getComponent(LibraryTable.class);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getTableLevel() {
    return LibraryTablesRegistrar.PROJECT_LEVEL;
  }
}
