// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

import java.util.Arrays;
import java.util.regex.Matcher;

public class MavenFixedValueReferenceProvider implements MavenParamReferenceProvider, MavenSoftAwareReferenceProvider {

  private final String[] myValues;

  private boolean mySoft = false;

  public MavenFixedValueReferenceProvider(String[] values) {
    myValues = values;
  }

  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                               @NotNull MavenDomConfiguration domCfg,
                                               @NotNull ProcessingContext context) {
    TextRange range = ElementManipulators.getValueTextRange(element);

    String text = range.substring(element.getText());
    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(text);
    if (matcher.find()) {
      return PsiReference.EMPTY_ARRAY;
    }

    return new PsiReference[] {
      new PsiReferenceBase<>(element, mySoft) {
        @Override
        public @Nullable PsiElement resolve() {
          if (mySoft) {
            return null;
          }

          if (Arrays.asList(myValues).contains(getValue())) {
            return getElement();
          }

          return null;
        }

        @Override
        public Object @NotNull [] getVariants() {
          return myValues;
        }
      }
    };
  }

  @Override
  public void setSoft(boolean soft) {
    mySoft = soft;
  }
}
