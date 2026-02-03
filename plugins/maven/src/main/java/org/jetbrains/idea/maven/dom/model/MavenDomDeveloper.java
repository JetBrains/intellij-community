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

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

/**
 * http://maven.apache.org/POM/4.0.0:Developer interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Developer documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomDeveloper extends MavenDomElement {

  /**
   * Returns the value of the id child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:id documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the id child.
   */
  @NotNull
  GenericDomValue<String> getId();

  /**
   * Returns the value of the name child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:name documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the name child.
   */
  @NotNull
  GenericDomValue<String> getName();

  /**
   * Returns the value of the email child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:email documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the email child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getEmail();

  /**
   * Returns the value of the url child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:url documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the url child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getUrl();

  /**
   * Returns the value of the organization child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:organization documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the organization child.
   */
  @NotNull
  GenericDomValue<String> getOrganization();

  /**
   * Returns the value of the organizationUrl child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:organizationUrl documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the organizationUrl child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getOrganizationUrl();

  /**
   * Returns the value of the roles child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:roles documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the roles child.
   */
  @NotNull
  MavenDomRoles getRoles();

  /**
   * Returns the value of the timezone child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:timezone documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the timezone child.
   */
  @NotNull
  GenericDomValue<String> getTimezone();

  /**
   * Returns the value of the properties child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:properties documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the properties child.
   */
  @NotNull
  MavenDomProperties getProperties();
}
