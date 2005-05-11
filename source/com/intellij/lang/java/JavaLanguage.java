package com.intellij.lang.java;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.formatter.newXmlFormatter.java.AbstractJavaBlock;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.java.JavaAdapter;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.newCodeFormatting.FormattingModel;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 22, 2005
 * Time: 11:16:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaLanguage extends Language {
  private final FormattingModelBuilder myFormattingModelBuilder;

  public JavaLanguage() {
    super("JAVA");
    myFormattingModelBuilder = new FormattingModelBuilder() {
      public FormattingModel createModel(final PsiFile element, final CodeStyleSettings settings) {
        return new PsiBasedFormattingModel(element, settings, AbstractJavaBlock.createJavaBlock(SourceTreeToPsiMap.psiElementToTree(element), 
                                                                                                settings));
      }
    };
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

  public RefactoringSupportProvider getRefactoringSupportProvider() {
    return new JavaRefactoringSupportProvier();
  }

  public FormattingModelBuilder getFormattingModelBuilder() {
    return myFormattingModelBuilder;
  }
}
