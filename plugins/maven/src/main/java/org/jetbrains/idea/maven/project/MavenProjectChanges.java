package org.jetbrains.idea.maven.project;

public class MavenProjectChanges {
  public boolean packaging;
  public boolean output;
  public boolean sources;
  public boolean dependencies;
  public boolean plugins;

  public void add(MavenProjectChanges other) {
    packaging &= other.packaging;
    output &= other.output;
    sources &= other.sources;
    dependencies &= other.dependencies;
    plugins &= other.plugins;
  }
}
