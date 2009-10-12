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
package org.jetbrains.idea.maven.dom.annotator;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;

public class MavenDomAnnotator implements DomElementsAnnotator {
  public void annotate(DomElement element, DomElementAnnotationHolder holder) {
    //Project project = element.getManager().getProject();
    //MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    //if (element instanceof MavenDomProjectModel) {
    //  String groupId = ((MavenDomProjectModel)element).getGroupId().getValue();
    //}
  }
}
