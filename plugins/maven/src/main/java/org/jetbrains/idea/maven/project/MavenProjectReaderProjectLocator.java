package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenId;

public interface MavenProjectReaderProjectLocator {
  VirtualFile findProjectFile(MavenId coordinates);
}
