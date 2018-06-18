package com.intellij.tasks.youtrack.lang;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class YouTrackParserDefinition implements ParserDefinition {
  private static final Logger LOG = Logger.getInstance(YouTrackParserDefinition.class);

  public static final IElementType ANY_TEXT = new IElementType("ANY_TEXT", YouTrackLanguage.INSTANCE);
  public static final IElementType QUERY = new IElementType("QUERY", YouTrackLanguage.INSTANCE);
  public static final IFileElementType FILE = new IFileElementType(YouTrackLanguage.INSTANCE);

  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new YouTrackMockLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new YouTrackMockParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    assert node.getElementType() == QUERY;
    return new YouTrackQueryElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new YouTrackFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }

  /**
   * Sole element that represents YouTrack query in PSI tree
   */
  public static class YouTrackQueryElement extends ASTWrapperPsiElement {
    YouTrackQueryElement(@NotNull ASTNode node) {
      super(node);
    }
  }

  /**
   * Tokenize whole query as single {@code ANY_TEXT} token
   */
  private static class YouTrackMockLexer extends LexerBase {
    private int myStart;
    private int myEnd;
    private CharSequence myBuffer;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      //LOG.debug(String.format("buffer: '%s', start: %d, end: %d", buffer, startOffset, endOffset));
      myBuffer = buffer;
      myStart = startOffset;
      myEnd = endOffset;
    }

    @Override
    public int getState() {
      return 0;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
      return myStart >= myEnd? null : ANY_TEXT;
    }

    @Override
    public int getTokenStart() {
      return myStart;
    }

    @Override
    public int getTokenEnd() {
      return myEnd;
    }

    @Override
    public void advance() {
      myStart = myEnd;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    @Override
    public int getBufferEnd() {
      return myEnd;
    }
  }


  /**
   * Parse whole YouTrack query as single {@code QUERY} element
   */
  private static class YouTrackMockParser implements PsiParser {

    @NotNull
    @Override
    public ASTNode parse(IElementType root, PsiBuilder builder) {
      PsiBuilder.Marker rootMarker = builder.mark();

      PsiBuilder.Marker queryMarker = builder.mark();
      assert builder.getTokenType() == null || builder.getTokenType() == ANY_TEXT;
      builder.advanceLexer();
      queryMarker.done(QUERY);
      assert builder.eof();

      rootMarker.done(root);
      return builder.getTreeBuilt();
    }
  }
}
