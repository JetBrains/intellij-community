package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.beans.Dependency;
import org.jetbrains.idea.maven.repository.MavenRepositoryException;
import org.jetbrains.idea.maven.repository.MavenRepositoryManager;
import org.sonatype.nexus.index.ArtifactInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class DependencyVersionConverter extends ResolvingConverter<String> {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return getVariants(context).contains(s) ? s : null;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    try {
      Project p = context.getModule().getProject();

      Dependency dep = (Dependency)context.getInvocationElement().getParent();
      String group = dep.getGroupId().getStringValue();
      String artifact = dep.getArtifactId().getStringValue();

      BooleanQuery q = new BooleanQuery();
      q.add(new TermQuery(new Term(ArtifactInfo.GROUP_ID, group)), BooleanClause.Occur.MUST);
      q.add(new TermQuery(new Term(ArtifactInfo.ARTIFACT_ID, artifact)), BooleanClause.Occur.MUST);

      Collection<String> result = new ArrayList<String>();
      for (ArtifactInfo each : MavenRepositoryManager.getInstance(p).search(q)) {
        result.add(each.version);
      }
      return result;
    }
    catch (MavenRepositoryException e) {
      return Collections.emptyList();
    }
  }
}