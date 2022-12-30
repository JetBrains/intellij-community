// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.references.MavenModulePsiReference;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.nio.file.Paths;

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
  @Nullable
  private static PsiReference getPomTagReferenceToDirectory(@Nullable XmlTag tag,
                                                            @NotNull VirtualFile pomFile,
                                                            @NotNull VirtualFile directory) {
    PsiReference reference = null;
    if (null != tag) {
      var oldDirectoryPath = Paths.get(directory.getPath()).normalize();
      var referencedDirectoryPath = Paths.get(pomFile.getParent().getPath(), tag.getValue().getText()).normalize();
      if (referencedDirectoryPath.equals(oldDirectoryPath)) {
        if (tag instanceof ASTNode node) {
          var textTag = node.findChildByType(XmlElementType.XML_TEXT);
          if (null != textTag) {
            var oldDirectoryName = directory.getName();
            var offset = textTag.getStartOffsetInParent();
            var from = offset + tag.getValue().getText().lastIndexOf(oldDirectoryName);
            reference = new MavenModulePsiReference(tag, tag.getText(), new TextRange(from, from + oldDirectoryName.length()));
          }
        }
      }
    }
    return reference;
  }

  private static void processModule(@NotNull Project project,
                                    @NotNull Module module,
                                    @NotNull PsiDirectory directory,
                                    @NotNull Processor<? super PsiReference> consumer) {
    var projectsManager = MavenProjectsManager.getInstance(project);
    if (!projectsManager.isMavenizedModule(module)) return;
    var mavenProject = projectsManager.findProject(module);
    if (null == mavenProject) return;
    var pomFile = mavenProject.getFile();
    var mavenModel = MavenDomUtil.getMavenDomProjectModel(project, pomFile);
    if (null == mavenModel) return;
    var mavenModules = mavenModel.getModules().getModules();
    for (var mavenModule : mavenModules) {
      var moduleTag = mavenModule.getXmlTag();
      var reference = getPomTagReferenceToDirectory(moduleTag, pomFile, directory.getVirtualFile());
      if (null != reference) {
        consumer.process(reference);
      }
    }
  }

  @Override
  public void processQuery(ReferencesSearch.@NotNull SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    if (queryParameters.getElementToSearch() instanceof PsiDirectory directory) {
      var project = queryParameters.getProject();
      var modules = ModuleManager.getInstance(project).getModules();
      for (var module : modules) {
        processModule(project, module, directory, consumer);
      }
    }
  }
}
