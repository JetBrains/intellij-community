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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenFileTemplateGroupFactory;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OpenOrCreateProfilesXmlAction extends MavenOpenOrCreateFilesAction {
  protected List<File> getFiles(AnActionEvent e) {
    List<File> result = new ArrayList<>();
    for (MavenProject each : MavenActionUtil.getMavenProjects(e.getDataContext())) {
      result.add(each.getProfilesXmlIoFile());
    }
    return result;
  }

  @Override
  protected String getFileTemplate() {
    return MavenFileTemplateGroupFactory.MAVEN_PROFILES_XML_TEMPLATE;
  }
}