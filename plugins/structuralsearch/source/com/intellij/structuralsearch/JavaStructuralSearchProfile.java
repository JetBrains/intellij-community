package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.JavaCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.filters.JavaLexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaStructuralSearchProfile extends StructuralSearchProfile {
  private JavaLexicalNodesFilter myJavaLexicalNodesFilter;

  public String getText(PsiElement match, int start,int end) {
    if (match instanceof PsiIdentifier) {
      PsiElement parent = match.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && !(parent instanceof PsiExpression)) {
        match = parent; // care about generic
      }
    }
    final String matchText = match.getText();
    if (start==0 && end==-1) return matchText;
    return matchText.substring(start,end == -1? matchText.length():end);
  }

  public Class getElementContextByPsi(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      element = element.getParent();
    }

    if (element instanceof PsiMember) {
      return PsiMember.class;
    } else {
      return PsiExpression.class;
    }
  }

  public String getTypedVarString(final PsiElement element) {
    String text;

    if (element instanceof PsiNamedElement) {
      text = ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PsiAnnotation) {
      PsiJavaCodeReferenceElement referenceElement = ((PsiAnnotation)element).getNameReferenceElement();
      text = referenceElement == null ? null : referenceElement.getQualifiedName();
    }
    else if (element instanceof PsiNameValuePair) {
      text = ((PsiNameValuePair)element).getName();
    }
    else {
      text = element.getText();
      if (StringUtil.startsWithChar(text, '@')) {
        text = text.substring(1);
      }
      if (StringUtil.endsWithChar(text, ';')) text = text.substring(0, text.length() - 1);
      else if (element instanceof PsiExpressionStatement) {
        int i = text.indexOf(';');
        if (i != -1) text = text.substring(0,i);
      }
    }

    if (text==null) text = element.getText();

    return text;
  }

  @Override
  public PsiElement updateCurrentNode(PsiElement targetNode) {
    if (targetNode instanceof PsiCodeBlock && ((PsiCodeBlock)targetNode).getStatements().length == 1) {
      PsiElement targetNodeParent = targetNode.getParent();
      if (targetNodeParent instanceof PsiBlockStatement) {
        targetNodeParent = targetNodeParent.getParent();
      }

      if (targetNodeParent instanceof PsiIfStatement || targetNodeParent instanceof PsiLoopStatement) {
        targetNode = targetNodeParent;
      }
    }
    return targetNode;
  }

  @Override
  public PsiElement extendMatchedByDownUp(PsiElement targetNode) {
    if (targetNode instanceof PsiIdentifier) {
      targetNode = targetNode.getParent();
      final PsiElement parent = targetNode.getParent();
      if (parent instanceof PsiTypeElement || parent instanceof PsiStatement) targetNode = parent;
    }
    return targetNode;
  }

  @Override
  public PsiElement extendMatchOnePsiFile(PsiElement file) {
    if (file instanceof PsiIdentifier) {
      // Searching in previous results
      file = file.getParent();
    }
    return file;
  }

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
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    if (context == PatternTreeContext.Block) {
      PsiElement element = elementFactory.createStatementFromText("{\n" + text + "\n}", null);
      final PsiElement[] children = ((PsiBlockStatement)element).getCodeBlock().getChildren();
      final int extraChildCount = 4;

      if (children.length > extraChildCount) {
        PsiElement[] result = new PsiElement[children.length - extraChildCount];
        final int extraChildStart = 2;
        System.arraycopy(children, extraChildStart, result, 0, children.length - extraChildCount);
        return result;
      }
      else {
        return PsiElement.EMPTY_ARRAY;
      }
    }
    else if (context == PatternTreeContext.Class) {
      PsiElement element = elementFactory.createStatementFromText("class A {\n" + text + "\n}", null);
      PsiClass clazz = (PsiClass)((PsiDeclarationStatement)element).getDeclaredElements()[0];
      PsiElement startChild = clazz.getLBrace();
      if (startChild != null) startChild = startChild.getNextSibling();

      PsiElement endChild = clazz.getRBrace();
      if (endChild != null) endChild = endChild.getPrevSibling();
      if (startChild == endChild) return PsiElement.EMPTY_ARRAY; // nothing produced

      final List<PsiElement> result = new ArrayList<PsiElement>(3);
      assert startChild != null;
      for (PsiElement el = startChild.getNextSibling(); el != endChild && el != null; el = el.getNextSibling()) {
        if (el instanceof PsiErrorElement) continue;
        result.add(el);
      }

      return PsiUtilCore.toPsiElementArray(result);
    }
    else {
      return PsiFileFactory.getInstance(project).createFileFromText("__dummy.java", text).getChildren();
    }
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
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    return factory.createCodeBlockCodeFragment(text, context, true);
  }

  @Override
  public void checkSearchPattern(Project project, MatchOptions options) {
    class ValidatingVisitor extends JavaRecursiveElementWalkingVisitor {
      @Override public void visitAnnotation(PsiAnnotation annotation) {
        final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();

        if (nameReferenceElement == null ||
            !nameReferenceElement.getText().equals(MatchOptions.MODIFIER_ANNOTATION_NAME)) {
          return;
        }

        for(PsiNameValuePair pair:annotation.getParameterList().getAttributes()) {
          final PsiAnnotationMemberValue value = pair.getValue();

          if (value instanceof PsiArrayInitializerMemberValue) {
            for(PsiAnnotationMemberValue v:((PsiArrayInitializerMemberValue)value).getInitializers()) {
              final String name = StringUtil.stripQuotesAroundValue(v.getText());
              checkModifier(name);
            }

          } else if (value != null) {
            final String name = StringUtil.stripQuotesAroundValue(value.getText());
            checkModifier(name);
          }
        }
      }

      private void checkModifier(final String name) {
        if (!MatchOptions.INSTANCE_MODIFIER_NAME.equals(name) &&
            !MatchOptions.PACKAGE_LOCAL_MODIFIER_NAME.equals(name) &&
            Arrays.binarySearch(JavaMatchingVisitor.MODIFIERS, name) < 0
          ) {
          throw new MalformedPatternException(SSRBundle.message("invalid.modifier.type",name));
        }
      }
    }
    ValidatingVisitor visitor = new ValidatingVisitor();
    final NodeIterator nodes = PatternCompiler.compilePattern(project, options).getNodes();
    while(nodes.hasNext()) {
      nodes.current().accept( visitor );
      nodes.advance();
    }
    nodes.reset();

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

  @Override
  public LanguageFileType getDefaultFileType(LanguageFileType currentDefaultFileType) {
    return StdFileTypes.JAVA;
  }
}
