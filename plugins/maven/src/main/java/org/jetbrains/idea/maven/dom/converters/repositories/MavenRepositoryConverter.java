// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters.repositories;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;
import org.jetbrains.idea.maven.dom.model.MavenDomRepositoryBase;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Serega.Vasiliev
 */
public abstract class MavenRepositoryConverter extends ResolvingConverter<String> {

  public static class Id extends MavenRepositoryConverter {
    @Override
    public @NotNull Collection<String> getVariants(final @NotNull ConvertContext context) {
      Module module = context.getModule();
      if (module != null) {
        return MavenRepositoriesProvider.getInstance().getRepositoryIds();
      }
      return Collections.emptySet();
    }

    @Override
    public LookupElement createLookupElement(String s) {
      return LookupElementBuilder.create(s)
        .withIcon(MavenIcons.MavenPlugin)
        .withTailText(" (" + MavenRepositoriesProvider.getInstance().getRepositoryUrl(s) + ")", true);
    }
  }

  public static class Name extends MavenRepositoryConverter {
    @Override
    public @NotNull Collection<String> getVariants(final @NotNull ConvertContext context) {
      Module module = context.getModule();

      if (module != null) {
        String name = MavenRepositoriesProvider.getInstance().getRepositoryName(getRepositoryId(context));
        if (!StringUtil.isEmptyOrSpaces(name)) return Collections.singleton(name);
      }
      return Collections.emptySet();
    }
  }

  public static class Url extends MavenUrlConverter {

    @Override
    public PsiReference @NotNull [] createReferences(GenericDomValue value, final PsiElement element, final ConvertContext context) {
      return new PsiReference[]{new WebReference(element) {
        @Override
        public Object @NotNull [] getVariants() {
          Module module = context.getModule();

          if (module != null) {
            String name = MavenRepositoriesProvider.getInstance().getRepositoryUrl(getRepositoryId(context));
            if (!StringUtil.isEmptyOrSpaces(name)) return new Object[]{name};
          }
          return super.getVariants();
        }
      }};
    }
  }

  private static @Nullable String getRepositoryId(ConvertContext context) {
    MavenDomRepositoryBase repository = context.getInvocationElement().getParentOfType(MavenDomRepositoryBase.class, false);
    if (repository != null) return repository.getId().getStringValue();

    return null;
  }

  @Override
  public String fromString(final @Nullable @NonNls String s, final @NotNull ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, @NotNull ConvertContext convertContext) {
    return s;
  }
}
