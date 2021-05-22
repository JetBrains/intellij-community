// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * @deprecated Use {@link XmlPsiBundle} instead. Some of the message keys needs to be updated.
 *             See @link {@link XmlErrorBundle#keyMappings}}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public class XmlErrorBundle {

  /**
   * @deprecated use {@link XmlPsiBundle#message} instead.
   */
  @Deprecated
  public static @Nls String message(@NotNull String key, Object @NotNull ... params) {
    return XmlPsiBundle.message(keyMappings.getOrDefault(key, key), params);
  }

  /**
   * @deprecated use {@link XmlPsiBundle#messagePointer} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull String key, Object @NotNull ... params) {
    return XmlPsiBundle.messagePointer(keyMappings.getOrDefault(key, key), params);
  }

  private static final Map<String, String> keyMappings = ContainerUtil.newHashMap(
    new Pair<>("tag.start.is.not.closed", "xml.parsing.tag.start.is.not.closed"),
    new Pair<>("unescaped.ampersand.or.nonterminated.character.entity.reference",
               "xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"),
    new Pair<>("named.element.is.not.closed", "xml.parsing.named.element.is.not.closed"),
    new Pair<>("expected.attribute.eq.sign", "xml.parsing.expected.attribute.eq.sign"),
    new Pair<>("top.level.element.is.not.completed", "xml.parsing.top.level.element.is.not.completed"),
    new Pair<>("way.too.unbalanced", "xml.parsing.way.too.unbalanced")
  );

  protected XmlErrorBundle() {
  }
}
