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
package org.jetbrains.idea.maven.importing.configurers;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @author Sergey Evdokimov
 */
public class MavenIdeaPluginConfigurer extends MavenModuleConfigurer {
  @Override
  public void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @Nullable Module module) {
    if (module == null) return;

    Element cfg = mavenProject.getPluginConfiguration("com.googlecode", "maven-idea-plugin");
    if (cfg == null) return;

    configureJdk(cfg, module);

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

    String downloadSources = cfg.getChildTextTrim("downloadSources");
    if (!StringUtil.isEmptyOrSpaces(downloadSources)) {
      projectsManager.getImportingSettings().setDownloadSourcesAutomatically(Boolean.parseBoolean(downloadSources));
    }

    String downloadJavadocs = cfg.getChildTextTrim("downloadJavadocs");
    if (!StringUtil.isEmptyOrSpaces(downloadJavadocs)) {
      projectsManager.getImportingSettings().setDownloadDocsAutomatically(Boolean.parseBoolean(downloadJavadocs));
    }

    String assertNotNull = cfg.getChildTextTrim("assertNotNull");
    if (!StringUtil.isEmptyOrSpaces(assertNotNull)) {
      CompilerConfiguration.getInstance(project).setAddNotNullAssertions(Boolean.parseBoolean(assertNotNull));
    }

    String autoscrollToSource = cfg.getChildTextTrim("autoscrollToSource");
    if (!StringUtil.isEmptyOrSpaces(autoscrollToSource)) {
      ((ProjectViewImpl)ProjectView.getInstance(project)).setAutoscrollToSource(Boolean.parseBoolean(autoscrollToSource), ProjectViewPane.ID);
    }

    String autoscrollFromSource = cfg.getChildTextTrim("autoscrollFromSource");
    if (!StringUtil.isEmptyOrSpaces(autoscrollFromSource)) {
      ((ProjectViewImpl)ProjectView.getInstance(project)).setAutoscrollFromSource(Boolean.parseBoolean(autoscrollFromSource), ProjectViewPane.ID);
    }

    String hideEmptyPackages = cfg.getChildTextTrim("hideEmptyPackages");
    if (!StringUtil.isEmptyOrSpaces(hideEmptyPackages)) {
      ProjectView.getInstance(project).setHideEmptyPackages(Boolean.parseBoolean(hideEmptyPackages), ProjectViewPane.ID);
    }

    String optimizeImportsBeforeCommit = cfg.getChildTextTrim("optimizeImportsBeforeCommit");
    if (!StringUtil.isEmptyOrSpaces(optimizeImportsBeforeCommit)) {
      VcsConfiguration.getInstance(module.getProject()).OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = Boolean.parseBoolean(optimizeImportsBeforeCommit);
    }

    String performCodeAnalisisBeforeCommit = cfg.getChildTextTrim("performCodeAnalisisBeforeCommit");
    if (!StringUtil.isEmptyOrSpaces(performCodeAnalisisBeforeCommit)) {
      VcsConfiguration.getInstance(module.getProject()).CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = Boolean.parseBoolean(performCodeAnalisisBeforeCommit);
    }

    String reformatCodeBeforeCommit = cfg.getChildTextTrim("reformatCodeBeforeCommit");
    if (!StringUtil.isEmptyOrSpaces(reformatCodeBeforeCommit)) {
      VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(module.getProject());
      vcsConfiguration.REFORMAT_BEFORE_PROJECT_COMMIT = Boolean.parseBoolean(reformatCodeBeforeCommit);
    }
  }

  private static void configureJdk(Element cfg, @NotNull Module module) {
    String jdkName = cfg.getChildTextTrim("jdkName");
    if (StringUtil.isEmptyOrSpaces(jdkName)) return;

    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

    String currentSdkName = null;
    Sdk sdk = rootManager.getSdk();
    if (sdk != null) {
      currentSdkName = sdk.getName();
    }

    if (!jdkName.equals(currentSdkName)) {
      ModifiableRootModel model = rootManager.getModifiableModel();

      if (jdkName.equals(ProjectRootManager.getInstance(model.getProject()).getProjectSdkName())) {
        model.inheritSdk();
      }
      else {
        Sdk jdk = ProjectJdkTable.getInstance().findJdk(jdkName);
        if (jdk != null) {
          model.setSdk(jdk);
        }
        else {
          model.setInvalidSdk(jdkName, JavaSdk.getInstance().getName());
        }
      }

      model.commit();
    }
  }
}
