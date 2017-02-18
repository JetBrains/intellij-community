/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.generate;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenerateManagedDependencyAction extends GenerateDomElementAction {
  public GenerateManagedDependencyAction() {
    super(new MavenOverridingDependencyGenerateProvider(), AllIcons.Nodes.PpLib);
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }

  private static class MavenOverridingDependencyGenerateProvider extends MavenGenerateProvider<MavenDomDependency> {
    public MavenOverridingDependencyGenerateProvider() {
      super(MavenDomBundle.message("generate.managed.dependency"), MavenDomDependency.class);
    }

    @Override
    protected MavenDomDependency doGenerate(@NotNull final MavenDomProjectModel mavenModel, final Editor editor) {
      Set<DependencyConflictId> existingDependencies = collectExistingDependencies(mavenModel);
      Map<DependencyConflictId, MavenDomDependency> managingDependencies = collectManagingDependencies(mavenModel);

      Map<DependencyConflictId, MavenDomDependency> unexistManagingDeps = Maps.filterKeys(managingDependencies,
                                                                                          Predicates.not(Predicates.in(existingDependencies)));

      final List<MavenDomDependency> dependenciesToOverride =
        GenerateDependencyUtil.chooseDependencies(unexistManagingDeps.values(), mavenModel.getManager().getProject());

      if (!dependenciesToOverride.isEmpty()) {
        return new WriteCommandAction<MavenDomDependency>(editor.getProject(), mavenModel.getXmlTag().getContainingFile()) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            for (MavenDomDependency parentDependency : dependenciesToOverride) {
              String groupId = parentDependency.getGroupId().getStringValue();
              String artifactId = parentDependency.getArtifactId().getStringValue();

              if (!StringUtil.isEmptyOrSpaces(groupId) && !StringUtil.isEmptyOrSpaces(artifactId)) {
                MavenDomDependency dependency = MavenDomUtil.createDomDependency(mavenModel, editor);

                dependency.getGroupId().setStringValue(groupId);
                dependency.getArtifactId().setStringValue(artifactId);
                String typeValue = parentDependency.getType().getStringValue();
                String classifier = parentDependency.getClassifier().getStringValue();

                if (!StringUtil.isEmptyOrSpaces(typeValue)) {
                  dependency.getType().setStringValue(typeValue);
                }

                if (!StringUtil.isEmptyOrSpaces(classifier)) {
                  dependency.getClassifier().setStringValue(classifier);
                }

                dependency.getVersion().undefine();
              }
            }
          }
        }.execute().getResultObject();
      }

      return null;
    }
  }

  private static Set<DependencyConflictId> collectExistingDependencies(@NotNull final MavenDomProjectModel model) {
    final Set<DependencyConflictId> existingDependencies = new HashSet<>();
    for (MavenDomDependency dependency : model.getDependencies().getDependencies()) {
      DependencyConflictId id = DependencyConflictId.create(dependency);
      if (id != null) {
        existingDependencies.add(id);
      }
    }

    return existingDependencies;
  }

  @NotNull
  public static Map<DependencyConflictId, MavenDomDependency> collectManagingDependencies(@NotNull final MavenDomProjectModel model) {
    final Map<DependencyConflictId, MavenDomDependency> dependencies = new HashMap<>();

    Processor<MavenDomDependency> collectProcessor = dependency -> {
      DependencyConflictId id = DependencyConflictId.create(dependency);
      if (id != null && !dependencies.containsKey(id)) {
        dependencies.put(id, dependency);
      }

      return false;
    };

    MavenDomProjectProcessorUtils.processDependenciesInDependencyManagement(model, collectProcessor, model.getManager().getProject());

    return dependencies;
  }
}
