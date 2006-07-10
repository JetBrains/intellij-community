package com.intellij.lang.properties;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.properties.findUsages.PropertiesFindUsagesProvider;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.structureView.PropertiesFileStructureViewComponent;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:03:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesLanguage extends Language {
  private static final Annotator ANNOTATOR = new PropertiesAnnotator();
  private final DocumentationProvider myDocumentationProvider = new DocumentationProvider() {

    @Nullable
    public String getQuickNavigateInfo(PsiElement element) {
      if (element instanceof Property) {
        @NonNls String info = "\n\"" + ((Property)element).getValue() + "\"";
        PsiFile file = element.getContainingFile();
        if (file != null) {
          info = info + " [" + file.getName() + "]";
        }
        return info;
      }
      return null;
    }
  };

  public PropertiesLanguage() {
    super("Properties", "text/properties");
    StdLanguages.PROPERTIES = this;
  }

  public ParserDefinition getParserDefinition() {
    return new PropertiesParserDefinition();
  }

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile) {
    return new PropertiesHighlighter();
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

  public Annotator getAnnotator() {
    return ANNOTATOR;
  }

  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    return new StructureViewBuilder() {
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

  @NotNull
  public RefactoringSupportProvider getRefactoringSupportProvider() {
    return new RefactoringSupportProvider() {
      public boolean isSafeDeleteAvailable(PsiElement element) {
        return true;
      }

      @Nullable
      public RefactoringActionHandler getIntroduceVariableHandler() {
        return null;
      }
    };
  }

  public DocumentationProvider getDocumentationProvider() {
    return myDocumentationProvider;
  }
}
