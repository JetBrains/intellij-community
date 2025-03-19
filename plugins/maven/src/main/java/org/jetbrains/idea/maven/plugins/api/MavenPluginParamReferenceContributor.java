// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class MavenPluginParamReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(XmlTokenType.XML_DATA_CHARACTERS).withParent(
        XmlPatterns.xmlText().inFile(XmlPatterns.xmlFile())
      ),
      new MavenPluginParamRefProvider());
  }

  private static class MavenPluginParamRefProvider extends PsiReferenceProvider {

    @Override
    public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element, final @NotNull ProcessingContext context) {
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
