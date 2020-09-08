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
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (!myStrict) return s;
    return getValues(context).contains(s) ? s : null;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @Override
  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    return getValues(context);
  }

  protected abstract Collection<@NlsSafe String> getValues(@NotNull ConvertContext context);

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return new HtmlBuilder()
      .append(MavenDomBundle.message("specified.value.is.not.acceptable.here"))
      .append(HtmlChunk.br())
      .append(MavenDomBundle.message("acceptable.values"))
      .appendWithSeparators(HtmlChunk.text(", "),
                            ContainerUtil.map(getValues(context), it -> HtmlChunk.text(it)))
      .toString();
  }
}