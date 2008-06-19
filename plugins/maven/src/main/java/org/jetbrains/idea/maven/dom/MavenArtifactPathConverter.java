package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;

import java.util.Set;
import java.util.Collections;

public class MavenArtifactPathConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(MavenIndicesManager manager, MavenId id, String specifiedPath) throws MavenIndexException {
    return LocalFileSystem.getInstance().findFileByPath(specifiedPath) != null;
  }

  @Override
  protected Set<String> getVariants(MavenIndicesManager manager, MavenId id) throws MavenIndexException {
    return Collections.emptySet();
  }
}