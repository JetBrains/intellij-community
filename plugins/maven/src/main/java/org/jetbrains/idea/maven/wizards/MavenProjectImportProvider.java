/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.maven.wizards;

import com.intellij.CommonBundle;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;

public class MavenProjectImportProvider extends ProjectImportProvider {
  public MavenProjectImportProvider(final MavenProjectBuilder builder) {
    super(builder);
  }

  public ModuleWizardStep[] createSteps(final WizardContext wizardContext) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    return new ModuleWizardStep[]{
      new MavenProjectImportStep(wizardContext),
      new SelectProfilesStep(wizardContext),
      new SelectImportedProjectsStep<MavenProject>(wizardContext) {
        @Override
        protected String getElementText(final MavenProject project) {
          final StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append(project.getMavenId());
          VirtualFile root = ((MavenProjectBuilder)getBuilder()).getRootDirectory();
          if (root != null) {
            final String relPath = VfsUtilCore.getRelativePath(project.getDirectoryFile(), root, File.separatorChar);
            if (StringUtil.isNotEmpty(relPath)) {
              stringBuilder.append(" [").append(relPath).append("]");
            }
          }

          if (!isElementEnabled(project)) {
            stringBuilder.append(" (project is ignored. See ").append(CommonBundle.settingsActionPath()).append(" | Maven | Ignored Files)");
          }

          return stringBuilder.toString();
        }

        @Override
        protected boolean isElementEnabled(MavenProject mavenProject) {
          Project project = wizardContext.getProject();
          if (project == null) return true;

          return !MavenProjectsManager.getInstance(project).isIgnored(mavenProject);
        }

        @Override
        public void updateDataModel() {
          super.updateDataModel();
          getWizardContext().setProjectName(((MavenProjectBuilder)getBuilder()).getSuggestedProjectName());
        }

        @Override
        public String getHelpId() {
          return "reference.dialogs.new.project.import.maven.page3";
        }
      },
      stepFactory.createProjectJdkStep(wizardContext),
      stepFactory.createNameAndLocationStep(wizardContext)
    };
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return "pom.xml".equals(file.getName());
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "<b>Maven</b> project file (pom.xml)";
  }
}