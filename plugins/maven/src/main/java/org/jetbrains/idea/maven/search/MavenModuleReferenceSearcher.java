// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
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

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
final class MavenModuleReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  private static final String DELIMITER = "/";

  // split with lookaheads and lookbehinds to keep the delimiters
  private static final String DELIMITER_REGEX = "((?<=%1$s)|(?=%1$s))".formatted(DELIMITER);

  private static @NotNull List<PsiReference> getPomTagReferencesToDirectory(@Nullable XmlTag tag,
                                                                            @NotNull VirtualFile pomFile,
                                                                            @NotNull VirtualFile directory) {
    try {
      return doGetPomTagReferencesToDirectory(tag, pomFile, directory);
    } catch (InvalidPathException ignored) {
      return List.of();
    }
  }

  private static @NotNull List<PsiReference> doGetPomTagReferencesToDirectory(@Nullable XmlTag tag,
                                                                              @NotNull VirtualFile pomFile,
                                                                              @NotNull VirtualFile directory) {
    var references = new ArrayList<PsiReference>();
    if (null != tag) {
      var oldDirectoryPath = Paths.get(directory.getPath()).normalize();
      var modulePath = tag.getValue().getTrimmedText();
      var tmpPath = Paths.get(pomFile.getParent().getPath()).normalize();
      var referencedDirectoryPath = Paths.get(pomFile.getParent().getPath(), modulePath).normalize();
      if (referencedDirectoryPath.startsWith(oldDirectoryPath)) {
        if (tag instanceof ASTNode node) {
          var textTag = node.findChildByType(XmlElementType.XML_TEXT);
          if (null != textTag) {
            var from = textTag.getStartOffsetInParent();
            var length = directory.getName().length();
            var items = modulePath.split(DELIMITER_REGEX);
            for (String item : items) {
              tmpPath = Paths.get(tmpPath.toString(), item).normalize();
              if (!DELIMITER.equals(item) && tmpPath.equals(oldDirectoryPath)) {
                references.add(new MavenModulePsiReference(tag, tag.getText(), new TextRange(from, from + length)));
              }
              from += item.length();
            }
          }
        }
      }
    }
    return references;
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
    var mavenModel = ReadAction.compute(() -> MavenDomUtil.getMavenDomProjectModel(project, pomFile));
    if (null == mavenModel) return;
    var references = ReadAction.compute(() -> mavenModel.getModules().getModules().stream()
      .flatMap(mavenModule -> getPomTagReferencesToDirectory(mavenModule.getXmlTag(), pomFile, directory.getVirtualFile()).stream())
      .toList());
    for (var reference : references) {
      consumer.process(reference);
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
