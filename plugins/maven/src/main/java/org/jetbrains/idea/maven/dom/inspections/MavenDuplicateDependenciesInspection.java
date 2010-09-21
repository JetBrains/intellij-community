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
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MavenDuplicateDependenciesInspection extends BasicDomElementsInspection<MavenDomProjectModel> {
  public MavenDuplicateDependenciesInspection() {
    super(MavenDomProjectModel.class);
  }

  @Override
  public void checkFileElement(DomFileElement<MavenDomProjectModel> domFileElement,
                               DomElementAnnotationHolder holder) {

    final XmlFile xmlFile = domFileElement.getFile();
    MavenDomProjectModel projectModel = domFileElement.getRootElement();

    checkMavenProjectModel(projectModel, xmlFile, holder);
  }

  private static void checkMavenProjectModel(@NotNull MavenDomProjectModel projectModel,
                                             @NotNull XmlFile xmlFile,
                                             @NotNull DomElementAnnotationHolder holder) {
    final Map<String, Set<MavenDomDependency>> allDuplicates = getDuplicateDependenciesMap(projectModel);

    for (MavenDomDependency dependency : projectModel.getDependencies().getDependencies()) {
      String id = createId(dependency);
      if (id != null) {
        Set<MavenDomDependency> dependencies = allDuplicates.get(id);
        if (dependencies != null && dependencies.size() > 1) {
          addProblem(dependency, dependencies, holder);
        }
      }
    }
  }

  private static void addProblem(@NotNull MavenDomDependency dependency,
                                 @NotNull Set<MavenDomDependency> dependencies,
                                 @NotNull DomElementAnnotationHolder holder) {
    StringBuffer sb = new StringBuffer();
    Set<MavenDomProjectModel> processed = new HashSet<MavenDomProjectModel>();
    for (MavenDomDependency domDependency : dependencies) {
      if (dependency.equals(domDependency)) continue;
      MavenDomProjectModel model = domDependency.getParentOfType(MavenDomProjectModel.class, false);
      if (model != null && !processed.contains(model)) {
        if (processed.size() > 0) sb.append(", ");
        sb.append(createLinkText(model, domDependency));

        processed.add(model);
      }
    }
    holder.createProblem(dependency, HighlightSeverity.WARNING, MavenDomBundle.message("MavenDuplicateDependenciesInspection.has.duplicates", sb.toString()));
  }

  private static String createLinkText(@NotNull MavenDomProjectModel model,@NotNull MavenDomDependency dependency) {
    StringBuffer sb =new StringBuffer();

    XmlTag tag = dependency.getXmlTag();
    if (tag == null) return getProjectName(model);
    VirtualFile file = tag.getContainingFile().getVirtualFile();
    if (file == null) return getProjectName(model);

    sb.append("<a href ='#navigation/");
    sb.append(file.getPath());
    sb.append(":");
    sb.append(tag.getTextRange().getStartOffset());
    sb.append("'>");
    sb.append(getProjectName(model));
    sb.append("</a>");

    return sb.toString(); 
  }

  @NotNull
  private static String getProjectName(MavenDomProjectModel model) {
    MavenProject mavenProject = MavenDomUtil.findProject(model);
    if (mavenProject != null) {
      return mavenProject.getDisplayName();
    } else {
      String name = model.getName().getStringValue();
      if (!StringUtil.isEmptyOrSpaces(name)) {
        return name;
      }
      else {
        return "pom.xml"; // ?
      }
    }
  }

  @NotNull
  private static Map<String, Set<MavenDomDependency>> getDuplicateDependenciesMap(MavenDomProjectModel projectModel) {
    final Map<String, Set<MavenDomDependency>> allDependencies = new HashMap<String, Set<MavenDomDependency>>();

    Processor<MavenDomProjectModel> collectProcessor = new Processor<MavenDomProjectModel>() {
      public boolean process(MavenDomProjectModel model) {
        for (MavenDomDependency dependency : model.getDependencies().getDependencies()) {
          String mavenId = createId(dependency);
          if (mavenId != null) {
            if (allDependencies.containsKey(mavenId)) {
              allDependencies.get(mavenId).add(dependency);
            }
            else {
              Set<MavenDomDependency> dependencies = new HashSet<MavenDomDependency>();
              dependencies.add(dependency);
              allDependencies.put(mavenId, dependencies);
            }
          }
        }
        return false;
      }
    };

    MavenDomProjectProcessorUtils.processChildrenRecursively(projectModel, collectProcessor, true);
    MavenDomProjectProcessorUtils.processParentProjects(projectModel, collectProcessor);

    return allDependencies;
  }

  @Nullable
  private static String createId(MavenDomDependency coordinates) {
    String groupId = coordinates.getGroupId().getStringValue();
    String artifactId = coordinates.getArtifactId().getStringValue();

    if (StringUtil.isEmptyOrSpaces(groupId) || StringUtil.isEmptyOrSpaces(artifactId)) return null;

    String version = coordinates.getVersion().getStringValue();
    String type = coordinates.getType().getStringValue();
    String classifier = coordinates.getClassifier().getStringValue();

    return groupId +":" + artifactId + ":" + version + ":" + type + ":" + classifier;

  }

  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @NotNull
  public String getDisplayName() {
    return MavenDomBundle.message("inspection.duplicate.dependencies.name");
  }

  @NotNull
  public String getShortName() {
    return "MavenDuplicateDependenciesInspection";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }
}