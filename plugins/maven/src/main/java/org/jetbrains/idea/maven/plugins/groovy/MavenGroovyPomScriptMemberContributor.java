/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectModelDescription;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.util.dynamicMembers.DynamicMemberUtils;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyPomScriptMemberContributor extends NonCodeMembersContributor {

  private static final String CLASS_SOURCE = "class PomElements {\n" +
                                             "  org.apache.maven.project.MavenProject project;\n" +
                                             "  org.apache.maven.project.MavenProject pom;\n" +
                                             "  org.apache.maven.execution.MavenSession session;\n" +
                                             "  org.apache.maven.settings.Settings settings;\n" +
                                             "  org.slf4j.Logger log;\n" +
                                             "  groovy.util.AntBuilder ant;\n" +
                                             "  public void fail() {}" +
                                             "}";

  @Nullable
  @Override
  protected String getParentClassName() {
    return "pom";
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    PsiElement pomElement = aClass.getContainingFile().getContext();
    if (pomElement == null) return;

    PsiFile pomFile = pomElement.getContainingFile();
    if (!(pomFile instanceof XmlFile)) return;

    DomManager domManager = DomManager.getDomManager(pomElement.getProject());
    if (!(domManager.getDomFileDescription((XmlFile)pomFile) instanceof MavenDomProjectModelDescription)) {
      return;
    }

    DynamicMemberUtils.process(processor, false, place, CLASS_SOURCE);
  }
}
