package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

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
      Dependency dep = (Dependency)context.getInvocationElement().getParent();
      String groupId = dep.getGroupId().getStringValue();
      String artifactId = dep.getArtifactId().getStringValue();

      Project p = context.getModule().getProject();
      return getVariants(MavenIndicesManager.getInstance(p), groupId, artifactId);
    }
    catch (MavenIndexException e) {
      return Collections.emptyList();
    }
  }

  protected abstract Set<String> getVariants(MavenIndicesManager manager, String groupId, String artifactId) throws MavenIndexException;
}