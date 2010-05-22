package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaCompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.impl.matcher.filters.*;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.impl.matcher.strategies.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.structuralsearch.MatchOptions.INSTANCE_MODIFIER_NAME;
import static com.intellij.structuralsearch.MatchOptions.MODIFIER_ANNOTATION_NAME;
import static com.intellij.structuralsearch.MatchOptions.PACKAGE_LOCAL_MODIFIER_NAME;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaCompilingVisitor extends JavaRecursiveElementWalkingVisitor {
  private final GlobalCompilingVisitor myCompilingVisitor;

  @NonNls private static final String SUBSTITUTION_PATTERN_STR = "\\b(__\\$_\\w+)\\b";
  private static Pattern ourSubstitutionPattern = Pattern.compile(SUBSTITUTION_PATTERN_STR);

  @NonNls private static final String WORD_SEARCH_PATTERN_STR = ".*?\\b(.+?)\\b.*?";
  private static final Pattern ourWordSearchPattern = Pattern.compile(WORD_SEARCH_PATTERN_STR);

  private static final Set<String> ourReservedWords = new HashSet<String>(
    Arrays.asList(MODIFIER_ANNOTATION_NAME, INSTANCE_MODIFIER_NAME, PACKAGE_LOCAL_MODIFIER_NAME)
  );

  private static final Pattern ourAlternativePattern = Pattern.compile("^\\((.+)\\)$");
  @NonNls private static final String COMMENT = "\\s*(__\\$_\\w+)\\s*";
  private static Pattern ourPattern = Pattern.compile("//" + COMMENT, Pattern.DOTALL);
  private static Pattern ourPattern2 = Pattern.compile("/\\*" + COMMENT + "\\*/", Pattern.DOTALL);
  private static Pattern ourPattern3 = Pattern.compile("/\\*\\*" + COMMENT + "\\*/", Pattern.DOTALL);

  public JavaCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
  }

  @Override
  public void visitDocTag(PsiDocTag psiDocTag) {
    super.visitDocTag(psiDocTag);

    NodeIterator sons = new DocValuesIterator(psiDocTag.getFirstChild());
    while (sons.hasNext()) {
      myCompilingVisitor.setHandler(sons.current(), new DocDataHandler());
      sons.advance();
    }
  }

  @Override
  public void visitComment(PsiComment comment) {
    super.visitComment(comment);

    final String text = comment.getText();
    Matcher matcher = ourPattern.matcher(text);
    boolean matches = false;
    if (!matcher.matches()) {
      matcher = ourPattern2.matcher(text);

      if (!matcher.matches()) {
        matcher = ourPattern3.matcher(text);
      }
      else {
        matches = true;
      }
    }
    else {
      matches = true;
    }

    if (matches || matcher.matches()) {
      String str = matcher.group(1);
      comment.putUserData(CompiledPattern.HANDLER_KEY, str);

      GlobalCompilingVisitor.setFilter(
        myCompilingVisitor.getContext().getPattern().getHandler(comment),
        CommentFilter.getInstance()
      );

      SubstitutionHandler handler = (SubstitutionHandler)myCompilingVisitor.getContext().getPattern().getHandler(str);

      if (handler.getPredicate() != null) {
        ((RegExpPredicate)handler.getPredicate()).setMultiline(true);
      }

      RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);
      if (!IsNotSuitablePredicate(predicate, handler)) {
        processTokenizedName(predicate.getRegExp(), true, OccurenceKind.COMMENT);
      }

      matches = true;
    }

    if (!matches) {
      MatchingHandler handler = processPatternStringWithFragments(text, OccurenceKind.COMMENT);
      if (handler != null) comment.putUserData(CompiledPattern.HANDLER_KEY, handler);
    }
  }

  @Override
  public void visitLiteralExpression(PsiLiteralExpression expression) {
    String value = expression.getText();

    if (value.length() > 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
      @Nullable MatchingHandler handler =
        processPatternStringWithFragments(value, OccurenceKind.LITERAL);

      if (handler != null) {
        expression.putUserData(CompiledPattern.HANDLER_KEY, handler);
      }
    }
    super.visitLiteralExpression(expression);
  }

  @Override
  public void visitClassInitializer(final PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    PsiStatement[] psiStatements = initializer.getBody().getStatements();
    if (psiStatements.length == 1 && psiStatements[0] instanceof PsiExpressionStatement) {
      MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(psiStatements[0]);

      if (handler instanceof SubstitutionHandler) {
        myCompilingVisitor.getContext().getPattern().setHandler(initializer, new SubstitutionHandler((SubstitutionHandler)handler));
      }
    }
  }

  @Override
  public void visitField(PsiField psiField) {
    super.visitField(psiField);
    CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiField);

    if (needsSupers(psiField, handler)) {
      assert pattern instanceof JavaCompiledPattern;
      ((JavaCompiledPattern)pattern).setRequestsSuperFields(true);
    }
  }

  @Override
  public void visitMethod(PsiMethod psiMethod) {
    super.visitMethod(psiMethod);
    CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiMethod);

    if (needsSupers(psiMethod, handler)) {
      assert pattern instanceof JavaCompiledPattern;
      ((JavaCompiledPattern)pattern).setRequestsSuperMethods(true);
    }

    GlobalCompilingVisitor.setFilter(handler, MethodFilter.getInstance());
    handleReferenceText(psiMethod.getName());
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression reference) {
    visitElement(reference);

    boolean typedVarProcessed = false;
    final PsiElement referenceParent = reference.getParent();

    if ((myCompilingVisitor.getContext().getPattern().isRealTypedVar(reference)) &&
        reference.getQualifierExpression() == null &&
        !(referenceParent instanceof PsiExpressionStatement)
      ) {
      // typed var for expression (but not top level)
      MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(reference);
      GlobalCompilingVisitor.setFilter(handler, ExpressionFilter.getInstance());
      typedVarProcessed = true;
    }

    if (!(referenceParent instanceof PsiMethodCallExpression)) {
      handleReference(reference);
    }

    MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(reference);

    // We want to merge qname related to class to find it in any form
    final String referencedName = reference.getReferenceName();

    if (!typedVarProcessed &&
        !(handler instanceof SubstitutionHandler)) {
      final PsiElement resolve = reference.resolve();

      PsiElement referenceQualifier = reference.getQualifier();
      if (resolve instanceof PsiClass ||
          (resolve == null &&
           ((referencedName != null && Character.isUpperCase(referencedName.charAt(0))) ||
            referenceQualifier == null
           )
          )
        ) {
        boolean hasNoNestedSubstitutionHandlers = false;
        PsiExpression qualifier;
        PsiReferenceExpression currentReference = reference;

        while ((qualifier = currentReference.getQualifierExpression()) != null) {
          if (!(qualifier instanceof PsiReferenceExpression) ||
              myCompilingVisitor.getContext().getPattern().getHandler(qualifier) instanceof SubstitutionHandler
            ) {
            hasNoNestedSubstitutionHandlers = true;
            break;
          }
          currentReference = (PsiReferenceExpression)qualifier;
        }
        if (!hasNoNestedSubstitutionHandlers) {
          createAndSetSubstitutionHandlerFromReference(
            reference,
            resolve != null ? ((PsiClass)resolve).getQualifiedName() : reference.getText()
          );
        }
      }
      else if (referenceQualifier != null && reference.getParent() instanceof PsiExpressionStatement) {
        //Handler qualifierHandler = context.pattern.getHandler(referenceQualifier);
        //if (qualifierHandler instanceof SubstitutionHandler &&
        //    !context.pattern.isRealTypedVar(reference)
        //   ) {
        //  createAndSetSubstitutionHandlerFromReference(reference, referencedName);
        //
        //  SubstitutionHandler substitutionHandler = (SubstitutionHandler)qualifierHandler;
        //  RegExpPredicate expPredicate = Handler.getSimpleRegExpPredicate(substitutionHandler);
        //  //if (expPredicate != null)
        //  //  substitutionHandler.setPredicate(new ExprTypePredicate(expPredicate.getRegExp(), null, true, true, false));
        //}
      }
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    handleReference(expression.getMethodExpression());
    super.visitMethodCallExpression(expression);
  }

  @Override
  public void visitBlockStatement(PsiBlockStatement psiBlockStatement) {
    super.visitBlockStatement(psiBlockStatement);
    myCompilingVisitor.getContext().getPattern().getHandler(psiBlockStatement).setFilter(BlockFilter.getInstance());
  }

  @Override
  public void visitVariable(PsiVariable psiVariable) {
    super.visitVariable(psiVariable);
    myCompilingVisitor.getContext().getPattern().getHandler(psiVariable).setFilter(VariableFilter.getInstance());
    handleReferenceText(psiVariable.getName());
  }

  @Override
  public void visitDeclarationStatement(PsiDeclarationStatement psiDeclarationStatement) {
    super.visitDeclarationStatement(psiDeclarationStatement);

    if (psiDeclarationStatement.getFirstChild() instanceof PsiTypeElement) {
      // search for expression or symbol
      final PsiJavaCodeReferenceElement reference =
        ((PsiTypeElement)psiDeclarationStatement.getFirstChild()).getInnermostComponentReferenceElement();

      if (reference != null &&
          (myCompilingVisitor.getContext().getPattern().isRealTypedVar(reference.getReferenceNameElement())) &&
          reference.getParameterList().getTypeParameterElements().length > 0
        ) {
        myCompilingVisitor.setHandler(psiDeclarationStatement, new TypedSymbolHandler());
        final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(psiDeclarationStatement);
        // typed symbol
        handler.setFilter(
          TypedSymbolNodeFilter.getInstance()
        );

        final PsiTypeElement[] params = reference.getParameterList().getTypeParameterElements();
        for (PsiTypeElement param : params) {
          if (param.getInnermostComponentReferenceElement() != null &&
              (myCompilingVisitor.getContext().getPattern().isRealTypedVar(
                param.getInnermostComponentReferenceElement().getReferenceNameElement()))
            ) {
            myCompilingVisitor.getContext().getPattern().getHandler(param).setFilter(
              TypeParameterFilter.getInstance()
            );
          }
        }

        return;
      }
    }

    final MatchingHandler handler = new DeclarationStatementHandler();
    myCompilingVisitor.getContext().getPattern().setHandler(psiDeclarationStatement, handler);
    PsiElement previousNonWhiteSpace = psiDeclarationStatement.getPrevSibling();

    while (previousNonWhiteSpace instanceof PsiWhiteSpace) {
      previousNonWhiteSpace = previousNonWhiteSpace.getPrevSibling();
    }

    if (previousNonWhiteSpace instanceof PsiComment) {
      ((DeclarationStatementHandler)handler)
        .setCommentHandler(myCompilingVisitor.getContext().getPattern().getHandler(previousNonWhiteSpace));

      myCompilingVisitor.getContext().getPattern().setHandler(
        previousNonWhiteSpace,
        handler
      );
    }

    // detect typed symbol, it will have no variable
    handler.setFilter(DeclarationFilter.getInstance());
  }

  @Override
  public void visitDocComment(PsiDocComment psiDocComment) {
    super.visitDocComment(psiDocComment);
    myCompilingVisitor.getContext().getPattern().getHandler(psiDocComment).setFilter(JavaDocFilter.getInstance());
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);

    if (reference.getParent() != null &&
        reference.getParent().getParent() instanceof PsiClass) {
      GlobalCompilingVisitor.setFilter(myCompilingVisitor.getContext().getPattern().getHandler(reference), TypeFilter.getInstance());
    }

    handleReference(reference);
  }

  @Override
  public void visitClass(PsiClass psiClass) {
    super.visitClass(psiClass);
    CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiClass);

    if (needsSupers(psiClass, handler)) {
      ((JavaCompiledPattern)pattern).setRequestsSuperInners(true);
    }

    GlobalCompilingVisitor.setFilter(handler, ClassFilter.getInstance());

    for (PsiElement element = psiClass.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiTypeElement && element.getNextSibling() instanceof PsiErrorElement) {
        // found match that
        psiClass.putUserData(CompiledPattern.ALL_CLASS_CONTENT_VAR_KEY, element);
      }
    }
  }

  private SubstitutionHandler createAndSetSubstitutionHandlerFromReference(final PsiElement expr, final String referenceText) {
    final SubstitutionHandler substitutionHandler = new SubstitutionHandler("__" + referenceText.replace('.', '_'), false, 1, 1, false);
    substitutionHandler.setPredicate(new RegExpPredicate(referenceText.replaceAll("\\.", "\\\\."), true, null, false, false));
    myCompilingVisitor.getContext().getPattern().setHandler(expr, substitutionHandler);
    return substitutionHandler;
  }

  @Override
  public void visitExpressionStatement(PsiExpressionStatement expr) {
    myCompilingVisitor.handle(expr);

    super.visitExpressionStatement(expr);

    final PsiElement child = expr.getLastChild();
    if (!(child instanceof PsiJavaToken) && !(child instanceof PsiComment)) {
      // search for expression or symbol
      final PsiElement reference = expr.getFirstChild();
      MatchingHandler referenceHandler = myCompilingVisitor.getContext().getPattern().getHandler(reference);

      if (referenceHandler instanceof SubstitutionHandler &&
          (reference instanceof PsiReferenceExpression)
        ) {
        // symbol
        myCompilingVisitor.getContext().getPattern().setHandler(expr, referenceHandler);
        referenceHandler.setFilter(
          SymbolNodeFilter.getInstance()
        );

        myCompilingVisitor.setHandler(expr, new SymbolHandler((SubstitutionHandler)referenceHandler));
      }
      else if (reference instanceof PsiLiteralExpression) {
        MatchingHandler handler = new ExpressionHandler();
        myCompilingVisitor.setHandler(expr, handler);
        handler.setFilter(ConstantFilter.getInstance());
      }
      else {
        // just expression
        MatchingHandler handler;
        myCompilingVisitor.setHandler(expr, handler = new ExpressionHandler());

        handler.setFilter(ExpressionFilter.getInstance());
      }
    }
    else if (expr.getExpression() instanceof PsiReferenceExpression &&
             (myCompilingVisitor.getContext().getPattern().isRealTypedVar(expr.getExpression()))) {
      // search for statement
      final MatchingHandler exprHandler = myCompilingVisitor.getContext().getPattern().getHandler(expr);
      if (exprHandler instanceof SubstitutionHandler) {
        SubstitutionHandler handler = (SubstitutionHandler)exprHandler;
        handler.setFilter(new StatementFilter());
        handler.setMatchHandler(new StatementHandler());
      }
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    myCompilingVisitor.handle(element);
    super.visitElement(element);
  }


  @Nullable
  private MatchingHandler processPatternStringWithFragments(String pattern, OccurenceKind kind) {
    String content;

    if (kind == OccurenceKind.LITERAL) {
      content = pattern.substring(1, pattern.length() - 1);
    }
    else if (kind == OccurenceKind.COMMENT) {
      content = pattern;
    }
    else {
      return null;
    }

    StringBuffer buf = new StringBuffer(content.length());
    Matcher matcher = ourSubstitutionPattern.matcher(content);
    List<SubstitutionHandler> handlers = null;
    int start = 0;
    String word;
    boolean hasLiteralContent = false;

    SubstitutionHandler handler = null;
    while (matcher.find()) {
      if (handlers == null) handlers = new LinkedList<SubstitutionHandler>();
      handler = (SubstitutionHandler)myCompilingVisitor.getContext().getPattern().getHandler(matcher.group(1));
      if (handler != null) handlers.add(handler);

      word = content.substring(start, matcher.start());

      if (word.length() > 0) {
        buf.append(shieldSpecialChars(word));
        hasLiteralContent = true;

        processTokenizedName(word, false, kind);
      }

      RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);

      if (predicate == null || !predicate.isWholeWords()) {
        buf.append("(.*?)");
      }
      else {
        buf.append(".*?\\b(").append(predicate.getRegExp()).append(")\\b.*?");
      }

      if (!IsNotSuitablePredicate(predicate, handler)) {
        processTokenizedName(predicate.getRegExp(), false, kind);
      }

      start = matcher.end();
    }

    word = content.substring(start, content.length());

    if (word.length() > 0) {
      hasLiteralContent = true;
      buf.append(shieldSpecialChars(word));

      processTokenizedName(word, false, kind);
    }

    if (hasLiteralContent) {
      if (kind == OccurenceKind.LITERAL) {
        buf.insert(0, "\"");
        buf.append("\"");
      }
      buf.append("$");
    }

    if (handlers != null) {
      return (hasLiteralContent) ? (MatchingHandler)new LiteralWithSubstitutionHandler(
        buf.toString(),
        handlers
      ) :
             handler;
    }

    return null;
  }

  private static boolean IsNotSuitablePredicate(RegExpPredicate predicate, SubstitutionHandler handler) {
    return predicate == null || handler.getMinOccurs() == 0 || !predicate.couldBeOptimized();
  }

  private void handleReference(PsiJavaCodeReferenceElement reference) {
    handleReferenceText(reference.getReferenceName());
  }

  private void handleReferenceText(String refname) {
    if (refname == null) return;

    if (myCompilingVisitor.getContext().getPattern().isTypedVar(refname)) {
      SubstitutionHandler handler = (SubstitutionHandler)myCompilingVisitor.getContext().getPattern().getHandler(refname);
      RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);
      if (IsNotSuitablePredicate(predicate, handler)) {
        return;
      }

      refname = predicate.getRegExp();

      if (handler.isStrictSubtype() || handler.isSubtype()) {
        if (myCompilingVisitor.getContext().getSearchHelper().addDescendantsOf(refname, handler.isSubtype())) {
          myCompilingVisitor.getContext().getSearchHelper().endTransaction();
        }

        return;
      }
    }

    addFilesToSearchForGivenWord(refname, true, OccurenceKind.CODE);
  }

  private void addFilesToSearchForGivenWord(String refname, boolean endTransaction, OccurenceKind kind) {
    if (!myCompilingVisitor.getContext().getSearchHelper().doOptimizing()) {
      return;
    }
    if (ourReservedWords.contains(refname)) return; // skip our special annotations !!!

    boolean addedSomething = false;

    if (kind == OccurenceKind.CODE) {
      addedSomething = myCompilingVisitor.getContext().getSearchHelper().addWordToSearchInCode(refname);
    }
    else if (kind == OccurenceKind.COMMENT) {
      addedSomething = myCompilingVisitor.getContext().getSearchHelper().addWordToSearchInComments(refname);
    }
    else if (kind == OccurenceKind.LITERAL) {
      addedSomething = myCompilingVisitor.getContext().getSearchHelper().addWordToSearchInLiterals(refname);
    }

    if (addedSomething && endTransaction) {
      myCompilingVisitor.getContext().getSearchHelper().endTransaction();
    }
  }

  private void processTokenizedName(String name, boolean skipComments, OccurenceKind kind) {
    WordTokenizer tokenizer = new WordTokenizer(name);
    for (Iterator<String> i = tokenizer.iterator(); i.hasNext();) {
      String nextToken = i.next();
      if (skipComments &&
          (nextToken.equals("/*") || nextToken.equals("/**") || nextToken.equals("*/") || nextToken.equals("*") || nextToken.equals("//"))
        ) {
        continue;
      }

      Matcher matcher = ourAlternativePattern.matcher(nextToken);
      if (matcher.matches()) {
        StringTokenizer alternatives = new StringTokenizer(matcher.group(1), "|");
        while (alternatives.hasMoreTokens()) {
          addFilesToSearchForGivenWord(alternatives.nextToken(), !alternatives.hasMoreTokens(), kind);
        }
      }
      else {
        addFilesToSearchForGivenWord(nextToken, true, kind);
      }
    }
  }

  private static String shieldSpecialChars(String word) {
    final StringBuffer buf = new StringBuffer(word.length());

    for (int i = 0; i < word.length(); ++i) {
      if (MatchUtils.SPECIAL_CHARS.indexOf(word.charAt(i)) != -1) {
        buf.append("\\");
      }
      buf.append(word.charAt(i));
    }

    return buf.toString();
  }

  @Override
  public void visitCodeBlock(PsiCodeBlock block) {
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() + 1);
    MatchingStrategy strategy = null;

    for (PsiElement el = block.getFirstChild(); el != null; el = el.getNextSibling()) {
      if (GlobalCompilingVisitor.getFilter().accepts(el)) {
        if (el instanceof PsiWhiteSpace) {
          myCompilingVisitor.addLexicalNode(el);
        }
      }
      else {
        el.accept(this);
        if (myCompilingVisitor.getCodeBlockLevel() == 1) {
          MatchingStrategy newstrategy = findStrategy(el);
          final MatchingHandler matchingHandler = myCompilingVisitor.getContext().getPattern().getHandler(el);
          myCompilingVisitor.getContext().getPattern().setHandler(el, new TopLevelMatchingHandler(matchingHandler));

          if (strategy == null || (strategy instanceof JavaDocMatchingStrategy)) {
            strategy = newstrategy;
          }
          else {
            if (strategy.getClass() != newstrategy.getClass()) {
              if (!(strategy instanceof CommentMatchingStrategy)) {
                throw new UnsupportedPatternException(SSRBundle.message("different.strategies.for.top.level.nodes.error.message"));
              }
              strategy = newstrategy;
            }
          }
        }
      }
    }

    if (myCompilingVisitor.getCodeBlockLevel() == 1) {
      if (strategy == null) {
        // this should happen only for error patterns
        strategy = ExprMatchingStrategy.getInstance();
      }
      myCompilingVisitor.getContext().getPattern().setStrategy(strategy);
    }
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() - 1);
  }

  private MatchingStrategy findStrategy(PsiElement el) {
    // identify matching strategy
    final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(el);

    //if (handler instanceof SubstitutionHandler) {
    //  final SubstitutionHandler shandler = (SubstitutionHandler) handler;
    if (handler.getFilter() instanceof SymbolNodeFilter ||
        handler.getFilter() instanceof TypedSymbolNodeFilter
      ) {
      return SymbolMatchingStrategy.getInstance();
    }
    //}

    if (el instanceof PsiDocComment) {
      return JavaDocMatchingStrategy.getInstance();
    }
    else if (el instanceof PsiComment) {
      return CommentMatchingStrategy.getInstance();
    }

    return ExprMatchingStrategy.getInstance();
  }

  private static boolean needsSupers(final PsiElement element, final MatchingHandler handler) {
    if (element.getParent() instanceof PsiClass &&
        handler instanceof SubstitutionHandler
      ) {
      final SubstitutionHandler handler2 = (SubstitutionHandler)handler;

      return (handler2.isStrictSubtype() || handler2.isSubtype());
    }
    return false;
  }

  private static class WordTokenizer {
    private final List<String> myWords = new LinkedList<String>();

    WordTokenizer(String text) {
      final StringTokenizer tokenizer = new StringTokenizer(text);
      Matcher matcher = null;

      while (tokenizer.hasMoreTokens()) {
        String nextToken = tokenizer.nextToken();
        if (matcher == null) {
          matcher = ourWordSearchPattern.matcher(nextToken);
        }
        else {
          matcher.reset(nextToken);
        }

        nextToken = (matcher.matches()) ? matcher.group(1) : nextToken;
        int lastWordStart = 0;
        int i;
        for (i = 0; i < nextToken.length(); ++i) {
          if (!Character.isJavaIdentifierStart(nextToken.charAt(i))) {
            if (i != lastWordStart) {
              myWords.add(nextToken.substring(lastWordStart, i));
            }
            lastWordStart = i + 1;
          }
        }

        if (i != lastWordStart) {
          myWords.add(nextToken.substring(lastWordStart, i));
        }
      }
    }

    Iterator<String> iterator() {
      return myWords.iterator();
    }
  }

  private static enum OccurenceKind {
    LITERAL, COMMENT, CODE
  }
}
