// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.idea.maven.model.MavenSource;

import java.util.Collection;
import java.util.List;

public class MavenSourceScopeConverter extends ResolvingConverter<String> implements MavenDomSoftAwareConverter {

  @Override
  public @Unmodifiable @NotNull Collection<? extends String> getVariants(@NotNull ConvertContext context) {
    return List.of(MavenSource.MAIN_SCOPE, MavenSource.TEST_SCOPE);
  }

  @Override
  public @Nullable String fromString(@Nullable String s, @NotNull ConvertContext context) {
    if (s == null) {
      return MavenSource.MAIN_SCOPE;
    }
    return s;
  }

  @Override
  public @Nullable String toString(@Nullable String s, @NotNull ConvertContext context) {
    return s;
  }

  @Override
  public boolean isSoft(@NotNull DomElement element) {
    return true;
  }
}
