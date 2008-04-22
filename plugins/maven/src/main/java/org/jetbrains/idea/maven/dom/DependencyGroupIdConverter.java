package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.repository.MavenRepositoryException;
import org.jetbrains.idea.maven.repository.MavenRepositoryManager;
import org.sonatype.nexus.index.ArtifactInfo;
import org.apache.lucene.search.MatchAllDocsQuery;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;

public class DependencyGroupIdConverter extends ResolvingConverter<String> {
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
      Collection<String> result = new ArrayList<String>();
      for (ArtifactInfo each : MavenRepositoryManager.getInstance(p).search(new MatchAllDocsQuery())) {
        result.add(each.groupId);
      }
      return result;
    }
    catch (MavenRepositoryException e) {
      return Collections.emptyList();
    }
  }
}
