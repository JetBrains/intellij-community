package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.TIntArrayList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IndexPatternSearcher implements QueryExecutor<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
  public boolean execute(final IndexPatternSearch.SearchParameters queryParameters, final Processor<IndexPatternOccurrence> consumer) {
    final PsiFile file = queryParameters.getFile();
    if (file instanceof PsiBinaryFile || file instanceof PsiCompiledElement ||
        file.getVirtualFile() == null) {
      return true;
    }

    final CacheManager cacheManager = ((PsiManagerImpl)file.getManager()).getCacheManager();
    final IndexPatternProvider patternProvider = queryParameters.getPatternProvider();
    if (patternProvider != null) {
      if (cacheManager.getTodoCount(file.getVirtualFile(), patternProvider) == 0)
        return true;
    }
    else {
      if (cacheManager.getTodoCount(file.getVirtualFile(), queryParameters.getPattern()) == 0)
        return true;
    }

    TIntArrayList commentStarts = new TIntArrayList();
    TIntArrayList commentEnds = new TIntArrayList();
    char[] chars = file.textToCharArray();
    findCommentTokenRanges(file, chars, queryParameters.getRange(), commentStarts, commentEnds);

    for (int i = 0; i < commentStarts.size(); i++) {
      int commentStart = commentStarts.get(i);
      int commentEnd = commentEnds.get(i);

      if (patternProvider != null) {
        for(final IndexPattern pattern: patternProvider.getIndexPatterns()) {
          if (!collectPatternMatches(pattern, chars, commentStart, commentEnd, file, queryParameters.getRange(), consumer)) {
            return false;
          }
        }
      }
      else {
        if (!collectPatternMatches(queryParameters.getPattern(), chars, commentStart, commentEnd, file, queryParameters.getRange(), consumer)) {
          return false;
        }
      }
    }

    return true;
  }

  private static final TokenSet XML_COMMENT_BIT_SET = TokenSet.create(TreeElement.XML_COMMENT_CHARACTERS);
  private static final TokenSet XML_DATA_CHARS = TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS);

  private static void findCommentTokenRanges(final PsiFile file,
                                             final char[] chars,
                                             final TextRange range,
                                             final TIntArrayList commentStarts,
                                             final TIntArrayList commentEnds) {
    if (file instanceof PsiPlainTextFile) {
      FileType fType = file.getFileType();
      synchronized (PsiLock.LOCK) {
        if (fType instanceof CustomFileType) {
          TokenSet commentTokens = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
          Lexer lexer = fType.getHighlighter(file.getProject(), file.getVirtualFile()).getHighlightingLexer();
          findComments(lexer, chars, range, commentTokens, commentStarts, commentEnds);
        }
        else {
          commentStarts.add(0);
          commentEnds.add(file.getTextLength());
        }
      }
    }
    else {
      // collect comment offsets to prevent long locks by PsiManagerImpl.LOCK
      synchronized (PsiLock.LOCK) {
        final Language lang = file.getLanguage();
        Lexer lexer = lang.getSyntaxHighlighter(file.getProject(), file.getVirtualFile()).getHighlightingLexer();
        TokenSet commentTokens = null;
        if (file instanceof PsiJavaFile && !(file instanceof JspFile)) {
          lexer = new JavaLexer(((PsiJavaFile)file).getLanguageLevel());
          commentTokens = TokenSet.orSet(ElementType.COMMENT_BIT_SET, XML_COMMENT_BIT_SET, JavaDocTokenType.ALL_JAVADOC_TOKENS, XML_DATA_CHARS);
        }
        else if (PsiUtil.isInJspFile(file)) {
          final JspFile jspFile = PsiUtil.getJspFile(file);
          commentTokens = TokenSet.orSet(XML_COMMENT_BIT_SET, ElementType.COMMENT_BIT_SET);
          final ParserDefinition parserDefinition = jspFile.getViewProvider().getTemplateDataLanguage().getParserDefinition();
          if (parserDefinition != null) {
            commentTokens = TokenSet.orSet(commentTokens, parserDefinition.getCommentTokens());
          }
        }
        else if (file instanceof XmlFile) {
          commentTokens = XML_COMMENT_BIT_SET;
        }
        else {
          final ParserDefinition parserDefinition = lang.getParserDefinition();
          if (parserDefinition != null) {
            commentTokens = parserDefinition.getCommentTokens();
          }
        }

        if (commentTokens != null) {
          findComments(lexer, chars, range, commentTokens, commentStarts, commentEnds);
        }
      }
    }
  }

  private static void findComments(final Lexer lexer,
                                   final char[] chars,
                                   final TextRange range,
                                   final TokenSet commentTokens,
                                   final TIntArrayList commentStarts, final TIntArrayList commentEnds) {
    for (lexer.start(chars); ; lexer.advance()) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;

      if (range != null) {
        if (lexer.getTokenEnd() <= range.getStartOffset()) continue;
        if (lexer.getTokenStart() >= range.getEndOffset()) break;
      }

      boolean isComment = commentTokens.contains(tokenType);
      if (!isComment) {
        final Language commentLang = tokenType.getLanguage();
        final ParserDefinition parserDefinition = commentLang.getParserDefinition();
        if (parserDefinition != null) {
          final TokenSet langCommentTokens = parserDefinition.getCommentTokens();
          isComment = langCommentTokens.contains(tokenType);
        }
      }

      if (isComment) {
        final boolean jspToken = lexer.getTokenType() == JspTokenType.JSP_COMMENT;
        final int startDelta = jspToken ? "<%--".length() : 0;
        final int endDelta = jspToken ? "--%>".length() : 0;

        commentStarts.add(lexer.getTokenStart() + startDelta);
        commentEnds.add(lexer.getTokenEnd() - endDelta);
      }
    }
  }

  private static boolean collectPatternMatches(IndexPattern indexPattern,
                                               char[] chars,
                                               int commentStart,
                                               int commentEnd,
                                               PsiFile file,
                                               TextRange range,
                                               Processor<IndexPatternOccurrence> consumer) {
    Pattern pattern = indexPattern.getPattern();
    if (pattern != null) {
      ProgressManager.getInstance().checkCanceled();

      CharSequence input = new CharArrayCharSequence(chars, commentStart, commentEnd);
      Matcher matcher = pattern.matcher(input);
      while (true) {
        //long time1 = System.currentTimeMillis();
        boolean found = matcher.find();
        //long time2 = System.currentTimeMillis();
        //System.out.println("scanned text of length " + (lexer.getTokenEnd() - lexer.getTokenStart() + " in " + (time2 - time1) + " ms"));

        if (!found) break;
        int start = matcher.start() + commentStart;
        int end = matcher.end() + commentStart;
        if (start != end) {
          if (range == null || range.getStartOffset() <= start && end <= range.getEndOffset()) {
            if (!consumer.process(new IndexPatternOccurrenceImpl(file, start, end, indexPattern))) {
              return false;
            }
          }
        }

        ProgressManager.getInstance().checkCanceled();
      }
    }
    return true;
  }

}
