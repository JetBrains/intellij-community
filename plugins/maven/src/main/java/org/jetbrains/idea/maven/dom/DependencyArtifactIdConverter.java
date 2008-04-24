package org.jetbrains.idea.maven.dom;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.sonatype.nexus.index.ArtifactInfo;

public class DependencyArtifactIdConverter extends DependencyConverter {
  protected Query createQuery(String group, String artifact) {
    return new TermQuery(new Term(ArtifactInfo.GROUP_ID, group));
  }

  protected String getValueFrom(ArtifactInfo i) {
    return i.artifactId;
  }
}
