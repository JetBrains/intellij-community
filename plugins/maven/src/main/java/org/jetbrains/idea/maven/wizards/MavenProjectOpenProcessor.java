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

package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.List;

public class MavenProjectOpenProcessor extends ProjectOpenProcessorBase<MavenProjectBuilder> {
  public MavenProjectOpenProcessor(@NotNull MavenProjectBuilder builder) {
    super(builder);
  }

  @Nullable
  public String[] getSupportedExtensions() {
    return MavenConstants.POM_NAMES;
  }

  @Override
  public boolean canOpenProject(VirtualFile file) {
    return super.canOpenProject(file) || MavenUtil.isPomFile(file);
  }

  public boolean doQuickImport(VirtualFile file, WizardContext wizardContext) {
    getBuilder().setFiles(Collections.singletonList(file));

    if (!getBuilder().setSelectedProfiles(MavenExplicitProfiles.NONE)) return false;

    List<MavenProject> projects = getBuilder().getList();
    if (projects.size() != 1) return false;

    getBuilder().setList(projects);
    wizardContext.setProjectName(getBuilder().getSuggestedProjectName());

    return true;
  }
}
