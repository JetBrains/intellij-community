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
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenUtil;

import static org.jetbrains.idea.maven.plugins.api.MavenPluginParamInfo.ParamInfo;

/**
 * @author Sergey Evdokimov
 */
public class MavenPluginParamReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(XmlTokenType.XML_DATA_CHARACTERS).withParent(
        XmlPatterns.xmlText().inFile(XmlPatterns.xmlFile())
      ),
      new MavenPluginParamRefProvider());
  }

  private static class MavenPluginParamRefProvider extends PsiReferenceProvider {

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
      final XmlText xmlText = (XmlText)element.getParent();
      PsiFile xmlFile = element.getContainingFile();
      VirtualFile virtualFile = xmlFile.getVirtualFile();
      if (virtualFile == null) {
        virtualFile = xmlFile.getOriginalFile().getVirtualFile();
      }
      if (!MavenUtil.isPomFile(element.getProject(), virtualFile)) return PsiReference.EMPTY_ARRAY;

      if (!MavenPluginParamInfo.isSimpleText(xmlText)) return PsiReference.EMPTY_ARRAY;

      MavenPluginParamInfo.ParamInfoList paramInfos = MavenPluginParamInfo.getParamInfoList(xmlText);
      for (ParamInfo info : paramInfos) {
        MavenParamReferenceProvider providerInstance = info.getProviderInstance();
        if (providerInstance != null) {
          return providerInstance.getReferencesByElement(element, paramInfos.getDomCfg(), context);
        }
      }

      return PsiReference.EMPTY_ARRAY;
    }
  }

}
