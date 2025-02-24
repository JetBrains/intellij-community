// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlBuilder {
  void doctype(final @Nullable CharSequence publicId, final @Nullable CharSequence systemId, final int startOffset, final int endOffset);

  enum ProcessingOrder {
    TAGS,
    TAGS_AND_TEXTS,
    TAGS_AND_ATTRIBUTES,
    TAGS_AND_ATTRIBUTES_AND_TEXTS
  }

  ProcessingOrder startTag(CharSequence localName, String namespace, int startoffset, int endoffset, final int headerEndOffset);

  void endTag(CharSequence localName, String namespace, int startoffset, int endoffset);

  void attribute(CharSequence name, CharSequence value, int startoffset, int endoffset);

  void textElement(CharSequence display, CharSequence physical, int startoffset, int endoffset);

  void entityRef(CharSequence ref, int startOffset, int endOffset);

  void error(@NotNull String message, int startOffset, int endOffset);
}