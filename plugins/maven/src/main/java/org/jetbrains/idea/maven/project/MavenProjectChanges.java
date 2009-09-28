package org.jetbrains.idea.maven.project;

public class MavenProjectChanges {
  public static final MavenProjectChanges NONE = new MavenProjectChanges();
  public static final MavenProjectChanges ALL = createAllChanges();
  public static final MavenProjectChanges DEPENDENCIES = createDependenciesChanges();

  public boolean packaging;
  public boolean output;
  public boolean sources;
  public boolean dependencies;
  public boolean plugins;

  private static MavenProjectChanges createAllChanges() {
    MavenProjectChanges result = new MavenProjectChanges();
    result.packaging = true;
    result.output = true;
    result.sources = true;
    result.dependencies = true;
    result.plugins = true;
    return result;
  }

  private static MavenProjectChanges createDependenciesChanges() {
    MavenProjectChanges result = new MavenProjectChanges();
    result.dependencies = true;
    return result;
  }

  public MavenProjectChanges mergedWith(MavenProjectChanges other) {
    if (other == null) return this;

    MavenProjectChanges result = new MavenProjectChanges();
    result.packaging = packaging | other.packaging;
    result.output = output | other.output;
    result.sources = sources | other.sources;
    result.dependencies = dependencies | other.dependencies;
    result.plugins = plugins | other.plugins;
    return result;
  }

  public boolean hasChanges() {
    return packaging || output || sources || dependencies || plugins;
  }
}
