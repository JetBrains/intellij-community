/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;
import org.jetbrains.idea.maven.plugins.api.MavenPluginParamInfo;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class MavenPropertyPsiReferenceProvider extends PsiReferenceProvider {
  public static final boolean SOFT_DEFAULT = false;

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return getReferences(element, SOFT_DEFAULT);
  }

  private static boolean isElementCanContainReference(PsiElement element) {
    if (element instanceof XmlTag) {
      for (MavenPluginParamInfo.ParamInfo info : MavenPluginParamInfo.getParamInfoList((XmlTag)element)) {
        if (Boolean.TRUE.equals(info.getParam().disableReferences)) {
          return false;
        }
      }
    }

    return true;
  }

  @Nullable
  private static MavenProject findMavenProject(PsiElement element) {
    VirtualFile virtualFile = MavenDomUtil.getVirtualFile(element);
    if (virtualFile == null) return null;

    MavenProjectsManager manager = MavenProjectsManager.getInstance(element.getProject());
    return manager.findProject(virtualFile);
  }

  public static PsiReference[] getReferences(PsiElement element, boolean isSoft) {
    TextRange textRange = ElementManipulators.getValueTextRange(element);
    if (textRange.isEmpty()) return PsiReference.EMPTY_ARRAY;

    String text = element.getText();

    if (StringUtil.isEmptyOrSpaces(text)) return PsiReference.EMPTY_ARRAY;

    if (!isElementCanContainReference(element)) return PsiReference.EMPTY_ARRAY;

    MavenProject mavenProject = null;
    XmlTag propertiesTag = null;
    List<PsiReference> result = null;

    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(textRange.substring(text));
    while (matcher.find()) {
      String propertyName = matcher.group(1);
      int from;
      if (propertyName == null) {
        propertyName = matcher.group(2);
        from = matcher.start(2);
      }
      else {
        from = matcher.start(1);
      }

      TextRange range = TextRange.from(textRange.getStartOffset() + from, propertyName.length());

      if (result == null) {
        result = new ArrayList<>();

        mavenProject = findMavenProject(element);
        if (mavenProject == null) {
          propertiesTag = findPropertiesParentTag(element);
          if (propertiesTag == null) {
            return PsiReference.EMPTY_ARRAY;
          }
        }
      }

      PsiReference ref;
      if (mavenProject != null) {
        ref = new MavenPropertyPsiReference(mavenProject, element, propertyName, range, isSoft);
      }
      else {
        ref = new MavenContextlessPropertyReference(propertiesTag, element, range, true);
      }

      result.add(ref);
    }

    return result == null ? PsiReference.EMPTY_ARRAY : result.toArray(new PsiReference[result.size()]);
  }

  private static XmlTag findPropertiesParentTag(@NotNull PsiElement element) {
    DomElement domElement = DomUtil.getDomElement(element);
    return domElement instanceof MavenDomProperties ? domElement.getXmlTag() : null;
  }
}
