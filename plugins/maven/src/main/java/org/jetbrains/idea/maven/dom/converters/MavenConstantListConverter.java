// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;

import java.util.Collection;

public abstract class MavenConstantListConverter extends ResolvingConverter<String> {
  private final boolean myStrict;

  protected MavenConstantListConverter() {
    this(true);
  }

  protected MavenConstantListConverter(boolean strict) {
    myStrict = strict;
  }

  @Override
  public String fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (!myStrict) return s;
    return getValues(context).contains(s) ? s : null;
  }

  @Override
  public String toString(@Nullable String s, @NotNull ConvertContext context) {
    return s;
  }

  @Override
  public @NotNull Collection<String> getVariants(@NotNull ConvertContext context) {
    return getValues(context);
  }

  protected abstract Collection<@NlsSafe String> getValues(@NotNull ConvertContext context);

  @Override
  public String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return new HtmlBuilder()
      .append(MavenDomBundle.message("specified.value.is.not.acceptable.here"))
      .append(HtmlChunk.br())
      .append(MavenDomBundle.message("acceptable.values"))
      .appendWithSeparators(HtmlChunk.text(", "),
                            ContainerUtil.map(getValues(context), it -> HtmlChunk.text(it)))
      .toString();
  }
}