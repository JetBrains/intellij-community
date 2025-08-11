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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.references.MavenDirectoryPathReferenceConverter;
import org.jetbrains.idea.maven.dom.references.MavenSourceDirectoryConverter;

/**
 * http://maven.apache.org/POM/4.0.0:Build interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Build documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomBuild extends MavenDomBuildBase {

  /**
   * Returns the value of the sourceDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:sourceDirectory documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the sourceDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(value = MavenSourceDirectoryConverter.class, soft = false)
  GenericDomValue<PathReference> getSourceDirectory();

  /**
   * Returns the value of the scriptSourceDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:scriptSourceDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the scriptSourceDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(value = MavenSourceDirectoryConverter.class, soft = false)
  GenericDomValue<PathReference> getScriptSourceDirectory();

  /**
   * Returns the value of the testSourceDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:testSourceDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the testSourceDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(value = MavenSourceDirectoryConverter.class, soft = false)
  GenericDomValue<PathReference> getTestSourceDirectory();

  /**
   * Returns the value of the outputDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:outputDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the outputDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(value = MavenDirectoryPathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getOutputDirectory();

  /**
   * Returns the value of the testOutputDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:testOutputDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the testOutputDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(value = MavenDirectoryPathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getTestOutputDirectory();

  /**
   * Returns the value of the extensions child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:extensions documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the extensions child.
   */
  @NotNull
  MavenDomExtensions getExtensions();



  @NotNull
  MavenDomSources getSources();

}
