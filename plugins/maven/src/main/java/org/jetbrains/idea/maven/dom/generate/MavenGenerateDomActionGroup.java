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

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomRepository;

public class MavenGenerateDomActionGroup extends DefaultActionGroup {
  public MavenGenerateDomActionGroup() {
    add(new GenerateDependencyAction());
    add(new GenerateManagedDependencyAction());

    add(createAction(MavenDomBundle.message("generate.dependency.template"), MavenDomDependency.class, "maven-dependency",
                     new Function<MavenDomProjectModel, DomElement>() {
                       public DomElement fun(MavenDomProjectModel mavenDomProjectModel) {
                         return mavenDomProjectModel.getDependencies();
                       }
                     }));
    add(createAction(MavenDomBundle.message("generate.plugin.template"), MavenDomPlugin.class, "maven-plugin",
                     new Function<MavenDomProjectModel, DomElement>() {
                       public DomElement fun(MavenDomProjectModel mavenDomProjectModel) {
                         return mavenDomProjectModel.getBuild().getPlugins();
                       }
                     }));

    add(createAction(MavenDomBundle.message("generate.repository.template"), MavenDomRepository.class, "maven-repository",
                     new Function<MavenDomProjectModel, DomElement>() {
                       public DomElement fun(MavenDomProjectModel mavenDomProjectModel) {
                         return mavenDomProjectModel.getRepositories();
                       }
                     }));


    add(new GenerateParentAction());
  }

  private static MavenGenerateTemplateAction createAction(String actionDescription,
                                                             final Class<? extends DomElement> aClass,
                                                             @NonNls @Nullable String mappingId,
                                                             @NotNull Function<MavenDomProjectModel, DomElement> parentFunction) {
    return new MavenGenerateTemplateAction(actionDescription, aClass, mappingId, parentFunction);
  }
}