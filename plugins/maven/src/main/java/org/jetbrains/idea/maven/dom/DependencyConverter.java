package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.apache.lucene.search.Query;
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

public abstract class DependencyConverter extends ResolvingConverter<String> {
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

      Query q = createQuery(group, artifact);
      Collection<ArtifactInfo> infos = MavenRepositoryManager.getInstance(p).search(q);

      Collection<String> result = new ArrayList<String>();
      for (ArtifactInfo each : infos) {
        result.add(getValueFrom(each));
      }

      return result;
    }
    catch (MavenRepositoryException e) {
      return Collections.emptyList();
    }
  }

  protected abstract Query createQuery(String group, String artifact);
  protected abstract String getValueFrom(ArtifactInfo i);
}