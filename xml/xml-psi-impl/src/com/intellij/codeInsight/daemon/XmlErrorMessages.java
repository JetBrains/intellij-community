// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @deprecated use {@link XmlPsiBundle} instead. Some message keys needs to be updated.
 *             See {@link XmlErrorMessages#keyMappings}
 */
@Deprecated(forRemoval = true)
public class XmlErrorMessages {
  private static final Map<String, String> keyMappings = ContainerUtil.newHashMap(
    new Pair<>("tag.start.is.not.closed", "xml.parsing.tag.start.is.not.closed"),
    new Pair<>("unescaped.ampersand.or.nonterminated.character.entity.reference",
               "xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"),
    new Pair<>("named.element.is.not.closed", "xml.parsing.named.element.is.not.closed"),
    new Pair<>("expected.attribute.eq.sign", "xml.parsing.expected.attribute.eq.sign"),
    new Pair<>("top.level.element.is.not.completed", "xml.parsing.top.level.element.is.not.completed"),
    new Pair<>("way.too.unbalanced", "xml.parsing.way.too.unbalanced")
  );

  /**
   * @deprecated use {@link XmlPsiBundle#message} instead
   */
  @Deprecated(forRemoval = true)
  public static @Nls String message(@NotNull String key, Object @NotNull ... params) {
    return XmlPsiBundle.message(keyMappings.getOrDefault(key, key), params);
  }
}
