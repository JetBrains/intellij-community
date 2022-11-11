package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.UserDataHolderBase;

/**
 * @author Eugene.Kudelevsky
 */
public class ResolveContext extends UserDataHolderBase {
  private final MavenProjectsTree myTree;

  public ResolveContext(MavenProjectsTree tree) {
    myTree = tree;
  }

  /**
   * @return new project tree for the current import
   */
  public MavenProjectsTree getMavenProjectsTree() {
    return myTree;
  }
}
