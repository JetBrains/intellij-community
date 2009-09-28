package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ModuleRootManager;

public class MavenDefaultModelsProvider implements MavenModelsProvider {
  private final Project myProject;

  public MavenDefaultModelsProvider(Project project) {
    myProject = project;
  }

  public Module[] getModules() {
    return ModuleManager.getInstance(myProject).getModules();
  }

  public VirtualFile[] getContentRoots(Module module) {
    return ModuleRootManager.getInstance(module).getContentRoots();
  }
}
