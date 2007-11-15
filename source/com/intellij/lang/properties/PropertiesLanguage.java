package com.intellij.lang.properties;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.properties.findUsages.PropertiesFindUsagesProvider;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.structureView.PropertiesFileStructureViewComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PropertiesLanguage extends Language {
  public PropertiesLanguage() {
    super("Properties", "text/properties");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new PropertiesHighlighter();
      }
    });
  }

  public ParserDefinition getParserDefinition() {
    return new PropertiesParserDefinition();
  }

  private TokenSet myReadableTextContainerElements;

  @NotNull
  public TokenSet getReadableTextContainerElements() {
    if (myReadableTextContainerElements == null) {
      myReadableTextContainerElements = TokenSet.orSet(
        super.getReadableTextContainerElements(),
        TokenSet.create(PropertiesElementTypes.PROPERTY)
      );
    }
    return myReadableTextContainerElements;
  }

  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    return new StructureViewBuilder() {
      @NotNull
      public StructureView createStructureView(FileEditor fileEditor, Project project) {
        return new PropertiesFileStructureViewComponent(project, (PropertiesFile)psiFile, fileEditor);
      }
    };
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return new PropertiesFindUsagesProvider();
  }

  public Commenter getCommenter() {
    return new PropertiesCommenter();
  }
}
