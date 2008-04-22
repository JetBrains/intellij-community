package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.repository.MavenRepositoryException;
import org.jetbrains.idea.maven.repository.MavenRepositoryManager;

public class DependencyArtifactIdConverter extends Converter<String> {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    Project p = context.getModule().getProject();
    try {
      if (MavenRepositoryManager.getInstance(p).findByArtifactId(s).isEmpty()) return null;
      return s;
    }
    catch (MavenRepositoryException e) {
      return null;
    }
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}