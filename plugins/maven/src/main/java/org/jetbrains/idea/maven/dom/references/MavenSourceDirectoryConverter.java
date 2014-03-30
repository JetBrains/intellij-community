/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.references;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDomSoftAwareConverter;
import org.jetbrains.idea.maven.dom.model.MavenDomBuild;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

/**
 * @author Sergey Evdokimov
 */
public class MavenSourceDirectoryConverter extends MavenDirectoryPathReferenceConverter implements MavenDomSoftAwareConverter {
  @Override
  public boolean isSoft(@NotNull DomElement element) {
    DomElement buildElement = element.getParent();
    if (!(buildElement instanceof MavenDomBuild)) {
      return false;
    }

    DomElement mavenProject = buildElement.getParent();
    if (!(mavenProject instanceof MavenDomProjectModel)) {
      return false;
    }

    return "pom".equals(((MavenDomProjectModel)mavenProject).getPackaging().getStringValue());
  }
}
