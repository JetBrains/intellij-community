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

  @NotNull ProcessingOrder startTag(@NotNull CharSequence localName,
                                    @NotNull String namespace,
                                    int startOffset,
                                    int endOffset,
                                    int headerEndOffset);

  void endTag(@NotNull CharSequence localName,
              @NotNull String namespace,
              int startOffset,
              int endOffset);

  void attribute(@NotNull CharSequence name,
                 @NotNull CharSequence value,
                 int startOffset,
                 int endOffset);

  void textElement(@NotNull CharSequence display,
                   @NotNull CharSequence physical,
                   int startOffset,
                   int endOffset);

  void entityRef(@NotNull CharSequence ref,
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