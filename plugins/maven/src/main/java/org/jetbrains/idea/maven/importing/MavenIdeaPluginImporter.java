// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
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
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @author Sergey Evdokimov
 */
public class MavenIdeaPluginImporter extends MavenImporter {
  public MavenIdeaPluginImporter() {
    super("com.googlecode", "maven-idea-plugin");
  }

  @Override
  public void postProcess(Module module,
                          MavenProject mavenProject,
                          MavenProjectChanges changes,
                          IdeModifiableModelsProvider modifiableModelsProvider) {
    configure(mavenProject, module.getProject(), module);
  }


  void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @NotNull Module module) {
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
      ((ProjectViewImpl)ProjectView.getInstance(project)).setAutoscrollToSource(Boolean.parseBoolean(autoscrollToSource),
                                                                                ProjectViewPane.ID);
    }

    String autoscrollFromSource = cfg.getChildTextTrim("autoscrollFromSource");
    if (!StringUtil.isEmptyOrSpaces(autoscrollFromSource)) {
      ((ProjectViewImpl)ProjectView.getInstance(project)).setAutoscrollFromSource(Boolean.parseBoolean(autoscrollFromSource),
                                                                                  ProjectViewPane.ID);
    }

    String hideEmptyPackages = cfg.getChildTextTrim("hideEmptyPackages");
    if (!StringUtil.isEmptyOrSpaces(hideEmptyPackages)) {
      ProjectView.getInstance(project).setHideEmptyPackages(ProjectViewPane.ID, Boolean.parseBoolean(hideEmptyPackages));
    }

    String optimizeImportsBeforeCommit = cfg.getChildTextTrim("optimizeImportsBeforeCommit");
    if (!StringUtil.isEmptyOrSpaces(optimizeImportsBeforeCommit)) {
      VcsConfiguration.getInstance(module.getProject()).OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT =
        Boolean.parseBoolean(optimizeImportsBeforeCommit);
    }

    String performCodeAnalisisBeforeCommit = cfg.getChildTextTrim("performCodeAnalisisBeforeCommit");
    if (!StringUtil.isEmptyOrSpaces(performCodeAnalisisBeforeCommit)) {
      VcsConfiguration.getInstance(module.getProject()).CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT =
        Boolean.parseBoolean(performCodeAnalisisBeforeCommit);
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

      if (ApplicationManager.getApplication().isDispatchThread()) {
        WriteAction.run(() ->
                          model.commit()
        );
      }
      else {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          WriteAction.run(() ->
                            model.commit()
          );
        });
      }
    }
  }
}
