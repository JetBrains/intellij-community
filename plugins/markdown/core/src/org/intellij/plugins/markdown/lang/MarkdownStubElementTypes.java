package org.intellij.plugins.markdown.lang;

import com.intellij.psi.tree.IElementType;

import static org.intellij.plugins.markdown.lang.MarkdownElementType.platformType;

public interface MarkdownStubElementTypes {
  IElementType SETEXT_1 = platformType(org.intellij.markdown.MarkdownElementTypes.SETEXT_1);
  IElementType SETEXT_2 = platformType(org.intellij.markdown.MarkdownElementTypes.SETEXT_2);

  IElementType ATX_1 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_1);
  IElementType ATX_2 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_2);
  IElementType ATX_3 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_3);
  IElementType ATX_4 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_4);
  IElementType ATX_5 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_5);
  IElementType ATX_6 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_6);
}
