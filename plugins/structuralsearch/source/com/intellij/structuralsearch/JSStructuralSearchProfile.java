package com.intellij.structuralsearch;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.OptimizingSearchHelper;
import com.intellij.structuralsearch.impl.matcher.filters.DefaultFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralSearchProfile extends TokenBasedProfile {
  private static final String TYPED_VAR_PREFIX = "__$_";
  private static String AS_SEARCH_VARIANT = "actionscript";

  @Override
  public void compile(PsiElement element, @NotNull GlobalCompilingVisitor globalVisitor) {
    element.accept(new MyJsCompilingVisitor(globalVisitor));
  }

  private static PsiElement extractOnlyStatement(JSBlockStatement e) {
    JSStatement[] statements = e.getStatements();
    if (statements.length == 1) {
      return statements[0];
    }
    return e;
  }

  @NotNull
  @Override
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new MyJsMatchingVisitor(globalVisitor);
  }

  @Override
  protected boolean isLexicalNode(@NotNull PsiElement element) {
    if (super.isLexicalNode(element)) {
      return true;
    }
    if (!(element instanceof LeafElement)) {
      return false;
    }
    IElementType type = ((LeafElement)element).getElementType();
    return type == JSTokenTypes.COMMA || type == JSTokenTypes.SEMICOLON;
  }

  @NotNull
  @Override
  protected String getTypedVarPrefix() {
    return TYPED_VAR_PREFIX;
  }

  @Override
  protected boolean isBlockElement(@NotNull PsiElement element) {
    return element instanceof JSBlockStatement || element instanceof JSFile;
  }

  @Override
  protected boolean canBeVariable(PsiElement element) {
    if (element instanceof JSExpression ||
        element instanceof JSParameter ||
        element instanceof JSVariable ||
        (element instanceof LeafElement && ((LeafElement)element).getElementType() == JSTokenTypes.IDENTIFIER)) {
      return true;
    }
    return false;
  }

  @Override
  protected boolean canBePatternVariable(PsiElement element) {
    PsiElement child = element.getFirstChild();
    if (child == null) {
      return true;
    }
    if (element instanceof JSReferenceExpression || element instanceof JSParameter) {
      ASTNode node = child.getNode();
      if (node != null) {
        return node.getElementType() == JSTokenTypes.IDENTIFIER && child.getNextSibling() == null;
      }
    }
    return false;
  }

  @NotNull
  @Override
  protected MatchingStrategy getMatchingStrategy(PsiElement root) {
    if (root != null) {
      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(root);
      if (profile != null && profile.getLanguage(root) == JavaScriptSupportLoader.ECMA_SCRIPT_L4) {
        return JSMatchingStrategy.getInstanceEcma();
      }
    }
    return JSMatchingStrategy.getInstance();
  }

  @NotNull
  @Override
  public String[] getFileTypeSearchVariants() {
    return new String[]{getTypeName(JavaScriptSupportLoader.JAVASCRIPT), AS_SEARCH_VARIANT};
  }

  @NotNull
  @Override
  public FileType[] getFileTypes() {
    return new FileType[]{JavaScriptSupportLoader.JAVASCRIPT};
  }

  @Override
  public String getFileExtensionBySearchVariant(@NotNull String searchVariant) {
    if (searchVariant.equals(AS_SEARCH_VARIANT)) {
      return JavaScriptSupportLoader.ECMA_SCRIPT_L4_FILE_EXTENSION;
    }
    return JavaScriptSupportLoader.JAVASCRIPT.getDefaultExtension();
  }

  @Override
  public String getSearchVariant(@NotNull FileType fileType, @Nullable String extension) {
    if (JavaScriptSupportLoader.ECMA_SCRIPT_L4_FILE_EXTENSION.equals(extension)) {
      return AS_SEARCH_VARIANT;
    }
    return super.getSearchVariant(fileType, extension);
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language instanceof JavascriptLanguage || language instanceof JSLanguageDialect;
  }

  @Override
  public boolean isMyFile(PsiFile file, @NotNull Language lang, Language... patternLanguages) {
    if (file != null && JavaScriptSupportLoader.isFlexMxmFile(file) && ArrayUtil.find(patternLanguages, JavaScriptSupportLoader.ECMA_SCRIPT_L4) >= 0) {
      return true;
    }
    return super.isMyFile(file, lang, patternLanguages);
  }

  private boolean containsFunctionOrClass(PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (element instanceof JSFunction || element instanceof JSClass) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    MatchOptions matchOptions = options.getMatchOptions();
    FileType fileType = matchOptions.getFileType();
    String pattern = matchOptions.getSearchPattern();
    PsiElement[] elements = createPatternTree(pattern, PatternTreeContext.File, fileType, project, false);

    for (PsiElement element : elements) {
      if (element instanceof JSExpressionStatement || element instanceof JSVarStatement) {
        PsiElement lastChild = element.getLastChild();
        if (!(lastChild instanceof LeafPsiElement && ((LeafPsiElement)lastChild).getElementType() == JSTokenTypes.SEMICOLON)) {
          throw new UnsupportedPatternException(SSRBundle.message("replacement.template.expression.not.supported", fileType.getName()));
        }
      }
    }
  }

  /*@NotNull
  @Override
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @NotNull String extension,
                                        @NotNull Project project,
                                        boolean physical) {
    if (context == PatternTreeContext.Block) {
      text = "function f() {" + text + "}";
      PsiElement[] elements = super.createPatternTree(text, context, fileType, extension, project, physical);
      for (PsiElement element : elements) {
        if (element instanceof JSFunction) {
          JSSourceElement[] sourceElements = ((JSFunction)element).getBody();
          if (sourceElements.length == 1 && sourceElements[0] instanceof JSBlockStatement) {
            return ((JSBlockStatement)sourceElements[0]).getStatements();
          }
        }
      }
      assert false;
    }
    return super.createPatternTree(text, context, fileType, extension, project, physical);
  }*/

  @NotNull
  @Override
  public Language getLanguage(PsiElement element) {
    return getLanguageForElement(element);
  }

  public static Language getLanguageForElement(PsiElement element) {
    if (element.getLanguage() instanceof JavascriptLanguage && !(element instanceof JSFile)) {
      PsiFile file = element.getContainingFile();
      if (file instanceof JSFile) {
        Language fileLanguage = file.getLanguage();
        if (fileLanguage instanceof JSLanguageDialect) {
          return fileLanguage;
        }
      }
    }
    return element.getLanguage();
  }

  private class MyJsMatchingVisitor extends JSElementVisitor {
    private final GlobalMatchingVisitor myGlobalVisitor;
    private final MyMatchingVisitor myBaseVisitor;

    private MyJsMatchingVisitor(GlobalMatchingVisitor globalVisitor) {
      myGlobalVisitor = globalVisitor;
      myBaseVisitor = new MyMatchingVisitor(globalVisitor);
    }

    @Override
    public void visitElement(PsiElement element) {
      PsiElement e = myGlobalVisitor.getElement();
      if (e instanceof JSBlockStatement) {
        JSStatement[] statements = ((JSBlockStatement)e).getStatements();
        if (statements.length == 1) {
          myGlobalVisitor.setResult(myGlobalVisitor.match(element, statements[0]));
          return;
        }
      }
      myBaseVisitor.visitElement(element);
    }

    @Override
    public void visitJSFunctionDeclaration(JSFunction f1) {
      final JSFunction f2 = (JSFunction)myGlobalVisitor.getElement();

      myGlobalVisitor.setResult(f1.getKind() == f2.getKind() &&
                                myGlobalVisitor.match(f1.getNameIdentifier(), f2.getNameIdentifier()) &&
                                myGlobalVisitor.matchSonsOptionally(f1.getAttributeList(), f2.getAttributeList()) &&
                                myGlobalVisitor.matchSons(f1.getParameterList(), f2.getParameterList()) &&
                                myGlobalVisitor.matchOptionally(f1.getReturnTypeElement(), f2.getReturnTypeElement()) &&
                                myGlobalVisitor.matchOptionally(f1.getBody(), f2.getBody()));
    }

    @Override
    public void visitJSVarStatement(JSVarStatement vs1) {
      JSVarStatement vs2 = (JSVarStatement)myGlobalVisitor.getElement();

      PsiElement firstChild1 = vs1.getFirstChild();
      PsiElement firstChild2 = vs2.getFirstChild();

      boolean result = true;

      if (firstChild1 instanceof JSAttributeList && firstChild1.getTextLength() > 0) {
        result = firstChild2 instanceof JSAttributeList ? myGlobalVisitor.match(firstChild1, firstChild2) : false;
      }

      myGlobalVisitor.setResult(result && myGlobalVisitor.matchSequentially(vs1.getVariables(), vs2.getVariables()));
    }

    @Override
    public void visitJSIfStatement(JSIfStatement if1) {
      JSIfStatement if2 = (JSIfStatement)myGlobalVisitor.getElement();

      myGlobalVisitor.setResult(myGlobalVisitor.match(if1.getCondition(), if2.getCondition()) &&
                                myGlobalVisitor.matchOptionally(if1.getThen(), if2.getThen()) &&
                                myGlobalVisitor.matchOptionally(if1.getElse(), if2.getElse()));
    }

    @Override
    public void visitJSForStatement(JSForStatement for1) {
      JSForStatement for2 = (JSForStatement)myGlobalVisitor.getElement();

      myGlobalVisitor.setResult(myGlobalVisitor.match(for1.getVarDeclaration(), for2.getVarDeclaration()) &&
                                myGlobalVisitor.match(for1.getInitialization(), for2.getInitialization()) &&
                                myGlobalVisitor.match(for1.getCondition(), for2.getCondition()) &&
                                myGlobalVisitor.match(for1.getUpdate(), for2.getUpdate()) &&
                                myGlobalVisitor.matchOptionally(for1.getBody(), for2.getBody()));
    }

    @Override
    public void visitJSForInStatement(JSForInStatement for1) {
      JSForInStatement for2 = (JSForInStatement)myGlobalVisitor.getElement();

      myGlobalVisitor.setResult(myGlobalVisitor.match(for1.getDeclarationStatement(), for2.getDeclarationStatement()) &&
                                myGlobalVisitor.match(for1.getVariableExpression(), for2.getVariableExpression()) &&
                                myGlobalVisitor.match(for1.getCollectionExpression(), for1.getCollectionExpression()) &&
                                myGlobalVisitor.matchOptionally(for1.getBody(), for2.getBody()));
    }

    @Override
    public void visitJSDoWhileStatement(JSDoWhileStatement while1) {
      JSDoWhileStatement while2 = (JSDoWhileStatement)myGlobalVisitor.getElement();

      myGlobalVisitor.setResult(myGlobalVisitor.match(while1.getCondition(), while2.getCondition()) &&
                                myGlobalVisitor.matchOptionally(while1.getBody(), while2.getBody()));
    }

    @Override
    public void visitJSWhileStatement(JSWhileStatement while1) {
      JSWhileStatement while2 = (JSWhileStatement)myGlobalVisitor.getElement();

      myGlobalVisitor.setResult(myGlobalVisitor.match(while1.getCondition(), while2.getCondition()) &&
                                myGlobalVisitor.matchOptionally(while1.getBody(), while2.getBody()));
    }

    @Override
    public void visitJSBlock(JSBlockStatement block1) {
      PsiElement element = myGlobalVisitor.getElement();
      PsiElement[] statements2 =
        element instanceof JSBlockStatement ? ((JSBlockStatement)element).getStatements() : new PsiElement[]{element};

      myGlobalVisitor.setResult(myGlobalVisitor.matchSequentially(block1.getStatements(), statements2));
    }
  }

  private class MyJsCompilingVisitor extends MyCompilingVisitor {

    protected MyJsCompilingVisitor(GlobalCompilingVisitor globalVisitor) {
      super(globalVisitor);
    }

    @Override
    public void visitElement(final PsiElement element) {
      super.visitElement(element);
      if (myGlobalVisitor.getContext().getSearchHelper().doOptimizing()) {
        if (element instanceof LeafElement && ((LeafElement)element).getElementType() == JSTokenTypes.IDENTIFIER) {
          OptimizingSearchHelper helper = myGlobalVisitor.getContext().getSearchHelper();
          boolean added = helper.addWordToSearchInText(element.getText());
          added = helper.addWordToSearchInCode(element.getText()) || added;
          if (added) {
            helper.endTransaction();
          }
        }
      }
      if (element instanceof JSExpressionStatement) {
        if (visitJsReferenceExpression((JSExpressionStatement)element)) {
          return;
        }
      }
      if (element instanceof JSStatement) {
        CompiledPattern pattern = myGlobalVisitor.getContext().getPattern();
        MatchingHandler handler = pattern.getHandler(element);
        if (handler.getFilter() == null) {
          handler.setFilter(new NodeFilter() {
            public boolean accepts(PsiElement e) {
              if (e instanceof JSBlockStatement) {
                e = extractOnlyStatement((JSBlockStatement)e);
              }
              return DefaultFilter.accepts(
                element instanceof JSBlockStatement ? extractOnlyStatement((JSBlockStatement)element) : element, e);
            }
          });
        }
      }
    }

    private boolean visitJsReferenceExpression(JSExpressionStatement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof JSFile)) {
        return false;
      }
      if (parent.getChildren().length != 1) {
        return false;
      }
      JSExpression expression = element.getExpression();
      String expText = expression.getText();
      if (expText == null || !expText.equals(element.getText())) {
        return false;
      }

      MatchingHandler handler = new MatchingHandler() {
        public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
          if (!super.match(patternNode, matchedNode, context)) {
            return false;
          }
          JSExpression jsExpression = ((JSExpressionStatement)patternNode).getExpression();
          return context.getMatcher().match(jsExpression, matchedNode);
        }
      };
      myGlobalVisitor.setHandler(element, handler);
      handler.setFilter(new NodeFilter() {
        public boolean accepts(PsiElement element) {
          return element instanceof JSExpression ||
                 (element instanceof LeafElement && ((LeafElement)element).getElementType() == JSTokenTypes.IDENTIFIER);
        }
      });
      return true;
    }
  }
}
