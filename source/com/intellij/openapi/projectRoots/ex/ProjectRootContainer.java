/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 16, 2002
 * Time: 7:14:37 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.vfs.VirtualFile;

public interface ProjectRootContainer {
  VirtualFile[] getRootFiles(ProjectRootType type);
  ProjectRoot[] getRoots(ProjectRootType type);

  void startChange();
  void finishChange();

  ProjectRoot addRoot(VirtualFile virtualFile, ProjectRootType type);
  void addRoot(ProjectRoot root, ProjectRootType type);
  void removeRoot(ProjectRoot root, ProjectRootType type);
  void removeAllRoots(ProjectRootType type);

  void removeAllRoots();

  void removeRoot(VirtualFile root, ProjectRootType type);

  void update();
}
