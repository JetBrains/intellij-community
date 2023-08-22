// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectBundle;

import java.util.*;

import static org.jetbrains.idea.maven.dom.MavenDomUtil.getProjectName;

public class MavenDuplicateDependenciesInspection extends DomElementsInspection<MavenDomProjectModel> {
  public MavenDuplicateDependenciesInspection() {
    super(MavenDomProjectModel.class);
  }

  @Override
  public void checkFileElement(@NotNull DomFileElement<MavenDomProjectModel> domFileElement,
                               @NotNull DomElementAnnotationHolder holder) {
    MavenDomProjectModel projectModel = domFileElement.getRootElement();

    checkManagedDependencies(projectModel, holder);
    checkDependencies(projectModel, holder);
  }

  private static void checkDependencies(@NotNull MavenDomProjectModel projectModel,
                                        @NotNull DomElementAnnotationHolder holder) {
    MultiMap<DependencyConflictId, MavenDomDependency> allDuplicates = getDuplicateDependenciesMap(projectModel);

    for (MavenDomDependency dependency : projectModel.getDependencies().getDependencies()) {
      DependencyConflictId id = DependencyConflictId.create(dependency);
      if (id != null) {
        Collection<MavenDomDependency> dependencies = allDuplicates.get(id);
        if (dependencies.size() > 1) {

          List<MavenDomDependency> duplicatedDependencies = new ArrayList<>();

          for (MavenDomDependency d : dependencies) {
            if (d == dependency) continue;

            if (d.getParent() == dependency.getParent()) {
              duplicatedDependencies.add(d); // Dependencies in the same file must be unique by groupId:artifactId:type:classifier
            }
            else {
              if (scope(d).equals(scope(dependency))
                  && Objects.equals(d.getVersion().getStringValue(), dependency.getVersion().getStringValue())) {
                duplicatedDependencies.add(d); // Dependencies in different files must not have same groupId:artifactId:VERSION:type:classifier:SCOPE
              }
            }
          }

          if (duplicatedDependencies.size() > 0) {
            addProblem(dependency, duplicatedDependencies, holder);
          }
        }
      }
    }
  }

  private static String scope(MavenDomDependency dependency) {
    String res = dependency.getScope().getRawText();
    if (StringUtil.isEmpty(res)) return "compile";

    return res;
  }

  private static void addProblem(@NotNull MavenDomDependency dependency,
                                 @NotNull Collection<MavenDomDependency> dependencies,
                                 @NotNull DomElementAnnotationHolder holder) {
    Set<MavenDomProjectModel> processed = new HashSet<>();
    List<String> links = new ArrayList<>();
    for (MavenDomDependency domDependency : dependencies) {
      if (dependency.equals(domDependency)) continue;
      MavenDomProjectModel model = domDependency.getParentOfType(MavenDomProjectModel.class, false);
      if (model != null && !processed.contains(model)) {
        links.add(createLinkText(model, domDependency));
        processed.add(model);
      }
    }
    links.sort(null);
    holder.createProblem(dependency, HighlightSeverity.WARNING,
                         MavenDomBundle.message("MavenDuplicateDependenciesInspection.has.duplicates", NlsMessages.formatAndList(links)));
  }

  private static String createLinkText(@NotNull MavenDomProjectModel model, @NotNull MavenDomDependency dependency) {
    XmlTag tag = dependency.getXmlTag();
    if (tag == null) return getProjectName(model);
    VirtualFile file = tag.getContainingFile().getVirtualFile();
    if (file == null) return getProjectName(model);

    return "<a href ='#navigation/" + file.getPath() + ":" + tag.getTextRange().getStartOffset() + "'>" + getProjectName(model) + "</a>";
  }

  @NotNull
  private static MultiMap<DependencyConflictId, MavenDomDependency> getDuplicateDependenciesMap(MavenDomProjectModel projectModel) {
    final MultiMap<DependencyConflictId, MavenDomDependency> allDependencies = MultiMap.createSet();

    Processor<MavenDomProjectModel> collectProcessor = model -> {
      collect(allDependencies, model.getDependencies());
      return false;
    };

    MavenDomProjectProcessorUtils.processChildrenRecursively(projectModel, collectProcessor, true);
    MavenDomProjectProcessorUtils.processParentProjects(projectModel, collectProcessor);

    return allDependencies;
  }

  private static void collect(MultiMap<DependencyConflictId, MavenDomDependency> duplicates, @NotNull MavenDomDependencies dependencies) {
    for (MavenDomDependency dependency : dependencies.getDependencies()) {
      DependencyConflictId mavenId = DependencyConflictId.create(dependency);
      if (mavenId == null) continue;

      duplicates.putValue(mavenId, dependency);
    }
  }

  private static void checkManagedDependencies(@NotNull MavenDomProjectModel projectModel,
                                               @NotNull DomElementAnnotationHolder holder) {
    MultiMap<DependencyConflictId, MavenDomDependency> duplicates = MultiMap.createSet();
    collect(duplicates, projectModel.getDependencyManagement().getDependencies());

    for (Map.Entry<DependencyConflictId, Collection<MavenDomDependency>> entry : duplicates.entrySet()) {
      Collection<MavenDomDependency> set = entry.getValue();
      if (set.size() <= 1) continue;

      for (MavenDomDependency dependency : set) {
        holder.createProblem(dependency, HighlightSeverity.WARNING, MavenProjectBundle.message("inspection.message.duplicated.dependency"));
      }
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "MavenDuplicateDependenciesInspection";
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }
}