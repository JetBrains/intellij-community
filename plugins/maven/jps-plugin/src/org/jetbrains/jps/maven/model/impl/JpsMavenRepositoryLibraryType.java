/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.maven.model.impl;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.maven.model.RepositoryLibraryDescriptor;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.serialization.library.JpsLibraryPropertiesSerializer;

public class JpsMavenRepositoryLibraryType extends JpsElementTypeBase<JpsSimpleElement<RepositoryLibraryDescriptor>>
  implements JpsLibraryType<JpsSimpleElement<RepositoryLibraryDescriptor>> {

  public static JpsMavenRepositoryLibraryType INSTANCE = new JpsMavenRepositoryLibraryType();
  private static final String MAVEN_ID_ATTRIBUTE = "maven-id";

  /** @noinspection MethodMayBeStatic*/
  public final String getTypeId() {
    return "repository";
  }

  public static JpsLibraryPropertiesSerializer<JpsSimpleElement<RepositoryLibraryDescriptor>> createPropertiesSerializer() {
    return new JpsLibraryPropertiesSerializer<JpsSimpleElement<RepositoryLibraryDescriptor>>(INSTANCE, INSTANCE.getTypeId()) {
      @Override
      public JpsSimpleElement<RepositoryLibraryDescriptor> loadProperties(@Nullable Element elem) {
        return JpsElementFactory.getInstance().createSimpleElement(new RepositoryLibraryDescriptor(
          elem != null? elem.getAttributeValue(MAVEN_ID_ATTRIBUTE, (String)null) : null
        ));
      }

      @Override
      public void saveProperties(JpsSimpleElement<RepositoryLibraryDescriptor> properties, Element element) {
        final String mavenId = properties.getData().getMavenId();
        if (mavenId != null) {
          element.setAttribute(MAVEN_ID_ATTRIBUTE, mavenId);
        }
      }
    };
  }
}
