package org.jetbrains.idea.maven.dom;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.sonatype.nexus.index.ArtifactInfo;

public class DependencyVersionConverter extends DependencyConverter {
  protected BooleanQuery createQuery(String group, String artifact) {
    BooleanQuery q = new BooleanQuery();
    q.add(new TermQuery(new Term(ArtifactInfo.GROUP_ID, group)), BooleanClause.Occur.MUST);
    q.add(new TermQuery(new Term(ArtifactInfo.ARTIFACT_ID, artifact)), BooleanClause.Occur.MUST);
    return q;
  }

  protected String getValueFrom(ArtifactInfo i) {
    return i.version;
  }

}