package com.intellij.lexer;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;


/**
 * @author max
 */
public class JavaHighlightingLexer extends LayeredLexer {
  public JavaHighlightingLexer(LanguageLevel languageLevel) {
    super(new JavaLexer(languageLevel));
    registerSelfStoppingLayer(new StringLiteralLexer('\"'),
                              new IElementType[]{JavaTokenType.STRING_LITERAL},
                              new IElementType[0]);

    registerSelfStoppingLayer(new StringLiteralLexer('\''),
                              new IElementType[]{JavaTokenType.CHARACTER_LITERAL},
                              new IElementType[0]);


    LayeredLexer docLexer = new LayeredLexer(new JavaDocLexer());

    HtmlHighlightingLexer lexer = new HtmlHighlightingLexer();
    lexer.setHasNoEmbeddments(true);
    docLexer.registerLayer(lexer,
                           new IElementType[]{JavaDocTokenType.DOC_COMMENT_DATA});

    registerSelfStoppingLayer(docLexer,
                              new IElementType[]{JavaTokenType.DOC_COMMENT},
                              new IElementType[]{JavaDocTokenType.DOC_COMMENT_END});
  }
}
