// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.plugins.api.common;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.dom.references.MavenDependencyReferenceProvider;
import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;
import org.jetbrains.idea.maven.plugins.api.MavenCompletionReferenceProvider;
import org.jetbrains.idea.maven.plugins.api.MavenParamReferenceProvider;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.charset.Charset;

/**
 * @author Sergey Evdokimov
 */
public final class MavenCommonParamReferenceProviders {

  private MavenCommonParamReferenceProviders() {
  }

  public static class FilePath implements MavenParamReferenceProvider {
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                 @NotNull MavenDomConfiguration domCfg,
                                                 @NotNull ProcessingContext context) {
      return MavenPathReferenceConverter.createReferences(domCfg, element, Conditions.alwaysTrue());
    }
  }

  public static class DirPath implements MavenParamReferenceProvider {
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                 @NotNull MavenDomConfiguration domCfg,
                                                 @NotNull ProcessingContext context) {
      return MavenPathReferenceConverter.createReferences(domCfg, element, FileReferenceSet.DIRECTORY_FILTER);
    }
  }

  public static class DependencyWithoutVersion extends MavenDependencyReferenceProvider {
    public DependencyWithoutVersion() {
      setCanHasVersion(false);
    }
  }

  public static class Encoding extends MavenCompletionReferenceProvider {

    @Override
    protected Object[] getVariants(@NotNull PsiReferenceBase reference) {
      Charset[] charsets = CharsetToolkit.getAvailableCharsets();

      LookupElement[] res = new LookupElement[charsets.length];
      for (int i = 0; i < charsets.length; i++) {
        res[i] = LookupElementBuilder.create(charsets[i].name()).withCaseSensitivity(false);
      }

      return res;
    }
  }

  public static class Goal extends MavenCompletionReferenceProvider {
    @Override
    protected Object[] getVariants(@NotNull PsiReferenceBase reference) {
      return MavenUtil.getPhaseVariants(MavenProjectsManager.getInstance(reference.getElement().getProject())).toArray();
    }
  }

  public static class Profile extends MavenCompletionReferenceProvider {
    @Override
    protected Object[] getVariants(@NotNull PsiReferenceBase reference) {
      return MavenProjectsManager.getInstance(reference.getElement().getProject()).getAvailableProfiles().toArray();
    }
  }

}
