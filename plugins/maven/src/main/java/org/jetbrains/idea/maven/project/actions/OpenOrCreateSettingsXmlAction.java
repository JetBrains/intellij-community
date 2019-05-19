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
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenFileTemplateGroupFactory;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class OpenOrCreateSettingsXmlAction extends MavenOpenOrCreateFilesAction {
  @Override
  protected List<File> getFiles(AnActionEvent e) {
    final MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(e.getDataContext());
    if(projectsManager == null) return Collections.emptyList();
    File file = projectsManager.getGeneralSettings().getEffectiveUserSettingsIoFile();
    return file != null ? Collections.singletonList(file) : Collections.emptyList();
  }

  @Override
  protected String getFileTemplate() {
    return MavenFileTemplateGroupFactory.MAVEN_SETTINGS_XML_TEMPLATE;
  }
}
