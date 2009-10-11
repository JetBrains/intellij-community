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

/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import org.jetbrains.annotations.Nullable;

public interface XmlBuilder {
  void doctype(@Nullable final CharSequence publicId, @Nullable final CharSequence systemId, final int startOffset, final int endOffset);

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

  void error(String message, int startOffset, int endOffset);
}