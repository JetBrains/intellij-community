package com.intellij.structuralsearch;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecmal4.JSAttribute;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
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
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementInfoImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralSearchProfile extends StructuralSearchProfile {
  private static final String TYPED_VAR_PREFIX = "__$_";

  private PsiElementVisitor myLexicalNodesFilter;

  @NotNull
  @Override
  public CompiledPattern createCompiledPattern() {
    return new CompiledPattern() {
      @Override
      public String[] getTypedVarPrefixes() {
        return new String[] {JSStructuralSearchProfile.getTypedVarPrefix()};
      }

      @Override
      public boolean isTypedVar(String str) {
        String prefix = JSStructuralSearchProfile.getTypedVarPrefix();
        return str.startsWith(prefix);
      }
    };
  }

  @NotNull
  @Override
  public PsiElementVisitor getLexicalNodesFilter(@NotNull final LexicalNodesFilter filter) {
    if (myLexicalNodesFilter == null) {
      myLexicalNodesFilter = new PsiElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          super.visitElement(element);
          if (isLexicalNode(element)) {
            filter.setResult(true);
          }
        }
      };
    }
    return myLexicalNodesFilter;
  }

  @Override
  public void compile(PsiElement[] elements, @NotNull GlobalCompilingVisitor globalVisitor) {
    elements[0].getParent().accept(new MyJsCompilingVisitor(globalVisitor));
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

  private static boolean isLexicalNode(@NotNull PsiElement element) {
    if (element instanceof PsiWhiteSpace || element instanceof PsiErrorElement) {
      return true;
    }
    if (!(element instanceof LeafElement)) {
      return false;
    }
    IElementType type = ((LeafElement)element).getElementType();
    return type == JSTokenTypes.COMMA || type == JSTokenTypes.SEMICOLON;
  }

  @NotNull
  private static String getTypedVarPrefix() {
    return TYPED_VAR_PREFIX;
  }

  private static boolean isBlockElement(@NotNull PsiElement element) {
    return element instanceof JSBlockStatement || element instanceof JSFile;
  }

  private static boolean canBeVariable(PsiElement element) {
    if (element instanceof JSExpression ||
        element instanceof JSParameter ||
        element instanceof JSVariable ||
        (element instanceof LeafElement && ((LeafElement)element).getElementType() == JSTokenTypes.IDENTIFIER)) {
      return true;
    }
    return false;
  }

  private static boolean canBePatternVariable(PsiElement element) {
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
  private static MatchingStrategy getMatchingStrategy(PsiElement root) {
    if (root != null) {
      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(root);
      if (profile != null && profile.getLanguage(root) == JavaScriptSupportLoader.ECMA_SCRIPT_L4) {
        return JSMatchingStrategy.getInstanceEcma();
      }
    }
    return JSMatchingStrategy.getInstance();
  }

  @Override
  public boolean canProcess(@NotNull FileType fileType) {
    return fileType == JavaScriptSupportLoader.JAVASCRIPT;
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

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return new MyReplaceHandler(context.getProject());
  }

  private static class MyJsMatchingVisitor extends JSElementVisitor {
    private final GlobalMatchingVisitor myGlobalVisitor;

    private MyJsMatchingVisitor(GlobalMatchingVisitor globalVisitor) {
      myGlobalVisitor = globalVisitor;
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

      super.visitElement(element);

      if (canBePatternVariable(element)) {
        String text = element.getText();
        final boolean isTypedVar = myGlobalVisitor.getMatchContext().getPattern().isTypedVar(text);

        if (isTypedVar) {
          myGlobalVisitor.setResult(myGlobalVisitor.handleTypedElement(element, myGlobalVisitor.getElement()));
        }
        else {
          myGlobalVisitor.setResult(text.equals(myGlobalVisitor.getElement().getText()));
        }
      }
      else {
        myGlobalVisitor.setResult(myGlobalVisitor.matchSequentially(
          new FilteringNodeIterator(element.getFirstChild()),
          new FilteringNodeIterator(myGlobalVisitor.getElement().getFirstChild())
        ));
      }
    }

    @Override
    public void visitJSLiteralExpression(JSLiteralExpression l1) {
      final JSLiteralExpression l2 = (JSLiteralExpression)myGlobalVisitor.getElement();

      MatchingHandler handler = (MatchingHandler)l1.getUserData(CompiledPattern.HANDLER_KEY);

      if (handler instanceof SubstitutionHandler) {
        int offset = 0;
        int length = l2.getTextLength();
        final String text = l2.getText();

        if (length > 2 && text.charAt(0) == '"' && text.charAt(length - 1) == '"') {
          length--;
          offset++;
        }
        myGlobalVisitor.setResult(((SubstitutionHandler)handler).handle(l2, offset, length, myGlobalVisitor.getMatchContext()));
      }
      else if (handler != null) {
        myGlobalVisitor.setResult(handler.match(l1, l2, myGlobalVisitor.getMatchContext()));
      }
      else {
        myGlobalVisitor.setResult(l1.textMatches(l2));
      }
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
    public void visitJSClass(JSClass c1) {
      JSClass c2 = (JSClass)myGlobalVisitor.getElement();

      myGlobalVisitor.setResult(myGlobalVisitor.match(c1.getNameIdentifier(), c2.getNameIdentifier()) &&
                                myGlobalVisitor.matchSonsOptionally(c1.getAttributeList(), c2.getAttributeList()) &&
                                myGlobalVisitor.matchSonsInAnyOrder(c1.getExtendsList(), c2.getExtendsList()) &&
                                myGlobalVisitor.matchSonsInAnyOrder(c1.getImplementsList(), c2.getImplementsList()) &&
                                myGlobalVisitor.matchInAnyOrder(c1.getFields(), c2.getFields()) &&
                                myGlobalVisitor.matchInAnyOrder(c1.getFunctions(), c2.getFunctions()));
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

  private class MyJsCompilingVisitor extends PsiRecursiveElementVisitor {
    private final GlobalCompilingVisitor myGlobalVisitor;

    private MyJsCompilingVisitor(GlobalCompilingVisitor globalVisitor) {
      myGlobalVisitor = globalVisitor;
    }

    @Override
    public void visitElement(final PsiElement element) {
      doVisitElement(element);
      if (myGlobalVisitor.getContext().getSearchHelper().doOptimizing()) {
        if (element instanceof LeafElement &&
            ((LeafElement)element).getElementType() == JSTokenTypes.IDENTIFIER &&
             !myGlobalVisitor.getContext().getPattern().isTypedVar(element.getText())) {
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
      else if (element instanceof JSAttributeList) {
        if (visitJsAttributeList((JSAttributeList)element)) {
          return;
        }
      }
      if (element instanceof JSLiteralExpression) {
        visitJsLiteralExpression((JSLiteralExpression)element);
      }
      else if (element instanceof JSStatement) {
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

    private void initTopLevelElement(PsiElement element, CompiledPattern pattern) {
      MatchingStrategy strategy = null;

      for (PsiElement el = element.getFirstChild(); el != null; el = el.getNextSibling()) {
        if (GlobalCompilingVisitor.getFilter().accepts(el)) {
          if (el instanceof PsiWhiteSpace) {
            myGlobalVisitor.addLexicalNode(el);
          }
        }
        else {
          el.accept(this);
          if (myGlobalVisitor.getCodeBlockLevel() == 1) {
            MatchingStrategy newstrategy = getMatchingStrategy(el);
            if (strategy == null) {
              strategy = newstrategy;
            }
            else if (strategy.getClass() != newstrategy.getClass()) {
              throw new UnsupportedPatternException(SSRBundle.message("different.strategies.for.top.level.nodes.error.message"));
            }
            final MatchingHandler matchingHandler = myGlobalVisitor.getContext().getPattern().getHandler(el);
            myGlobalVisitor.getContext().getPattern().setHandler(el, new TopLevelMatchingHandler(matchingHandler));
          }
        }
      }

      if (myGlobalVisitor.getCodeBlockLevel() == 1) {
        if (strategy == null) {
          strategy = new MatchingStrategy() {
            public boolean continueMatching(PsiElement start) {
              return true;
            }

            @Override
            public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
              return false;
            }
          };
        }
        myGlobalVisitor.getContext().getPattern().setStrategy(strategy);
      }
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }

    private void doVisitElement(PsiElement element) {
      final CompiledPattern pattern = myGlobalVisitor.getContext().getPattern();
      if (isBlockElement(element)) {
        myGlobalVisitor.setCodeBlockLevel(myGlobalVisitor.getCodeBlockLevel() + 1);

        if (myGlobalVisitor.getCodeBlockLevel() == 1) {
          initTopLevelElement(element, pattern);
        }
        else {
          super.visitElement(element);
        }

        myGlobalVisitor.setCodeBlockLevel(myGlobalVisitor.getCodeBlockLevel() - 1);
        return;
      }
      if (canBePatternVariable(element)) {
        if (pattern.isRealTypedVar(element)) {
          myGlobalVisitor.handle(element);
          final MatchingHandler handler = pattern.getHandler(element);
          handler.setFilter(new NodeFilter() {
            public boolean accepts(PsiElement other) {
              return canBeVariable(other);
            }
          });
          return;
        }
      }
      super.visitElement(element);
    }

    private void visitJsLiteralExpression(JSLiteralExpression expression) {
      String value = expression.getText();

      if (value.length() > 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
        @Nullable MatchingHandler handler =
          myGlobalVisitor.processPatternStringWithFragments(value, GlobalCompilingVisitor.OccurenceKind.LITERAL);

        if (handler != null) {
          expression.putUserData(CompiledPattern.HANDLER_KEY, handler);
        }
      }
    }

    private boolean visitJsReferenceExpression(JSExpressionStatement element) {
      if (!isOnlyTopElement(element)) return false;

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

    private boolean visitJsAttributeList(JSAttributeList attrList) {
      if (!isOnlyTopElement(attrList)) return false;

      final JSAttribute[] attributes = attrList.getAttributes();
      if (attributes.length != 1) {
        return false;
      }

      final JSAttribute attribute = attributes[0];
      if (!attribute.getText().equals(attrList.getText())) {
        return false;
      }

      MatchingHandler handler = new MatchingHandler() {
        public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
          if (!super.match(patternNode, matchedNode, context)) {
            return false;
          }
          JSAttribute jsAttr = ((JSAttributeList)patternNode).getAttributes()[0];
          return context.getMatcher().match(jsAttr, matchedNode);
        }
      };
      myGlobalVisitor.setHandler(attrList, handler);

      handler.setFilter(new NodeFilter() {
        public boolean accepts(PsiElement element) {
          return element instanceof JSAttribute;
        }
      });
      return true;
    }

    private boolean isOnlyTopElement(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof JSFile)) {
        return false;
      }
      if (parent.getChildren().length != 1) {
        return false;
      }
      return true;
    }
  }

  private static class MyReplaceHandler extends StructuralReplaceHandler {
    private final Project myProject;
    private final Map<ReplacementInfo, RangeMarker> myRangeMarkers = new HashMap<ReplacementInfo, RangeMarker>();

    private MyReplaceHandler(Project project) {
      myProject = project;
    }

    public void replace(ReplacementInfo info) {
      if (info.getMatchesCount() == 0) return;
      assert info instanceof ReplacementInfoImpl;
      PsiElement element = info.getMatch(0);
      if (element == null) return;
      PsiFile file = element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
      assert file != null;
      RangeMarker rangeMarker = myRangeMarkers.get(info);
      Document document = rangeMarker.getDocument();
      document.replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), info.getReplacement());
      PsiDocumentManager.getInstance(element.getProject()).commitDocument(document);
    }

    @Override
    public void prepare(ReplacementInfo info) {
      assert info instanceof ReplacementInfoImpl;
      MatchResult result = ((ReplacementInfoImpl)info).getMatchResult();
      PsiElement element = result.getMatch();
      PsiFile file = element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      TextRange textRange = result.getMatchRef().getElement().getTextRange();
      assert textRange != null;
      RangeMarker rangeMarker = document.createRangeMarker(textRange);
      rangeMarker.setGreedyToLeft(true);
      rangeMarker.setGreedyToRight(true);
      myRangeMarkers.put(info, rangeMarker);
    }
  }
}
