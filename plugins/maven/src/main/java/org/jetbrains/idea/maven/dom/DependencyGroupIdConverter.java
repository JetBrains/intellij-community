package org.jetbrains.idea.maven.dom;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.sonatype.nexus.index.ArtifactInfo;

public class DependencyGroupIdConverter extends DependencyConverter {
  protected Query createQuery(String group, String artifact) {
    return new MatchAllDocsQuery();
  }

  protected String getValueFrom(ArtifactInfo i) {
    return i.groupId;
  }
}
