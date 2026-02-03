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

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Activation interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Activation documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomActivation extends MavenDomElement {

  /**
   * Returns the value of the activeByDefault child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:activeByDefault documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the activeByDefault child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<Boolean> getActiveByDefault();

  /**
   * Returns the value of the jdk child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:jdk documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the jdk child.
   */
  @NotNull
  GenericDomValue<String> getJdk();

  /**
   * Returns the value of the os child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:os documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the os child.
   */
  @NotNull
  MavenDomActivationOS getOs();

  /**
   * Returns the value of the property child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:property documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the property child.
   */
  @NotNull
  MavenDomActivationProperty getProperty();

  /**
   * Returns the value of the file child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:file documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the file child.
   */
  @NotNull
  MavenDomActivationFile getFile();
}
