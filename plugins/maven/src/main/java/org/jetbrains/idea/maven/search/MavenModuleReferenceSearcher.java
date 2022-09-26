// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.references.MavenModulePsiReference;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * Searches for module references in maven pom files to be renamed in "Rename directory" refactoring
 * <pre>
 *   {@code
 *   <modules>
 *     <module>my_module_name</module>
 *   </modules>
 *   }
 * </pre>
 */
public class MavenModuleReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  private static void processModule(@NotNull Project project,
                             @NotNull Module module,
                             @NotNull String searchString,
                             @NotNull Processor<? super PsiReference> consumer) {
    var projectsManager = MavenProjectsManager.getInstance(project);
    if (!projectsManager.isMavenizedModule(module)) return;
    var mavenProject = projectsManager.findProject(module);
    if (null == mavenProject) return;
    var mavenModel = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (null == mavenModel) return;
    var mavenModules = mavenModel.getModules().getModules();
    for (var mavenModule : mavenModules) {
      var moduleTag = mavenModule.getXmlTag();
      if (null != moduleTag && moduleTag.getValue().getText().contains(searchString)) {
        var from = moduleTag.getText().indexOf(searchString);
        var reference = new MavenModulePsiReference(moduleTag, moduleTag.getText(), new TextRange(from, from + searchString.length()));
        consumer.process(reference);
      }
    }
  }

  @Override
  public void processQuery(ReferencesSearch.@NotNull SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    if (queryParameters.getElementToSearch() instanceof PsiDirectory directory) {
      var project = queryParameters.getProject();
      var modules = ModuleManager.getInstance(project).getModules();
      var searchString = directory.getName();
      for (var module : modules) {
        processModule(project, module, searchString, consumer);
      }
    }
  }
}
