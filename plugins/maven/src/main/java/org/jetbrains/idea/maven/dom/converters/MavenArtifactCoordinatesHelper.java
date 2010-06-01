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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.model.MavenId;

public class MavenArtifactCoordinatesHelper {
  public static MavenId getId(ConvertContext context) {
    return getId(getCoordinates(context));
  }

  @Nullable
  public static MavenDomShortArtifactCoordinates getCoordinates(ConvertContext context) {
    return (MavenDomShortArtifactCoordinates)context.getInvocationElement().getParent();
  }

  public static MavenId getId(MavenDomShortArtifactCoordinates coords) {
    String version = "";
    if (coords instanceof MavenDomArtifactCoordinates) {
      version = ((MavenDomArtifactCoordinates)coords).getVersion().getStringValue();
    }
    return new MavenId(coords.getGroupId().getStringValue(),
                       coords.getArtifactId().getStringValue(),
                       version);
  }
}
