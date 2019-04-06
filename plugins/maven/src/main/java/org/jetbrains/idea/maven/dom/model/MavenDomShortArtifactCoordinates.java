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
package org.jetbrains.idea.maven.dom.model;

import com.intellij.ide.presentation.Presentation;
import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesArtifactIdConverter;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesGroupIdConverter;
import org.jetbrains.idea.maven.dom.model.presentation.MavenArtifactCoordinatesPresentationProvider;


@Presentation(typeName = "Dependency", icon = "AllIcons.Nodes.PpLib", provider = MavenArtifactCoordinatesPresentationProvider.class)
public interface MavenDomShortArtifactCoordinates extends DomElement {
  @Required
  @NoSpellchecking
  @Convert(MavenArtifactCoordinatesGroupIdConverter.class)
  GenericDomValue<String> getGroupId();

  @Required
  @NoSpellchecking
  @Convert(MavenArtifactCoordinatesArtifactIdConverter.class)
  GenericDomValue<String> getArtifactId();
}
