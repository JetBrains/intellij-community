/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

public interface XmlBuilder {
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
}