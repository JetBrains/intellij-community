package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.*;
import com.intellij.structuralsearch.impl.matcher.filters.*;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.strategies.*;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.BinaryPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.util.containers.GenericHashMap;
import com.intellij.util.Processor;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:25:08
 * To change this template use File | Settings | File Templates.
 */
class CompilingVisitor extends PsiRecursiveElementVisitor {
  private static NodeFilter filter = LexicalNodesFilter.getInstance();

  private CompileContext context;
  private ArrayList<PsiElement> lexicalNodes = new ArrayList<PsiElement>();

  private CompilingVisitor() {
  }

  private List<PsiElement> buildDescendants(String className, boolean includeSelf) {
    PsiShortNamesCache cache = PsiManager.getInstance(context.project).getShortNamesCache();
    SearchScope scope = context.options.getScope();
    PsiClass[] classes = cache.getClassesByName(className,(GlobalSearchScope)scope);
    final List<PsiElement> results = new ArrayList<PsiElement>();

    PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        results.add(element);
        return true;
      }

    };

    for(int i=0;i<classes.length;++i) {
      context.helper.processInheritors(
        processor,
        classes[i],
        scope,
        true,
        true
      );
    }

    if (includeSelf) {
      for(int i=0;i<classes.length;++i) {
        results.add( classes[i] );
      }
    }

    return results;
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

    Matcher matcher = pattern.matcher(comment.getText());
    boolean matches = false;
    if (!matcher.matches()) {
      matcher = pattern2.matcher(comment.getText());

      if (!matcher.matches()) {
        matcher = pattern3.matcher(comment.getText());
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

      if (context.findMatchingFiles) {
        RegExpPredicate predicate = getSimpleRegExpPredicate( handler );
        if (!IsNotSuitablePredicate(predicate, handler)) {
          processTokenizedName(predicate.getRegExp(),true,false,true,false);
        }
      }

      matches = true;
    }

    if (!matches && context.findMatchingFiles) {
      processTokenizedName(comment.getText(),true,false,true,false);
    }
  }

  private static Pattern alternativePattern = Pattern.compile("^\\((.+)\\)$");
  private void processTokenizedName(String name,boolean skipComments,boolean code, boolean comments, boolean literals) {
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
          addFilesToSearchForGivenWord(alternatives.nextToken(),!alternatives.hasMoreTokens(),code,comments,literals);
        }
      } else {
        addFilesToSearchForGivenWord(nextToken,true,code,comments,literals);
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

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    String value = expression.getText();

    if (value.length() > 2 && value.charAt(0)=='"' && value.charAt(value.length()-1)=='"') {
      String content = value.substring(1,value.length()-1);

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
        handlers.add( handler );

        word = content.substring(start,matcher.start());

        if (word.length() > 0) {
          buf.append( shieldSpecialChars(word) );
          hasLiteralContent = true;

          if (context.findMatchingFiles) {
            processTokenizedName(word,false,false,false,true);
          }
        }

        RegExpPredicate predicate = getSimpleRegExpPredicate( handler );
        //if (predicate!=null) {
        //  buf.append( "(" + predicate.getRegExp() + ")" );
        //} else {
          buf.append("(.*?)");
        //}

        if (context.findMatchingFiles) {
          if (!IsNotSuitablePredicate(predicate, handler)) {
            String refname = predicate.getRegExp();
            processTokenizedName(refname,false,false,false,true);
          }
        }

        start = matcher.end();
      }

      word = content.substring(start,content.length());

      if (word.length() > 0) {
        hasLiteralContent = true;
        buf.append( shieldSpecialChars(word) );

        if (context.findMatchingFiles) {
          processTokenizedName(word,false,false,false,true);
        }
      }

      if (hasLiteralContent) {
        buf.append("$");
      }

      if (handlers!=null) {
        expression.putUserData(
          CompiledPattern.HANDLER_KEY,
          (hasLiteralContent)?(Handler)new LiteralWithSubstituionHandler(
            buf.toString(),
            handlers
          ):
           handler
        );
      }
    }
    super.visitLiteralExpression(expression);
  }

  private String shieldSpecialChars(String word) {
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
    if ((context.pattern.isRealTypedVar(reference)) &&
        reference.getQualifierExpression() == null &&
        !(reference.getParent() instanceof PsiExpressionStatement)
       ) {
      // typed var for expression (but not top level)
      SubstitutionHandler handler = (SubstitutionHandler) context.pattern.getHandler(reference);
      setFilter( handler, ExpressionFilter.getInstance() );
    }

    if (!(reference.getParent() instanceof PsiMethodCallExpression)) {
      handleReference(reference);
    }
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    handleReference(expression.getMethodExpression());
    final PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();

    if (qualifier instanceof PsiJavaReference) {
      PsiElement element = ((PsiJavaReference)qualifier).resolve();

      if (element instanceof PsiClass) {
        expression.putUserData(CompiledPattern.FQN,((PsiClass)element).getQualifiedName());
      }
    }
    super.visitMethodCallExpression(expression);
  }

  private void handleReference(PsiJavaCodeReferenceElement reference) {
    handleReferenceText(reference.getReferenceName());
  }
  // structural search
  private void handleReferenceText(String refname) {
    if (!context.findMatchingFiles) return;
    if (refname==null) return;

    if (context.pattern.isTypedVar( refname )) {
      SubstitutionHandler handler = (SubstitutionHandler)context.pattern.getHandler( refname );
      RegExpPredicate predicate = getSimpleRegExpPredicate( handler );
      if (IsNotSuitablePredicate(predicate, handler)) {
        return;
      }

      refname = predicate.getRegExp();

      if(handler.isStrictSubtype() || handler.isSubtype()) {
        List classes = buildDescendants(refname,handler.isSubtype());
        for(Iterator i=classes.iterator();i.hasNext();) {
          final PsiClass clazz = (PsiClass)i.next();
          String text;

          if (clazz instanceof PsiAnonymousClass) {
            text = ((PsiAnonymousClass)clazz).getBaseClassReference().getReferenceName();
          } else {
            text = clazz.getName();
          }

          addFilesToSearchForGivenWord(
            text,
            false
          );
        }

        if (classes.size()>0) {
          endTransaction();
        }
        return;
      }
    }

    addFilesToSearchForGivenWord(refname,true);
  }

  private void addFilesToSearchForGivenWord(String refname, boolean endTransaction) {
    addFilesToSearchForGivenWord(refname,endTransaction,true,false,false);
  }

  private void addFilesToSearchForGivenWord(String refname, boolean endTransaction,boolean code, boolean comments, boolean literals) {
    boolean addedSomething = false;

    if (code && context.scanned.get(refname)==null) {
      context.helper.processAllFilesWithWord(refname,
                                             (GlobalSearchScope)context.options.getScope(),
                                             new MyFileProcessor()
      );

      context.scanned.put( refname, refname );
      addedSomething  = true;
    } else if (comments && context.scannedComments.get(refname)==null) {
      context.helper.processAllFilesWithWordInComments(refname,
                                                       (GlobalSearchScope)context.options.getScope(),
                                                       new MyFileProcessor()
      );

      context.scannedComments.put( refname, refname );
      addedSomething  = true;
    } else if (literals && context.scannedLiterals.get(refname)==null) {
      context.helper.processAllFilesWithWordInLiterals(refname,
                                                       (GlobalSearchScope)context.options.getScope(),
                                                       new MyFileProcessor());

      context.scannedLiterals.put( refname, refname );
      addedSomething  = true;
    }

    if (addedSomething && endTransaction) {
      endTransaction();
    }
  }

  private void endTransaction() {
    GenericHashMap<PsiFile,PsiFile> map = context.filesToScan;
    if (map.size() > 0) map.clear();
    context.filesToScan = context.filesToScan2;
    context.filesToScan2 = map;
    context.scanRequest++;
  }

  static Handler findRegExpPredicate(Handler start) {
    if (start==null) return null;
    if (start instanceof RegExpPredicate) return start;

    if(start instanceof BinaryPredicate) {
      BinaryPredicate binary = (BinaryPredicate)start;
      final Handler result = findRegExpPredicate(binary.getFirst());
      if (result!=null) return result;

      return findRegExpPredicate(binary.getSecond());
    } else if (start instanceof NotPredicate) {
      return null;
    }
    return null;
  }

  private RegExpPredicate getSimpleRegExpPredicate(SubstitutionHandler handler) {
    if (handler == null) return null;
    return (RegExpPredicate)findRegExpPredicate(handler.getPredicate());
  }

  private void setFilter(Handler handler, NodeFilter filter) {
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
        for(int i=0;i<params.length;++i) {
          if (params[i].getInnermostComponentReferenceElement() != null &&
              (context.pattern.isRealTypedVar(params[i].getInnermostComponentReferenceElement().getReferenceNameElement()))
             ) {
            context.pattern.getHandler(params[i]).setFilter(
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

  private boolean needsSupers(final PsiElement element, final Handler handler) {
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

    if (!(expr.getLastChild() instanceof PsiJavaToken)) {
      // search for expression or symbol
      final PsiElement reference = expr.getFirstChild();

      if ((context.pattern.isRealTypedVar(expr)) &&
          (reference instanceof PsiReferenceExpression) &&
          ((PsiReferenceExpression)reference).getQualifierExpression() == null
         ) {
        // symbol
        handle(reference);
        Handler handler = context.pattern.getHandler(reference);
        if (handler instanceof SubstitutionHandler) {
          handler.setFilter(
            SymbolNodeFilter.getInstance()
          );
          setHandler(expr,new SymbolHandler((SubstitutionHandler)handler));
        }
      } else if (reference instanceof PsiLiteralExpression) {
        Handler handler = (Handler)reference.getUserData(CompiledPattern.HANDLER_KEY);
        setHandler(expr,(handler !=null) ? handler: (handler = new ExpressionHandler()));
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
      SubstitutionHandler handler = (SubstitutionHandler) context.pattern.getHandler(expr);
      handler.setFilter( new StatementFilter() );
      handler.setMatchHandler( new StatementHandler() );
    }

    super.visitExpressionStatement(expr);
  }

  public void visitElement(PsiElement element) {
    handle(element);
    super.visitElement(element);
  }

  private int codeBlockLevel;

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

          if (strategy == null || (strategy instanceof JavaDocMatchingStrategy)) strategy = newstrategy;
          else {
            if (strategy.getClass() != newstrategy.getClass()) {
              if (!(strategy instanceof CommentMatchingStrategy))
                throw new UnsupportedPatternException(SSRBundle.message("different.strategies.for.top.level.nodes.error.message"));
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

  void compile(PsiElement element, CompileContext context) {
    codeBlockLevel = 0;
    this.context = context;
    element.accept(this);
  }

  private static CompilingVisitor instance;

  private class MyFileProcessor implements Processor<PsiFile> {
    public boolean process(PsiFile file) {
      if (context.scanRequest == 0 ||
          context.filesToScan.get(file)!=null) {
        context.filesToScan2.put(file,file);
      }
      return true;
    }
  }
}
