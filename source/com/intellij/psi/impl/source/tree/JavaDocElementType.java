package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lexer.JavaLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.parsing.JavadocParsing;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.util.text.CharArrayUtil;

public interface JavaDocElementType {
  //chameleon
  IElementType DOC_TAG = new IJavaDocElementType("DOC_TAG");
  IElementType DOC_TAG_VALUE = new IJavaDocElementType("DOC_TAG_VALUE");
  IElementType DOC_INLINE_TAG = new IJavaDocElementType("DOC_INLINE_TAG");
  IElementType DOC_METHOD_OR_FIELD_REF = new IJavaDocElementType("DOC_METHOD_OR_FIELD_REF");
  IElementType DOC_PARAMETER_REF = new IJavaDocElementType("DOC_PARAMETER_REF");

  IElementType DOC_REFERENCE_HOLDER = new IChameleonElementType("DOC_REFERENCE_HOLDER", Language.findByID("JAVA")){
    public ASTNode parseContents(ASTNode chameleon) {
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      return JavadocParsing.parseJavaDocReference(chars, SharedImplUtil.findCharTableByTree(chameleon), getLanguage().getParserDefinition().createLexer(),
                                                  ((LeafElement)chameleon).getState(), false);
    }
    public boolean isParsable(CharSequence buffer) {return false;}
  };

  IElementType DOC_TYPE_HOLDER = new IChameleonElementType("DOC_TYPE_HOLDER", Language.findByID("JAVA")){
    public ASTNode parseContents(ASTNode chameleon) {
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      return JavadocParsing.parseJavaDocReference(chars, SharedImplUtil.findCharTableByTree(chameleon), getLanguage().getParserDefinition().createLexer(),
                                                  ((LeafElement)chameleon).getState(), true);
    }
    public boolean isParsable(CharSequence buffer) {return false;}
  };

  IElementType DOC_COMMENT = new IChameleonElementType("DOC_COMMENT", Language.findByID("JAVA")){
    public ASTNode parseContents(ASTNode chameleon) {
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      final ParsingContext context = new ParsingContext(SharedImplUtil.findCharTableByTree(chameleon));
      return context.getJavadocParsing().parseDocCommentText(chameleon.getTreeParent().getPsi().getManager(), chars, 0, chars.length);
    }
    public boolean isParsable(CharSequence buffer) {
      final JavaLexer lexer = new JavaLexer(LanguageLevel.JDK_1_5);

      lexer.start(CharArrayUtil.fromSequence(buffer));
      if(lexer.getTokenType() != DOC_COMMENT) return false;
      lexer.advance();
      if(lexer.getTokenType() != null) return false;
      return true;
    }
  };
}
