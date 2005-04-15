package com.intellij.lang.java;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.source.codeStyle.java.JavaAdapter;
import com.intellij.psi.impl.source.tree.ElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 22, 2005
 * Time: 11:16:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaLanguage extends Language {
  public JavaLanguage() {
    super("JAVA");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    LanguageLevel level = project != null ? PsiManager.getInstance(project).getEffectiveLanguageLevel() : LanguageLevel.HIGHEST;
    return new JavaFileHighlighter(level);
  }

  public PseudoTextBuilder getFormatter() {
    return new JavaAdapter() {
      protected FileType getFileType() {
        return StdFileTypes.JAVA;
      }
    };
  }

  public ParserDefinition getParserDefinition() {
    return new JavaParserDefinition();
  }

  public Commenter getCommenter() {
    return new JavaCommenter();
  }

  public FindUsagesProvider getFindUsagesProvider() {
    return new JavaFindUsagesProvider();
  }

  private static class Inner {
    private static final TokenSet COMMENT_BIT_SET = TokenSet.create(new IElementType[]{
      ElementType.DOC_COMMENT_DATA,
      ElementType.DOC_TAG_VALUE_TOKEN,
      ElementType.C_STYLE_COMMENT,
      ElementType.END_OF_LINE_COMMENT});
  }

  public boolean mayHaveReferences(IElementType token, final short searchContext) {
    if ((searchContext & UsageSearchContext.IN_STRINGS) != 0 && token == ElementType.LITERAL_EXPRESSION) return true;
    if ((searchContext & UsageSearchContext.IN_COMMENTS) != 0 && Inner.COMMENT_BIT_SET.isInSet(token)) return true;
    if ((searchContext & UsageSearchContext.IN_CODE) != 0 &&
        (token == ElementType.IDENTIFIER || token == ElementType.DOC_TAG_VALUE_TOKEN)) return true;
    // Java string literal to property file
    if ((searchContext & UsageSearchContext.IN_FOREIGN_LANGUAGES) != 0 && token == ElementType.LITERAL_EXPRESSION) return true;
    return false;
  }
}
