package com.intellij.lang.properties;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.properties.findUsages.PropertiesFindUsagesProvider;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.structureView.PropertiesFileStructureViewComponent;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author max
 */
public class PropertiesLanguage extends Language {
  private static final Annotator ANNOTATOR = new PropertiesAnnotator();
  private final DocumentationProvider myDocumentationProvider = new QuickDocumentationProvider() {
    @Nullable
    public String getQuickNavigateInfo(PsiElement element) {
      if (element instanceof Property) {
        @NonNls String info = "\n\"" + ((Property)element).getValue() + "\"";
        PsiFile file = element.getContainingFile();
        if (file != null) {
          info += " [" + file.getName() + "]";
        }
        return info;
      }
      return null;
    }

    public String generateDoc(final PsiElement element, final PsiElement originalElement) {
      if (element instanceof Property) {
        Property property = (Property)element;
        String text = property.getDocCommentText();

        @NonNls String info = "";
        if (text != null) {
          TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_COMMENT).clone();
          Color background = attributes.getBackgroundColor();
          if (background != null) {
            info +="<div bgcolor=#"+GuiUtils.colorToHex(background)+">";
          }
          String doc = StringUtil.join(StringUtil.split(text, "\n"), "<br>");
          info += "<font color=#" + GuiUtils.colorToHex(attributes.getForegroundColor()) + ">" + doc + "</font>\n<br>";
          if (background != null) {
            info += "</div>";
          }
        }
        info += "\n<b>" + property.getName() + "</b>=\"" + ((Property)element).getValue() + "\"";
        PsiFile file = element.getContainingFile();
        if (file != null) {
          info += " [" + file.getName() + "]";
        }
        return info;
      }
      return null;
    }
  };

  public PropertiesLanguage() {
    super("Properties", "text/properties");
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

  protected DocumentationProvider createDocumentationProvider() {
    return myDocumentationProvider;
  }
}
