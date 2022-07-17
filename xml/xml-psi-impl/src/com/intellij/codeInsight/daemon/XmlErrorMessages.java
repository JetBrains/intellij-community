// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

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
  private static final Map<String, String> keyMappings = Map.ofEntries(
    Map.entry("tag.start.is.not.closed", "xml.parsing.tag.start.is.not.closed"),
    Map.entry("unescaped.ampersand.or.nonterminated.character.entity.reference", "xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"),
    Map.entry("named.element.is.not.closed", "xml.parsing.named.element.is.not.closed"),
    Map.entry("expected.attribute.eq.sign", "xml.parsing.expected.attribute.eq.sign"),
    Map.entry("top.level.element.is.not.completed", "xml.parsing.top.level.element.is.not.completed"),
    Map.entry("way.too.unbalanced", "xml.parsing.way.too.unbalanced")
  );

  /**
   * @deprecated use {@link XmlPsiBundle#message} instead
   */
  @Deprecated(forRemoval = true)
  public static @Nls String message(@NotNull String key, Object @NotNull ... params) {
    return XmlPsiBundle.message(keyMappings.getOrDefault(key, key), params);
  }
}
