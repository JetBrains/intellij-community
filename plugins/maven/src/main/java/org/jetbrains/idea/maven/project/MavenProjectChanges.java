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
  public static final MavenProjectChanges NONE = createNoneChanges();
  public static final MavenProjectChanges ALL = createAllChanges();
  public static final MavenProjectChanges DEPENDENCIES = createDependenciesChanges();

  /**
   * @deprecated Use MavenProjectChangesBuilder instead
   */
  @Deprecated
  MavenProjectChanges() {
  }

  /**
   * @deprecated Use the corresponding setter method or MavenProjectChangesBuilder instead
   */
  @Deprecated
  public boolean packaging;
  /**
   * @deprecated Use the corresponding setter method or MavenProjectChangesBuilder instead
   */
  @Deprecated
  public boolean output;
  /**
   * @deprecated Use the corresponding setter method or MavenProjectChangesBuilder instead
   */
  @Deprecated
  public boolean sources;
  /**
   * @deprecated Use the corresponding setter method or MavenProjectChangesBuilder instead
   */
  @Deprecated
  public boolean dependencies;
  /**
   * @deprecated Use the corresponding setter method or MavenProjectChangesBuilder instead
   */
  @Deprecated
  public boolean plugins;
  /**
   * @deprecated Use the corresponding setter method or MavenProjectChangesBuilder instead
   */
  @Deprecated
  public boolean properties;

  public boolean hasPackagingChanges() {
    return packaging;
  }

  public boolean hasOutputChanges() {
    return output;
  }

  public boolean hasSourceChanges() {
    return sources;
  }

  public boolean hasDependencyChanges() {
    return dependencies;
  }

  public boolean hasPluginsChanges() {
    return plugins;
  }

  public boolean hasPropertyChanges() {
    return properties;
  }

  public boolean hasChanges() {
    return hasPackagingChanges() ||
           hasOutputChanges() ||
           hasSourceChanges() ||
           hasDependencyChanges() ||
           hasPluginsChanges() ||
           hasPropertyChanges();
  }

  private static MavenProjectChanges createNoneChanges() {
    return new MavenProjectChangesBuilder();
  }

  private static MavenProjectChanges createAllChanges() {
    MavenProjectChangesBuilder result = new MavenProjectChangesBuilder();
    result.setAllChanges(true);
    return result;
  }

  private static MavenProjectChanges createDependenciesChanges() {
    MavenProjectChangesBuilder result = new MavenProjectChangesBuilder();
    result.setHasDependencyChanges(true);
    return result;
  }
}
