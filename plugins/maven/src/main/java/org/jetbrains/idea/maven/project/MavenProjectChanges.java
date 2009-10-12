/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
