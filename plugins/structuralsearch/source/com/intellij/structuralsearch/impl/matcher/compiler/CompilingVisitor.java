package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import static com.intellij.structuralsearch.MatchOptions.*;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
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

/**
 * @author maxim
 */
class CompilingVisitor extends PsiRecursiveElementVisitor {
  private static NodeFilter filter = LexicalNodesFilter.getInstance();

  private CompileContext context;
  private ArrayList<PsiElement> lexicalNodes = new ArrayList<PsiElement>();

  private CompilingVisitor() {
  }

  private void setHandler(PsiElement element, Handler handler) {
    Handler realHandler = context.pattern.getHandlerSimple(element);

    if (realHandler instanceof SubstitutionHandler) {
      ((SubstitutionHandler)realHandler).setMatchHandler(handler);
    } else {
      // @todo care about composite handler in this case of simple handler!
      context.pattern.setHandler(element,handler);
    }
  }

  public void visitDocTag(PsiDocTag psiDocTag) {
    super.visitDocTag(psiDocTag);

    NodeIterator sons = new DocValuesIterator(psiDocTag.getFirstChild());
    while(sons.hasNext()) {
      setHandler(sons.current(), new DocDataHandler());
      sons.advance();
    }
  }

  private final void handle(PsiElement element) {

    if ((!filter.accepts(element) ||
         element instanceof PsiIdentifier) &&
                                           (context.pattern.isRealTypedVar(element)) &&
                                           context.pattern.getHandlerSimple(element)==null
       ) {
      String name = SubstitutionHandler.getTypedVarString(element);
      // name is the same for named element (clazz,methods, etc) and token (name of ... itself)
      // @todo need fix this
      final SubstitutionHandler handler;

      context.pattern.setHandler(
        element,
        handler = (SubstitutionHandler) context.pattern.getHandler(name)
      );

      if (handler !=null && context.options.getVariableConstraint(handler.getName()).isPartOfSearchResults()) {
        handler.setTarget(true);
        context.pattern.setTargetNode(element);
      }
    }
  }

  //@fixme
  @NonNls private static final String COMMENT = "\\s*(__\\$_\\w+)\\s*";
  static Pattern pattern = Pattern.compile("//"+COMMENT, Pattern.DOTALL);
  static Pattern pattern2 = Pattern.compile("/\\*"+COMMENT+"\\*/", Pattern.DOTALL);
  static Pattern pattern3 = Pattern.compile("/\\*\\*"+COMMENT+"\\*/", Pattern.DOTALL);

  public void visitComment(PsiComment comment) {
    super.visitComment(comment);

    final String text = comment.getText();
    Matcher matcher = pattern.matcher(text);
    boolean matches = false;
    if (!matcher.matches()) {
      matcher = pattern2.matcher(text);

      if (!matcher.matches()) {
        matcher = pattern3.matcher(text);
      } else {
        matches = true;
      }
    } else {
      matches = true;
    }

    if(matches || matcher.matches()) {
      String str = matcher.group(1);
      comment.putUserData(CompiledPattern.HANDLER_KEY,str);

      setFilter(
        context.pattern.getHandler(comment),
        CommentFilter.getInstance()
      );

      SubstitutionHandler handler = (SubstitutionHandler)context.pattern.getHandler(str);

      if (handler.getPredicate()!=null) {
        ((RegExpPredicate)handler.getPredicate()).setMultiline(true);
      }

      RegExpPredicate predicate = Handler.getSimpleRegExpPredicate( handler );
      if (!IsNotSuitablePredicate(predicate, handler)) {
        processTokenizedName(predicate.getRegExp(),true, OccurenceKind.COMMENT);
      }

      matches = true;
    }

    if (!matches) {
      Handler handler = processPatternStringWithFragments(text, OccurenceKind.COMMENT);
      if (handler != null) comment.putUserData(CompiledPattern.HANDLER_KEY,handler);
    }
  }

  private static Pattern alternativePattern = Pattern.compile("^\\((.+)\\)$");
  enum OccurenceKind {
    LITERAL, COMMENT, CODE
  }

  private void processTokenizedName(String name,boolean skipComments,OccurenceKind kind) {
    WordTokenizer tokenizer = new WordTokenizer(name);
    for(Iterator<String> i=tokenizer.iterator();i.hasNext();) {
      String nextToken = i.next();
      if (skipComments &&
          (nextToken.equals("/*") || nextToken.equals("/**") || nextToken.equals("*/") || nextToken.equals("*") || nextToken.equals("//"))
         ) {
        continue;
      }

      Matcher matcher = alternativePattern.matcher(nextToken);
      if (matcher.matches()) {
        StringTokenizer alternatives = new StringTokenizer(matcher.group(1),"|");
        while(alternatives.hasMoreTokens()) {
          addFilesToSearchForGivenWord(alternatives.nextToken(),!alternatives.hasMoreTokens(),kind);
        }
      } else {
        addFilesToSearchForGivenWord(nextToken,true,kind);
      }
    }
  }

  @NonNls private static final String WORD_SEARCH_PATTERN_STR = ".*?\\b(.+?)\\b.*?";
  private static Pattern wordSearchPattern = Pattern.compile(WORD_SEARCH_PATTERN_STR);

  static class WordTokenizer {
    private List<String> words = new LinkedList<String>();

    WordTokenizer(String text) {
      final StringTokenizer tokenizer = new StringTokenizer(text);
      Matcher matcher = null;

      while(tokenizer.hasMoreTokens()) {
        String nextToken = tokenizer.nextToken();
        if (matcher==null) {
          matcher = wordSearchPattern.matcher(nextToken);
        } else {
          matcher.reset(nextToken);
        }

        nextToken = (matcher.matches())?matcher.group(1):nextToken;
        int lastWordStart=0;
        int i;
        for(i=0;i<nextToken.length();++i) {
          if (!Character.isJavaIdentifierStart(nextToken.charAt(i))) {
            if (i!=lastWordStart) {
              words.add(nextToken.substring(lastWordStart,i));
            }
            lastWordStart = i+1;
          }
        }

        if (i!=lastWordStart) {
          words.add(nextToken.substring(lastWordStart,i));
        }
      }
    }

    Iterator<String> iterator() {
      return words.iterator();
    }
  }

  @NonNls private static final String SUBSTITUTION_PATTERN_STR = "\\b(__\\$_\\w+)\\b";
  static Pattern substitutionPattern = Pattern.compile(SUBSTITUTION_PATTERN_STR);

  private @Nullable Handler processPatternStringWithFragments(String pattern, OccurenceKind kind) {
    String content;

    if (kind == OccurenceKind.LITERAL) {
      content = pattern.substring(1,pattern.length()-1);
    } else if (kind == OccurenceKind.COMMENT) {
      content = pattern;
    } else {
      return null;
    }

    StringBuffer buf = new StringBuffer(content.length());
    Matcher matcher = substitutionPattern.matcher(content);
    List<SubstitutionHandler> handlers = null;
    int start = 0;
    String word;
    boolean hasLiteralContent = false;

    SubstitutionHandler handler = null;
    while(matcher.find()) {
      if(handlers==null) handlers = new LinkedList<SubstitutionHandler>();
      handler = (SubstitutionHandler)context.pattern.getHandler(matcher.group(1));
      if (handler != null) handlers.add( handler );

      word = content.substring(start,matcher.start());

      if (word.length() > 0) {
        buf.append( shieldSpecialChars(word) );
        hasLiteralContent = true;

        processTokenizedName(word,false,kind);
      }

      RegExpPredicate predicate = Handler.getSimpleRegExpPredicate( handler );

      if (predicate == null || !predicate.isWholeWords())  buf.append("(.*?)");
      else {
        buf.append(".*?\\b(").append(predicate.getRegExp()).append(")\\b.*?");
      }

      if (!IsNotSuitablePredicate(predicate, handler)) {
        processTokenizedName(predicate.getRegExp(),false,kind);
      }

      start = matcher.end();
    }

    word = content.substring(start,content.length());

    if (word.length() > 0) {
      hasLiteralContent = true;
      buf.append( shieldSpecialChars(word) );

      processTokenizedName(word,false,kind);
    }

    if (hasLiteralContent) {
      if (kind == OccurenceKind.LITERAL) {
        buf.insert(0, "\"");
        buf.append("\"");
      }
      buf.append("$");
    }

    if (handlers!=null) {
      return (hasLiteralContent)?(Handler)new LiteralWithSubstitutionHandler(
        buf.toString(),
        handlers
      ):
       handler;
    }

    return null;
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    String value = expression.getText();

    if (value.length() > 2 && value.charAt(0)=='"' && value.charAt(value.length()-1)=='"') {
      @Nullable Handler handler = processPatternStringWithFragments(value, OccurenceKind.LITERAL);

      if (handler!=null) {
        expression.putUserData( CompiledPattern.HANDLER_KEY,handler);
      }
    }
    super.visitLiteralExpression(expression);
  }

  private static String shieldSpecialChars(String word) {
    final StringBuffer buf = new StringBuffer(word.length());

    for(int i=0;i<word.length();++i) {
      if (MatchUtils.SPECIAL_CHARS.indexOf(word.charAt(i))!=-1) {
        buf.append("\\");
      }
      buf.append(word.charAt(i));
    }

    return buf.toString();
  }

  private static boolean IsNotSuitablePredicate(RegExpPredicate predicate, SubstitutionHandler handler) {
    return predicate==null || handler.getMinOccurs() == 0 || !predicate.couldBeOptimized();
  }

  public void visitClassInitializer(final PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    PsiStatement[] psiStatements = initializer.getBody().getStatements();
    if (psiStatements.length == 1 && psiStatements[0] instanceof PsiExpressionStatement) {
      Handler handler = context.pattern.getHandler(psiStatements[0]);

      if (handler instanceof SubstitutionHandler) {
        context.pattern.setHandler(initializer, new SubstitutionHandler((SubstitutionHandler)handler));
      }
    }
  }

  public void visitField(PsiField psiField) {
    super.visitField(psiField);
    final Handler handler = context.pattern.getHandler(psiField);

    if(needsSupers(psiField,handler)) {
      context.pattern.setRequestsSuperFields(true);
    }
  }

  public void visitMethod(PsiMethod psiMethod) {
    super.visitMethod(psiMethod);
    final Handler handler = context.pattern.getHandler(psiMethod);

    if(needsSupers(psiMethod,handler)) {
      context.pattern.setRequestsSuperMethods(true);
    }

    setFilter(handler,MethodFilter.getInstance());
    handleReferenceText(psiMethod.getName());
  }

  public void visitReferenceExpression(PsiReferenceExpression reference) {
    visitElement(reference);

    boolean typedVarProcessed = false;
    final PsiElement referenceParent = reference.getParent();
    
    if ((context.pattern.isRealTypedVar(reference)) &&
        reference.getQualifierExpression() == null &&
        !(referenceParent instanceof PsiExpressionStatement)
       ) {
      // typed var for expression (but not top level)
      Handler handler = context.pattern.getHandler(reference);
      setFilter( handler, ExpressionFilter.getInstance() );
      typedVarProcessed = true;
    }

    if (!(referenceParent instanceof PsiMethodCallExpression)) {
      handleReference(reference);
    }

    Handler handler = context.pattern.getHandler(reference);

    // We want to merge qname related to class to find it in any form
    final String referencedName = reference.getReferenceName();

    if (!typedVarProcessed &&
        !(handler instanceof SubstitutionHandler)) {
      final PsiElement resolve = reference.resolve();

      PsiElement referenceQualifier = reference.getQualifier();
      if (resolve instanceof PsiClass ||
          ( resolve == null &&
            ( (referencedName != null && Character.isUpperCase(referencedName.charAt(0))) ||
              referenceQualifier == null
            )
          )
        ) {
        boolean hasNoNestedSubstitutionHandlers = false;
        PsiExpression qualifier;
        PsiReferenceExpression currentReference = reference;

        while((qualifier = currentReference.getQualifierExpression()) != null) {
          if (!(qualifier instanceof PsiReferenceExpression) ||
              context.pattern.getHandler(qualifier) instanceof SubstitutionHandler
             ) {
            hasNoNestedSubstitutionHandlers = true;
            break;
          }
          currentReference = (PsiReferenceExpression)qualifier;
        }
        if (!hasNoNestedSubstitutionHandlers) createAndSetSubstitutionHandlerFromReference(reference, reference.getText());
      }
    }
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    handleReference(expression.getMethodExpression());
    super.visitMethodCallExpression(expression);
  }

  private void handleReference(PsiJavaCodeReferenceElement reference) {
    handleReferenceText(reference.getReferenceName());
  }

  private void handleReferenceText(String refname) {
    if (refname==null) return;

    if (context.pattern.isTypedVar( refname )) {
      SubstitutionHandler handler = (SubstitutionHandler)context.pattern.getHandler( refname );
      RegExpPredicate predicate = Handler.getSimpleRegExpPredicate( handler );
      if (IsNotSuitablePredicate(predicate, handler)) {
        return;
      }

      refname = predicate.getRegExp();

      if(handler.isStrictSubtype() || handler.isSubtype()) {
        if (context.searchHelper.addDescendantsOf(refname,handler.isSubtype())) {
          context.searchHelper.endTransaction();
        }

        return;
      }
    }

    addFilesToSearchForGivenWord(refname, true, OccurenceKind.CODE);
  }

  private static Set<String> ourReservedWords = new HashSet<String>(
    Arrays.asList(MODIFIER_ANNOTATION_NAME,INSTANCE_MODIFIER_NAME,PACKAGE_LOCAL_MODIFIER_NAME)
  );

  private void addFilesToSearchForGivenWord(String refname, boolean endTransaction,OccurenceKind kind) {
    if (!context.searchHelper.doOptimizing()) {
      return;
    }
    if(ourReservedWords.contains(refname)) return; // skip our special annotations !!!

    boolean addedSomething = false;

    if (kind == OccurenceKind.CODE) {
      addedSomething = context.searchHelper.addWordToSearchInCode(refname);
    } else if (kind == OccurenceKind.COMMENT) {
      addedSomething = context.searchHelper.addWordToSearchInComments(refname);
    } else if (kind == OccurenceKind.LITERAL) {
      addedSomething = context.searchHelper.addWordToSearchInLiterals(refname);
    }

    if (addedSomething && endTransaction) {
      context.searchHelper.endTransaction();
    }
  }

  private static void setFilter(Handler handler, NodeFilter filter) {
    if (handler.getFilter()!=null &&
        handler.getFilter().getClass()!=filter.getClass()
        ) {
      // for constructor we will have the same handler for class and method and tokens itselfa
      handler.setFilter(
        new CompositeFilter(
          filter,
          handler.getFilter()
        )
      );
    } else {
      handler.setFilter( filter );
    }
  }
  public void visitBlockStatement(PsiBlockStatement psiBlockStatement) {
    super.visitBlockStatement(psiBlockStatement);
    context.pattern.getHandler(psiBlockStatement).setFilter( BlockFilter.getInstance() );
  }

  public void visitVariable(PsiVariable psiVariable) {
    super.visitVariable(psiVariable);
    context.pattern.getHandler(psiVariable).setFilter( VariableFilter.getInstance() );
    handleReferenceText(psiVariable.getName());
  }

  public void visitDeclarationStatement(PsiDeclarationStatement psiDeclarationStatement) {
    super.visitDeclarationStatement(psiDeclarationStatement);

    if (psiDeclarationStatement.getFirstChild() instanceof PsiTypeElement) {
      // search for expression or symbol
      final PsiJavaCodeReferenceElement reference = ((PsiTypeElement)psiDeclarationStatement.getFirstChild()).getInnermostComponentReferenceElement();

      if (reference != null &&
          (context.pattern.isRealTypedVar(reference.getReferenceNameElement())) &&
          reference.getParameterList().getTypeParameterElements().length > 0
         ) {
        setHandler(psiDeclarationStatement,new TypedSymbolHandler());
        final Handler handler = context.pattern.getHandler(psiDeclarationStatement);
        // typed symbol
        handler.setFilter(
          TypedSymbolNodeFilter.getInstance()
        );

        final PsiTypeElement[] params = reference.getParameterList().getTypeParameterElements();
        for (PsiTypeElement param : params) {
          if (param.getInnermostComponentReferenceElement() != null &&
              (context.pattern.isRealTypedVar(param.getInnermostComponentReferenceElement().getReferenceNameElement()))
            ) {
            context.pattern.getHandler(param).setFilter(
              TypeParameterFilter.getInstance()
            );
          }
        }

        return;
      }
    }

    final Handler handler = new DeclarationStatementHandler();
    context.pattern.setHandler(psiDeclarationStatement, handler);
    PsiElement previousNonWhiteSpace = psiDeclarationStatement.getPrevSibling();

    while(previousNonWhiteSpace instanceof PsiWhiteSpace) {
      previousNonWhiteSpace = previousNonWhiteSpace.getPrevSibling();
    }

    if (previousNonWhiteSpace instanceof PsiComment) {
      ((DeclarationStatementHandler)handler).setCommentHandler(context.pattern.getHandler(previousNonWhiteSpace));

      context.pattern.setHandler(
        previousNonWhiteSpace,
        handler
      );
    }

    // detect typed symbol, it will have no variable
    handler.setFilter( DeclarationFilter.getInstance() );
  }

  public void visitDocComment(PsiDocComment psiDocComment) {
    super.visitDocComment(psiDocComment);
    context.pattern.getHandler(psiDocComment).setFilter( JavaDocFilter.getInstance() );
  }

  private static boolean needsSupers(final PsiElement element, final Handler handler) {
    if (element.getParent() instanceof PsiClass &&
        handler instanceof SubstitutionHandler
        )  {
      final SubstitutionHandler handler2 = (SubstitutionHandler) handler;

      return (handler2.isStrictSubtype() || handler2.isSubtype());
    }
    return false;
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);

    if (reference.getParent() != null &&
        reference.getParent().getParent() instanceof PsiClass) {
      setFilter(context.pattern.getHandler(reference),TypeFilter.getInstance());
    }

    handleReference(reference);
  }

  public void visitClass(PsiClass psiClass) {
    super.visitClass(psiClass);
    final Handler handler = context.pattern.getHandler(psiClass);

    if (needsSupers(psiClass,handler))  {
      context.pattern.setRequestsSuperInners(true);
    }

    setFilter( handler, ClassFilter.getInstance() );

    for(PsiElement element = psiClass.getFirstChild();element!=null; element = element.getNextSibling()) {
      if (element instanceof PsiTypeElement && element.getNextSibling() instanceof PsiErrorElement) {
        // found match that
        psiClass.putUserData(CompiledPattern.ALL_CLASS_CONTENT_VAR_KEY, element );
      }
    }
  }

  public void visitExpressionStatement(PsiExpressionStatement expr) {
    handle(expr);

    super.visitExpressionStatement(expr);
    
    if (!(expr.getLastChild() instanceof PsiJavaToken)) {
      // search for expression or symbol
      final PsiElement reference = expr.getFirstChild();
      Handler referenceHandler = context.pattern.getHandler(reference);

      if (referenceHandler instanceof SubstitutionHandler &&
          (reference instanceof PsiReferenceExpression)
         ) {
        // symbol
        context.pattern.setHandler(expr, referenceHandler);
        referenceHandler.setFilter(
          SymbolNodeFilter.getInstance()
        );

        setHandler(expr,new SymbolHandler((SubstitutionHandler)referenceHandler));
      } else if (reference instanceof PsiLiteralExpression) {
        Handler handler = new ExpressionHandler();
        setHandler(expr,handler);
        handler.setFilter( ConstantFilter.getInstance() );
      }  else {
        // just expression
        Handler handler;
        setHandler(expr,handler = new ExpressionHandler());

        handler.setFilter( ExpressionFilter.getInstance() );
      }
    } else if (expr.getExpression() instanceof PsiReferenceExpression &&
               (context.pattern.isRealTypedVar(expr.getExpression()))) {
      // search for statement
      final Handler exprHandler = context.pattern.getHandler(expr);
      if (exprHandler instanceof SubstitutionHandler) {
        SubstitutionHandler handler = (SubstitutionHandler) exprHandler;
        handler.setFilter( new StatementFilter() );
        handler.setMatchHandler( new StatementHandler() );
      }
    }
  }

  private SubstitutionHandler createAndSetSubstitutionHandlerFromReference(final PsiElement expr, final String referenceText) {
    final SubstitutionHandler substitutionHandler = new SubstitutionHandler("__"+ referenceText.replace('.','_'), false, 1, 1, false);
    substitutionHandler.setPredicate(new RegExpPredicate(referenceText.replaceAll("\\.","\\\\."),true, null, false,false));
    context.pattern.setHandler(expr,substitutionHandler);
    return substitutionHandler;
  }

  public void visitElement(PsiElement element) {
    handle(element);
    super.visitElement(element);
  }

  private int codeBlockLevel;

  public void visitXmlToken(XmlToken token) {
    super.visitXmlToken(token);

    if (token.getParent() instanceof XmlText && context.pattern.isRealTypedVar(token)) {
      final Handler handler = context.pattern.getHandler(token);
      handler.setFilter(TagValueFilter.getInstance());

      final XmlTextHandler parentHandler = new XmlTextHandler();
      context.pattern.setHandler(token.getParent(), parentHandler);
      parentHandler.setFilter(TagValueFilter.getInstance());
    }
  }

  public void visitXmlTag(XmlTag xmlTag) {
    super.visitXmlTag(xmlTag);

    if (codeBlockLevel==0) {
      context.pattern.setStrategy(XmlMatchingStrategy.getInstance());
    }
  }

  public void visitCodeBlock(PsiCodeBlock block) {
    ++codeBlockLevel;
    MatchingStrategy strategy = null;

    for(PsiElement el = block.getFirstChild(); el !=null; el = el.getNextSibling()) {
      if (filter.accepts(el)) {
        if (el instanceof PsiWhiteSpace) {
          lexicalNodes.add(el);
        }
      } else {
        el.accept(this);
        if (codeBlockLevel==1) {
          MatchingStrategy newstrategy = findStrategy(el);

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

    if (codeBlockLevel==1) {
      if (strategy==null) {
        // this should happen only for error patterns
        strategy = ExprMatchingStrategy.getInstance();
      }
      context.pattern.setStrategy(strategy);
    }
    --codeBlockLevel;
  }

  private MatchingStrategy findStrategy(PsiElement el) {
    // identify matching strategy
    final Handler handler = context.pattern.getHandler(el);

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
    } else
    if (el instanceof PsiComment) {
      return CommentMatchingStrategy.getInstance();
    }

    return ExprMatchingStrategy.getInstance();
  }

  public ArrayList getLexicalNodes() {
    return lexicalNodes;
  }

  static CompilingVisitor getInstance() {
    if (instance==null) instance = new CompilingVisitor();

    return instance;
  }

  synchronized void compile(PsiElement element, CompileContext context) {
    codeBlockLevel = 0;
    this.context = context;
    element.accept(this);
  }

  private static CompilingVisitor instance;
}
