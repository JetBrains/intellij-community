package com.intellij.lang.xml;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.codeFormatting.xml.xml.XmlPseudoTextBuilder;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.ASTNode;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:59:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMLLanguage extends Language {
  private FoldingBuilder myFoldingBuilder;
  protected static final CDATAOnAnyEncodedPolicy CDATA_ON_ANY_ENCODED_POLICY = new CDATAOnAnyEncodedPolicy();
  protected static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();
  private final FormattingModelBuilder myFormattingModelBuilder;

  public XMLLanguage() {
    this("XML");    
  }

  protected XMLLanguage(String str) {
    super(str);
    myFormattingModelBuilder = new FormattingModelBuilder() {
      public FormattingModel createModel(final PsiFile element, final CodeStyleSettings settings) {
        final ASTNode root = SourceTreeToPsiMap.psiElementToTree(element);
        return new PsiBasedFormattingModel(element, settings, new XmlBlock(root, null, null, new XmlPolicy(settings), null));
      }
    };    
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new XmlFileHighlighter();
  }

  public PseudoTextBuilder getFormatter() {
    return new XmlPseudoTextBuilder();
  }

  public XmlPsiPolicy getPsiPolicy(){
    return CDATA_ON_ANY_ENCODED_POLICY;
  }

  public ParserDefinition getParserDefinition() {
    return new XMLParserDefinition();
  }

  public FindUsagesProvider getFindUsagesProvider() {
    return new XmlFindUsagesProvider();
  }

  @NotNull public SurroundDescriptor[] getSurroundDescriptors() {
    return new SurroundDescriptor[] {new XmlSurroundDescriptor()};
  }

  public Commenter getCommenter() {
    return new XmlCommenter();
  }

  public FoldingBuilder getFoldingBuilder() {
    if (myFoldingBuilder == null) myFoldingBuilder = new XmlFoldingBuilder();
    return myFoldingBuilder;
  }

  public FormattingModelBuilder getFormattingModelBuilder() {
    return myFormattingModelBuilder;
  }
}
