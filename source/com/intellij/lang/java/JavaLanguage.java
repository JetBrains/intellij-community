package com.intellij.lang.java;

import com.intellij.codeInsight.hint.api.impls.AnnotationParameterInfoHandler;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.codeInsight.hint.api.impls.ReferenceParameterInfoHandler;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
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
  public JavaLanguage() {
    super("JAVA", "text/java", "application/x-java", "text/x-java");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SyntaxHighlighterFactory() {
      @NotNull
      public SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
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
    });
  }

  public Commenter getCommenter() {
    return new JavaCommenter();
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return new JavaFindUsagesProvider();
  }

  @NotNull
  public TokenSet getReadableTextContainerElements() {
    return TokenSet.create(JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT,
                           JavaDocTokenType.DOC_COMMENT_DATA, JavaTokenType.STRING_LITERAL);
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) return null;
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new JavaFileTreeModel((PsiJavaFile)psiFile);
      }

      public boolean isRootNodeShown() {
        return false;
      }
    };
  }

  private ParameterInfoHandler[] myHandlers;

  public @Nullable ParameterInfoHandler[] getParameterInfoHandlers() {
    if (myHandlers == null) {
      myHandlers = new ParameterInfoHandler[] {
        new MethodParameterInfoHandler(),
        new ReferenceParameterInfoHandler(),
        new AnnotationParameterInfoHandler()
      };
    }
    return myHandlers;
  }

}
