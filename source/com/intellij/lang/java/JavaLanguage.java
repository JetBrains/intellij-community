package com.intellij.lang.java;

import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurroundDescriptor;
import com.intellij.codeInsight.generation.surroundWith.JavaStatementsSurroundDescriptor;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.lang.Commenter;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.formatter.java.AbstractJavaBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 22, 2005
 * Time: 11:16:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaLanguage extends Language {
  private final FormattingModelBuilder myFormattingModelBuilder;

  private final static SurroundDescriptor[] SURROUND_DESCRIPTORS = new SurroundDescriptor[] {
    new JavaExpressionSurroundDescriptor(),
    new JavaStatementsSurroundDescriptor()
  };

  public JavaLanguage() {
    super("JAVA", "text/java", "application/x-java", "text/x-java");
    myFormattingModelBuilder = new FormattingModelBuilder() {
      @NotNull
      public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
        final FileElement fileElement = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
        return new PsiBasedFormattingModel(element.getContainingFile(), AbstractJavaBlock.createJavaBlock(fileElement,
                                                                                                          settings),
                                           FormattingDocumentModelImpl.createOn(element.getContainingFile()));
      }
    };
  }

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile) {
    LanguageLevel languageLevel;
    if (project != null && virtualFile != null) {
      final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(virtualFile);
      if (module != null) {
        languageLevel = module.getEffectiveLanguageLevel();
      } else {
        languageLevel = LanguageLevel.HIGHEST;
      }
    } else {
      languageLevel = LanguageLevel.HIGHEST;
    }

    return new JavaFileHighlighter(languageLevel);
  }

  public ParserDefinition getParserDefinition() {
    return new JavaParserDefinition();
  }

  public Commenter getCommenter() {
    return new JavaCommenter();
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return new JavaFindUsagesProvider();
  }

  @NotNull
  public RefactoringSupportProvider getRefactoringSupportProvider() {
    return new JavaRefactoringSupportProvier();
  }

  public FormattingModelBuilder getFormattingModelBuilder() {
    return myFormattingModelBuilder;
  }

  @NotNull
  public TokenSet getReadableTextContainerElements() {
    return TokenSet.create(JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT,
                           JavaDocTokenType.DOC_COMMENT_DATA, JavaTokenType.STRING_LITERAL);
  }

  @NotNull
  public SurroundDescriptor[] getSurroundDescriptors() {
    return SURROUND_DESCRIPTORS;
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) return null;
    return new TreeBasedStructureViewBuilder() {
      public StructureViewModel createStructureViewModel() {
        return new JavaFileTreeModel((PsiJavaFile)psiFile);
      }

      public boolean isRootNodeShown() {
        return false;
      }
    };
  }

  @NotNull
  public ImportOptimizer getImportOptimizer() {
    return new JavaImportOptimizer();
  }

  public DocumentationProvider getDocumentationProvider() {
    return new JavaDocumentationProvider();
  }
}
