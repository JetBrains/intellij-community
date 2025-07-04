// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlBuilder {
  void doctype(@Nullable CharSequence publicId,
               @Nullable CharSequence systemId,
               int startOffset,
               int endOffset);

  ProcessingOrder startTag(CharSequence localName,
                           String namespace,
                           int startOffset,
                           int endOffset,
                           int headerEndOffset);

  void endTag(CharSequence localName,
              String namespace,
              int startOffset,
              int endOffset);

  void attribute(CharSequence name,
                 CharSequence value,
                 int startOffset,
                 int endOffset);

  void textElement(CharSequence display,
                   CharSequence physical,
                   int startOffset,
                   int endOffset);

  void entityRef(CharSequence ref,
                 int startOffset,
                 int endOffset);

  void error(@NotNull String message,
             int startOffset,
             int endOffset);

  enum ProcessingOrder {
    TAGS,
    TAGS_AND_TEXTS,
    TAGS_AND_ATTRIBUTES,
    TAGS_AND_ATTRIBUTES_AND_TEXTS
  }
}