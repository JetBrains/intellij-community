package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

public interface MavenModelsProvider {
  Module[] getModules();

  VirtualFile[] getContentRoots(Module module);
}
