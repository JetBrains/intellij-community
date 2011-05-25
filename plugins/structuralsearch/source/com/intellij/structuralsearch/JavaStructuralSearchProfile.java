package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.JavaCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.filters.JavaLexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaStructuralSearchProfile extends StructuralSearchProfile {
  private JavaLexicalNodesFilter myJavaLexicalNodesFilter;

  public void compile(PsiElement[] elements, @NotNull GlobalCompilingVisitor globalVisitor) {
    elements[0].getParent().accept(new JavaCompilingVisitor(globalVisitor));
  }

  @NotNull
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new JavaMatchingVisitor(globalVisitor);
  }

  @NotNull
  @Override
  public PsiElementVisitor getLexicalNodesFilter(@NotNull LexicalNodesFilter filter) {
    if (myJavaLexicalNodesFilter == null) {
      myJavaLexicalNodesFilter = new JavaLexicalNodesFilter(filter);
    }
    return myJavaLexicalNodesFilter;
  }

  @NotNull
  public CompiledPattern createCompiledPattern() {
    return new JavaCompiledPattern();
  }

  @Override
  public boolean canProcess(@NotNull FileType fileType) {
    return fileType == StdFileTypes.JAVA;
  }

  public boolean isMyLanguage(@NotNull Language language) {
    return language == StdLanguages.JAVA;
  }

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return new JavaReplaceHandler(context);
  }

  @NotNull
  @Override
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @Nullable Language language,
                                        String contextName, @Nullable String extension,
                                        @NotNull Project project,
                                        boolean physical) {
    if (physical) {
      throw new UnsupportedOperationException(getClass() + " cannot create physical PSI");
    }
    PsiElement[] result;
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    if (context == PatternTreeContext.Block) {
      PsiElement element = elementFactory.createStatementFromText("{\n" + text + "\n}", null);
      result = ((PsiBlockStatement)element).getCodeBlock().getChildren();
      final int extraChildCount = 4;

      if (result.length > extraChildCount) {
        PsiElement[] newresult = new PsiElement[result.length - extraChildCount];
        final int extraChildStart = 2;
        System.arraycopy(result, extraChildStart, newresult, 0, result.length - extraChildCount);
        result = newresult;
      }
      else {
        result = PsiElement.EMPTY_ARRAY;
      }

    }
    else if (context == PatternTreeContext.Class) {
      PsiElement element = elementFactory.createStatementFromText("class A {\n" + text + "\n}", null);
      PsiClass clazz = (PsiClass)((PsiDeclarationStatement)element).getDeclaredElements()[0];
      PsiElement startChild = clazz.getLBrace();
      if (startChild != null) startChild = startChild.getNextSibling();

      PsiElement endChild = clazz.getRBrace();
      if (endChild != null) endChild = endChild.getPrevSibling();

      List<PsiElement> resultElementsList = new ArrayList<PsiElement>(3);
      assert startChild != null;
      for (PsiElement el = startChild.getNextSibling(); el != endChild && el != null; el = el.getNextSibling()) {
        resultElementsList.add(el);
      }

      result = PsiUtilBase.toPsiElementArray(resultElementsList);
    }
    else {
      result = PsiFileFactory.getInstance(project).createFileFromText("__dummy.java", text).getChildren();
    }
    return result;
  }

  @NotNull
  @Override
  public Editor createEditor(@NotNull SearchContext searchContext,
                             @NotNull FileType fileType,
                             Language dialect,
                             String text,
                             boolean useLastConfiguration) {
    // provides autocompletion

    PsiElement element = searchContext.getFile();

    if (element != null && !useLastConfiguration) {
      final Editor selectedEditor = FileEditorManager.getInstance(searchContext.getProject()).getSelectedTextEditor();

      if (selectedEditor != null) {
        int caretPosition = selectedEditor.getCaretModel().getOffset();
        PsiElement positionedElement = searchContext.getFile().findElementAt(caretPosition);

        if (positionedElement == null) {
          positionedElement = searchContext.getFile().findElementAt(caretPosition + 1);
        }

        if (positionedElement != null) {
          element = PsiTreeUtil.getParentOfType(
            positionedElement,
            PsiClass.class, PsiCodeBlock.class
          );
        }
      }
    }

    final PsiManager psimanager = PsiManager.getInstance(searchContext.getProject());
    final Project project = psimanager.getProject();
    final PsiCodeFragment file = createCodeFragment(project, text, element);
    final Document doc = PsiDocumentManager.getInstance(searchContext.getProject()).getDocument(file);
    DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(file, false);
    return UIUtil.createEditor(doc, searchContext.getProject(), true, true, getTemplateContextType());
  }

  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return JavaCodeContextType.class;
  }

  protected PsiCodeFragment createCodeFragment(Project project, String text, PsiElement context) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    return factory.createCodeBlockCodeFragment(text, context, true);
  }

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    MatchOptions matchOptions = options.getMatchOptions();
    FileType fileType = matchOptions.getFileType();
    PsiElement[] statements = MatcherImplUtil.createTreeFromText(
      matchOptions.getSearchPattern(),
      PatternTreeContext.Block,
      fileType,
      project
    );
    boolean searchIsExpression = false;

    for (PsiElement statement : statements) {
      if (statement.getLastChild() instanceof PsiErrorElement) {
        searchIsExpression = true;
        break;
      }
    }

    PsiElement[] statements2 = MatcherImplUtil.createTreeFromText(
      options.getReplacement(),
      PatternTreeContext.Block,
      fileType,
      project
    );
    boolean replaceIsExpression = false;

    for (PsiElement statement : statements2) {
      if (statement.getLastChild() instanceof PsiErrorElement) {
        replaceIsExpression = true;
        break;
      }
    }

    if (searchIsExpression != replaceIsExpression) {
      throw new UnsupportedPatternException(
        searchIsExpression ? SSRBundle.message("replacement.template.is.not.expression.error.message") :
        SSRBundle.message("search.template.is.not.expression.error.message")
      );
    }
  }
}
