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
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.utils.MavenIcons;

import java.util.List;
import java.util.Set;

public class GenerateManagedDependencyAction extends GenerateDomElementAction {
  public GenerateManagedDependencyAction() {
    super(new MavenOverridingDependencyGenerateProvider(), MavenIcons.DEPENDENCY_ICON);
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
    protected MavenDomDependency doGenerate(final MavenDomProjectModel mavenModel, final Editor editor) {
      Set<MavenDomDependency> managingDependencies = collectManagingDependencies(mavenModel);

      List<MavenDomDependency> dependenciesToOverride =
        GenerateDependencyUtil.chooseDependencies(managingDependencies, mavenModel.getManager().getProject());

      for (final MavenDomDependency parentDependency : dependenciesToOverride) {
        final String groupId = parentDependency.getGroupId().getStringValue();
        final String artifactId = parentDependency.getArtifactId().getStringValue();

        if (!StringUtil.isEmptyOrSpaces(groupId) && !StringUtil.isEmptyOrSpaces(artifactId)) {
          return new WriteCommandAction<MavenDomDependency>(editor.getProject(), mavenModel.getXmlTag().getContainingFile()) {
            @Override
            protected void run(Result result) throws Throwable {
              MavenDomDependency dependency = MavenDomUtil.createMavenDomDependency(mavenModel, editor);

              dependency.getGroupId().setStringValue(groupId);
              dependency.getArtifactId().setStringValue(artifactId);
              String typeValue = parentDependency.getType().getStringValue();

              if (!StringUtil.isEmptyOrSpaces(typeValue)) {
                dependency.getType().setStringValue(typeValue);
              }
              dependency.getVersion().undefine();
            }
          }.execute().getResultObject();
        }
      }

      return null;
    }
  }

  @NotNull
  public static Set<MavenDomDependency> collectManagingDependencies(@NotNull final MavenDomProjectModel model) {
    final Set<MavenDomDependency> dependencies = new HashSet<MavenDomDependency>();

    final List<MavenDomDependency> existingDependencies = model.getDependencies().getDependencies();

    Processor<MavenDomDependencies> collectProcessor = new Processor<MavenDomDependencies>() {
      public boolean process(MavenDomDependencies mavenDomDependencies) {
        if (!model.equals(mavenDomDependencies.getParentOfType(MavenDomProjectModel.class, true))) {
          for (MavenDomDependency dependency : mavenDomDependencies.getDependencies()) {
            String groupId = dependency.getGroupId().getStringValue();
            String artifactId = dependency.getArtifactId().getStringValue();
            if (StringUtil.isEmptyOrSpaces(groupId) || StringUtil.isEmptyOrSpaces(artifactId)) continue;

            if (!isDependencyExist(groupId, artifactId, existingDependencies)) {
              dependencies.add(dependency);
            }
          }
        }
        return false;
      }
    };

    MavenDomProjectProcessorUtils.processDependenciesInDependencyManagement(model, collectProcessor, model.getManager().getProject());

    return dependencies;
  }

  private static boolean isDependencyExist(String groupId, String artifactId, List<MavenDomDependency> existingDependencies) {
    for (MavenDomDependency existingDependency : existingDependencies) {
      if (groupId.equals(existingDependency.getGroupId().getStringValue()) ||
          artifactId.equals(existingDependency.getArtifactId().getStringValue())) {
        return true;
      }
    }
    return false;
  }
}
