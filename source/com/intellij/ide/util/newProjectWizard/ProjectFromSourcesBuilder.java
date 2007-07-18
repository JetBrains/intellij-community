package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.importProject.ProjectLayout;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectFromSourcesBuilder extends ProjectBuilder {
  private List<Pair<String, String>> mySourcePaths = Collections.emptyList();
  private String myContentRootPath;
  private ProjectLayout myProjectLayout;

  public ProjectFromSourcesBuilder() {
  }

  public void setContentRootPath(final String contentRootPath) {
    myContentRootPath = contentRootPath;
  }

  public String getContentRootPath() {
    return myContentRootPath;
  }

  /**
   * @param paths list of pairs [SourcePath, PackagePrefix]
   */
  public void setSourcePaths(List<Pair<String,String>> paths) {
    mySourcePaths = paths;
  }

  public List<Pair<String, String>> getSourcePaths() {
    return mySourcePaths;
  }

  public void commit(final Project project) {
  }

  public void setProjectLayout(final ProjectLayout layout) {
    myProjectLayout = layout;
  }

  public ProjectLayout getProjectLayout() {
    return myProjectLayout;
  }
}
