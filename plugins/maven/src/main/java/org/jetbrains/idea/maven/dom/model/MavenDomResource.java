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

// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.openapi.paths.PathReference;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import com.intellij.util.xml.converters.PathReferenceConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Resource interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Resource documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomResource extends MavenDomElement {

  /**
   * Returns the value of the targetPath child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:targetPath documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the targetPath child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(value = PathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getTargetPath();

  /**
   * Returns the value of the filtering child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:filtering documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the filtering child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<Boolean> getFiltering();

  /**
   * Returns the value of the directory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:directory documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the directory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(value = PathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getDirectory();

  /**
   * Returns the value of the includes child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:includes documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the includes child.
   */
  @NotNull
  MavenDomIncludes getIncludes();

  /**
   * Returns the value of the excludes child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:excludes documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the excludes child.
   */
  @NotNull
  MavenDomExcludes getExcludes();
}
