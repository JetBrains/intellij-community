package com.intellij.lang.xhtml;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.formatter.xml.HtmlPolicy;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 11:01:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class XHTMLLanguage extends XMLLanguage {
  private final FormattingModelBuilder myFormattingModelBuilder;

  public XHTMLLanguage() {
    super("XHTML");
    myFormattingModelBuilder = new FormattingModelBuilder() {
      public FormattingModel createModel(final PsiFile element, final CodeStyleSettings settings) {
        return new PsiBasedFormattingModel(element, settings, 
                                           new XmlBlock(SourceTreeToPsiMap.psiElementToTree(element), 
                                                        null, null, 
                                                        new HtmlPolicy(settings, ElementType.XML_TAG), null));
      }
    };
    
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new XmlFileHighlighter(false,true);
  }

  public PseudoTextBuilder getFormatter() {
    //return new HtmlPseudoTextBuilder(ElementType.XML_TAG);
    return null;  
  }

  public XmlPsiPolicy getPsiPolicy() {
    return ENCODE_EACH_SYMBOL_POLICY;
  }

  public ParserDefinition getParserDefinition() {
    return new XHTMLParserDefinition();
  }

  public FormattingModelBuilder getFormattingModelBuilder() {
    return myFormattingModelBuilder;
  }
}
