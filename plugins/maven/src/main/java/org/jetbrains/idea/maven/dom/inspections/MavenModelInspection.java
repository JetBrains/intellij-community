/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.converters.MavenDomSoftAwareConverter;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenModelInspection extends BasicDomElementsInspection<MavenDomProjectModel> {
  public MavenModelInspection() {
    super(MavenDomProjectModel.class);
  }

  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @NotNull
  public String getDisplayName() {
    return MavenDomBundle.message("inspection.name");
  }

  @NotNull
  public String getShortName() {
    return "MavenModelInspection";
  }

  private static boolean isElementInsideManagedFile(GenericDomValue value) {
    VirtualFile virtualFile = DomUtil.getFile(value).getVirtualFile();
    if (virtualFile == null) {
      return false;
    }

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(value.getManager().getProject());

    return projectsManager.findProject(virtualFile) != null;
  }

  @Override
  protected boolean shouldCheckResolveProblems(GenericDomValue value) {
    if (!isElementInsideManagedFile(value)) {
      return false;
    }

    Converter converter = value.getConverter();
    if (converter instanceof MavenDomSoftAwareConverter) {
      return !((MavenDomSoftAwareConverter)converter).isSoft(value);
    }

    return true;
  }
}