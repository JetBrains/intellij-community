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
    @NotNull
    public Collection<String> getVariants(final ConvertContext context) {
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
    @NotNull
    public Collection<String> getVariants(final ConvertContext context) {
      Module module = context.getModule();

      if (module != null) {
        String name = MavenRepositoriesProvider.getInstance().getRepositoryName(getRepositoryId(context));
        if (!StringUtil.isEmptyOrSpaces(name)) return Collections.singleton(name);
      }
      return Collections.emptySet();
    }
  }

  public static class Url extends MavenUrlConverter {

    @NotNull
    @Override
    public PsiReference[] createReferences(GenericDomValue value, final PsiElement element, final ConvertContext context) {
      return new PsiReference[]{new WebReference(element) {
        @NotNull
        @Override
        public Object[] getVariants() {
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

  @Nullable
  private static String getRepositoryId(ConvertContext context) {
    MavenDomRepositoryBase repository = context.getInvocationElement().getParentOfType(MavenDomRepositoryBase.class, false);
    if (repository != null) return repository.getId().getStringValue();

    return null;
  }

  public String fromString(@Nullable @NonNls final String s, final ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext convertContext) {
    return s;
  }
}
