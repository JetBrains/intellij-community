// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;

public interface MavenDomRepository extends MavenDomRepositoryBase {
  @NotNull
  MavenDomRepositoryPolicy getReleases();

  @NotNull
  MavenDomRepositoryPolicy getSnapshots();
}
