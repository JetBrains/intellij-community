package org.jetbrains.idea.maven.indices;

import org.sonatype.nexus.index.ArtifactInfo;

import java.util.List;
import java.util.ArrayList;

public class MavenArtifactSearchResult {
  public List<ArtifactInfo> versions = new ArrayList<ArtifactInfo>();
}
