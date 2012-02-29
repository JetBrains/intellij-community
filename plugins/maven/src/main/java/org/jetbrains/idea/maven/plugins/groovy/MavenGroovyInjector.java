/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof XmlText)) return;

    PsiElement sourceTag = host.getParent();
    if (!isTagOfName(sourceTag, "source")) return;

    PsiElement configurationTag = sourceTag.getParent();
    if (!isTagOfName(configurationTag, "configuration")) return;

    PsiElement executionTag = configurationTag.getParent();
    if (!isTagOfName(executionTag, "execution")) return;

    PsiElement executionsTag = executionTag.getParent();
    if (!isTagOfName(executionsTag, "executions")) return;

    PsiElement pluginTag = executionsTag.getParent();
    if (!isTagOfName(pluginTag, "plugin")) return;

    XmlTag plugin = (XmlTag)pluginTag;

    XmlTag groupId = plugin.findFirstSubTag("groupId");
    if (groupId == null || !groupId.getValue().getText().trim().equals("org.codehaus.groovy.maven")) return;

    XmlTag artifactId = plugin.findFirstSubTag("artifactId");
    if (artifactId == null || !artifactId.getValue().getText().trim().equals("gmaven-plugin")) return;

    if (!"pom.xml".equals(plugin.getContainingFile().getName())) return;

    injectionPlacesRegistrar.addPlace(GroovyFileType.GROOVY_LANGUAGE, TextRange.from(0, host.getTextLength()), null, null);
  }

  private static boolean isTagOfName(@Nullable PsiElement element, String name) {
    return element instanceof XmlTag && name.equals(((XmlTag)element).getName());
  }
}
