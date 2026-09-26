// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util.documentation;

import com.intellij.xml.util.Html5TagAndAttributeNamesProvider;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @deprecated Use {@link Html5TagAndAttributeNamesProvider} instead
 */
@Deprecated(forRemoval = true)
public final class HtmlDescriptorsTable {

  private static final Set<String> knownAttributes = Html5TagAndAttributeNamesProvider.getTags(false)
    .stream().flatMap(tag -> notNull(Html5TagAndAttributeNamesProvider.getTagAttributes(tag, false), Collections.<CharSequence>emptySet()).stream())
    .map(attr -> attr.toString())
    .collect(Collectors.toSet());

  /**
   * @deprecated Use {@link Html5TagAndAttributeNamesProvider} instead
   */
  @Deprecated(forRemoval = true)
  public static boolean isKnownAttributeDescriptor(String attributeName) {
    return knownAttributes.contains(attributeName.toLowerCase(Locale.US));
  }
}
