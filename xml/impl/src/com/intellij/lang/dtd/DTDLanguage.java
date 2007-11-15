package com.intellij.lang.dtd;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.xml.XmlCommenter;
import com.intellij.lang.xml.XmlFindUsagesProvider;
import com.intellij.lang.xml.XmlFoldingBuilder;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:53:26 AM
 * To change this template use File | Settings | File Templates.
 */
public class DTDLanguage extends Language {
  public DTDLanguage() {
    super("DTD", "text/dtd", "text/x-dtd");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new XmlFileHighlighter(true);
      }
    });
  }

  public ParserDefinition getParserDefinition() {
    return new DTDParserDefinition();
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return new XmlFindUsagesProvider() {
    };
  }

  public Commenter getCommenter() {
    return new XmlCommenter();
  }

  @Nullable
  public FoldingBuilder getFoldingBuilder() {
    return new XmlFoldingBuilder();
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new XmlStructureViewTreeModel((XmlFile)psiFile);
      }
    };
  }
}
