package com.intellij.psi.impl.search;

import com.intellij.ant.PsiAntElement;
import com.intellij.codeHighlighting.CopyCreatorLexer;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbDeclMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.PsiReferenceSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.StringSearcher;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");

  private final PsiManagerImpl myManager;
  private static final TodoItem[] EMPTY_TODO_ITEMS = new TodoItem[0];
  private static final TokenSet XML_DATA_CHARS = TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS);

  static {
    PsiReferenceSearch.INSTANCE.registerExecutor(new PsiAnnotationMethodReferencesSearcher());
    PsiReferenceSearch.INSTANCE.registerExecutor(new PsiAntElementReferenceSearcher());
    PsiReferenceSearch.INSTANCE.registerExecutor(new CachesBasedRefSearcher());
    PsiReferenceSearch.INSTANCE.registerExecutor(new ConstructorReferencesSearcher());

    DirectClassInheritorsSearch.INSTANCE.registerExecutor(new JavaDirectInheritorsSearcher());
    DirectClassInheritorsSearch.INSTANCE.registerExecutor(new EjbDirectInhertiorsSearcher());
  }

  @NotNull
  public SearchScope getUseScope(PsiElement element) {
    final GlobalSearchScope maximalUseScope = myManager.getFileManager().getUseScope(element);
    if (element instanceof PsiPackage) {
      return maximalUseScope;
    }
    else if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        return new LocalSearchScope(element);
      }
      PsiFile file = element.getContainingFile();
      if (file instanceof JspFile) return maximalUseScope;
      PsiClass aClass = (PsiClass)element;
      if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return maximalUseScope;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return maximalUseScope;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass topClass = PsiUtil.getTopLevelClass(aClass);
        return new LocalSearchScope(topClass == null ? aClass.getContainingFile() : topClass);
      }
      else {
        PsiPackage aPackage = null;
        if (file instanceof PsiJavaFile) {
          aPackage = element.getManager().findPackage(((PsiJavaFile)file).getPackageName());
        }

        if (aPackage == null) {
          PsiDirectory dir = file.getContainingDirectory();
          if (dir != null) {
            aPackage = dir.getPackage();
          }
        }

        if (aPackage != null) {
          SearchScope scope = GlobalSearchScope.packageScope(aPackage, false);
          scope = scope.intersectWith(maximalUseScope);
          return scope;
        }

        return new LocalSearchScope(file);
      }
    }
    else if (element instanceof PsiMethod || element instanceof PsiField) {
      PsiMember member = (PsiMember) element;
      PsiFile file = element.getContainingFile();
      if (file instanceof JspFile) return maximalUseScope;

      PsiClass aClass = member.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        //member from anonymous class can be called from outside the class
        PsiElement methodCallExpr = PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression.class);
        return new LocalSearchScope(methodCallExpr != null ? methodCallExpr : aClass);
      }

      if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
        return maximalUseScope;
      }
      else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
        return maximalUseScope;
      }
      else if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass topClass = PsiUtil.getTopLevelClass(member);
        return topClass != null ? new LocalSearchScope(topClass) : new LocalSearchScope(file);
      }
      else {
        PsiPackage aPackage = file instanceof PsiJavaFile ? myManager.findPackage(((PsiJavaFile) file).getPackageName()) : null;
        if (aPackage != null) {
          SearchScope scope = GlobalSearchScope.packageScope(aPackage, false);
          scope = scope.intersectWith(maximalUseScope);
          return scope;
        }

        return maximalUseScope;
      }
    }
    else if (element instanceof ImplicitVariable) {
      return new LocalSearchScope(((ImplicitVariable)element).getDeclarationScope());
    }
    else if (element instanceof PsiLocalVariable) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiDeclarationStatement) {
        return new LocalSearchScope(parent.getParent());
      }
      else {
        return maximalUseScope;
      }
    }
    else if (element instanceof PsiParameter) {
      return new LocalSearchScope(((PsiParameter)element).getDeclarationScope());
    }
    else if (element instanceof PsiLabeledStatement) {
      return new LocalSearchScope(element);
    }
    else if (element instanceof PsiAntElement) {
      return ((PsiAntElement)element).getSearchScope();
    }
    else {
      return maximalUseScope;
    }
  }


  public PsiSearchHelperImpl(PsiManagerImpl manager) {
    myManager = manager;
  }

  public PsiReference[] findReferences(PsiElement element, SearchScope searchScope, boolean ignoreAccessScope) {
    LOG.assertTrue(searchScope != null);

    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferences(processor, element, searchScope, ignoreAccessScope);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferences(final PsiReferenceProcessor processor,
                                   final PsiElement refElement,
                                   SearchScope originalScope,
                                   boolean ignoreAccessScope) {
    LOG.assertTrue(originalScope != null);

    final Query<PsiReference> query = PsiReferenceSearch.search(refElement, originalScope, ignoreAccessScope);
    return query.forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference t) {
        return processor.execute(t);
      }
    });
  }

  public PsiMethod[] findOverridingMethods(PsiMethod method, SearchScope searchScope, boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiMethod> processor = new PsiElementProcessor.CollectElements<PsiMethod>();
    processOverridingMethods(processor, method, searchScope, checkDeep);

    return processor.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public boolean processOverridingMethods(final PsiElementProcessor<PsiMethod> processor,
                                          final PsiMethod method,
                                          SearchScope searchScope,
                                          final boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    final PsiClass parentClass = method.getContainingClass();
    if (parentClass == null
        || method.isConstructor()
        || method.hasModifierProperty(PsiModifier.STATIC)
        || method.hasModifierProperty(PsiModifier.FINAL)
        || method.hasModifierProperty(PsiModifier.PRIVATE)
        || parentClass instanceof PsiAnonymousClass
        || parentClass.hasModifierProperty(PsiModifier.FINAL)) {
      return true;
    }

    PsiElementProcessor<PsiClass> inheritorsProcessor = new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass inheritor) {
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(parentClass, inheritor,
                                                                                 PsiSubstitutor.EMPTY);
        MethodSignature signature = method.getSignature(substitutor);
        PsiMethod method1 = MethodSignatureUtil.findMethodBySuperSignature(inheritor, signature);
        if (method1 == null
            || method1.hasModifierProperty(PsiModifier.STATIC)
            || (method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
                && !method1.getManager().arePackagesTheSame(parentClass, inheritor))) {
          return true;
        }
        return processor.execute(method1);
      }
    };
    if (!processInheritors(inheritorsProcessor, parentClass, searchScope, true)) return false;
    final EjbMethodRole ejbRole = J2EERolesUtil.getEjbRole(method);
    if (ejbRole instanceof EjbDeclMethodRole) {
      final PsiMethod[] implementations = ((EjbDeclMethodRole)ejbRole).findImplementations();
      for (PsiMethod implementation : implementations) {
        // same signature methods were processed already
        if (implementation.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY))) continue;
        if (!processor.execute(implementation)) return false;
      }
    }
    return true;
  }



  public PsiReference[] findReferencesIncludingOverriding(final PsiMethod method,
                                                          SearchScope searchScope,
                                                          boolean isStrictSignatureSearch) {
    LOG.assertTrue(searchScope != null);

    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferencesIncludingOverriding(processor, method, searchScope, isStrictSignatureSearch);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferencesIncludingOverriding(final PsiReferenceProcessor processor,
                                                      final PsiMethod method,
                                                      SearchScope searchScope) {
    return processReferencesIncludingOverriding(processor, method, searchScope, true);
  }

  public boolean processReferencesIncludingOverriding(final PsiReferenceProcessor processor,
                                                      final PsiMethod method,
                                                      SearchScope searchScope,
                                                      final boolean isStrictSignatureSearch) {
    LOG.assertTrue(searchScope != null);

    if (method.isConstructor()) {
      Processor<PsiReference> adapter = new Processor<PsiReference>() {
        public boolean process(final PsiReference t) {
          return processor.execute(t);
        }
      };

      final ConstructorReferencesSearchHelper helper = new ConstructorReferencesSearchHelper(myManager);
      if (!helper. processConstructorReferences(adapter, method, searchScope, !isStrictSignatureSearch, isStrictSignatureSearch)) {
        return false;
      }
    }

    PsiClass parentClass = method.getContainingClass();
    if (isStrictSignatureSearch && (parentClass == null
                                    || parentClass instanceof PsiAnonymousClass
                                    || parentClass.hasModifierProperty(PsiModifier.FINAL)
                                    || method.hasModifierProperty(PsiModifier.STATIC)
                                    || method.hasModifierProperty(PsiModifier.FINAL)
                                    || method.hasModifierProperty(PsiModifier.PRIVATE))
    ) {
      return processReferences(processor, method, searchScope, false);
    }

    final String text = method.getName();
    final PsiMethod[] methods = isStrictSignatureSearch ? new PsiMethod[]{method} : getOverloadsMayBeOverriden(method);

    SearchScope accessScope = methods[0].getUseScope();
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method1 = methods[i];
      SearchScope someScope = PsiSearchScopeUtil.scopesUnion(accessScope, method1.getUseScope());
      accessScope = someScope == null ? accessScope : someScope;
    }

    final PsiClass aClass = method.getContainingClass();

    final TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        PsiReference reference = element.findReferenceAt(offsetInElement);

        if (reference != null) {
          for (PsiMethod method : methods) {
            if (reference.isReferenceTo(method)) {
              return processor.execute(reference);
            }
            PsiElement refElement = reference.resolve();

            if (refElement instanceof PsiMethod) {
              PsiMethod refMethod = (PsiMethod)refElement;
              PsiClass refMethodClass = refMethod.getContainingClass();
              if (refMethodClass == null) return true;

              if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
                PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(aClass, refMethodClass, PsiSubstitutor.EMPTY);
                if (substitutor != null) {
                  if (refMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(substitutor))) {
                    if (!processor.execute(reference)) return false;
                  }
                }
              }

              if (!isStrictSignatureSearch) {
                PsiManager manager = method.getManager();
                if (manager.areElementsEquivalent(refMethodClass, aClass)) {
                  return processor.execute(reference);
                }
              }
            }
            else {
              return true;
            }
          }
        }

        return true;
      }
    };

    searchScope = searchScope.intersectWith(accessScope);

    short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS;
    boolean toContinue = processElementsWithWord(processor1,
                                                 searchScope,
                                                 text,
                                                 searchContext, true);
    if (!toContinue) return false;

    if (PropertyUtil.isSimplePropertyAccessor(method)) {
      final String propertyName = PropertyUtil.getPropertyName(method);

      //if (myManager.getNameHelper().isIdentifier(propertyName)) {
        if (searchScope instanceof GlobalSearchScope) {
          searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            (GlobalSearchScope)searchScope,
            StdFileTypes.JSP,
            StdFileTypes.JSPX,
            StdFileTypes.XML
          );
        }
        toContinue = processElementsWithWord(processor1,
                                             searchScope,
                                             propertyName,
                                             UsageSearchContext.IN_FOREIGN_LANGUAGES, true);
        if (!toContinue) return false;
      //}
    }

    return true;
  }

  public PsiClass[] findInheritors(PsiClass aClass, SearchScope searchScope, boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processInheritors(processor, aClass, searchScope, checkDeep);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  public boolean processInheritors(PsiElementProcessor<PsiClass> processor,
                                   PsiClass aClass,
                                   SearchScope searchScope,
                                   boolean checkDeep) {
    return processInheritors(processor, aClass, searchScope, checkDeep, true);
  }

  public boolean processInheritors(final PsiElementProcessor<PsiClass> processor,
                                   PsiClass aClass,
                                   SearchScope searchScope,
                                   boolean checkDeep,
                                   boolean checkInheritance) {
    return ClassInheritorsSearch.search(aClass, searchScope, checkDeep, checkInheritance).forEach(new Processor<PsiClass>() {
      public boolean process(final PsiClass t) {
        return processor.execute(t);
      }
    });
  }

  public PsiFile[] findFilesWithTodoItems() {
    return myManager.getCacheManager().getFilesWithTodoItems();
  }

  private static final TokenSet XML_COMMENT_BIT_SET = TokenSet.create(new IElementType[]{TreeElement.XML_COMMENT_CHARACTERS});

  public TodoItem[] findTodoItems(PsiFile file) {
    return findTodoItems(file, null);
  }

  public TodoItem[] findTodoItems(PsiFile file, int startOffset, int endOffset) {
    return findTodoItems(file, new TextRange(startOffset, endOffset));
  }

  private TodoItem[] findTodoItems(PsiFile file, TextRange range) {
    if (file instanceof PsiBinaryFile || file instanceof PsiCompiledElement ||
        file.getVirtualFile() == null) {
      return EMPTY_TODO_ITEMS;
    }

    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile());
    if (count == 0) {
      return EMPTY_TODO_ITEMS;
    }

    TIntArrayList commentStarts = new TIntArrayList();
    TIntArrayList commentEnds = new TIntArrayList();
    char[] chars = file.textToCharArray();
    if (file instanceof PsiPlainTextFile) {
      FileType fType = file.getFileType();
      synchronized (PsiLock.LOCK) {
        if (fType instanceof CustomFileType) {
          TokenSet commentTokens = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
          Lexer lexer = fType.getHighlighter(myManager.getProject()).getHighlightingLexer();
          findComments(lexer, chars, range, commentTokens, commentStarts, commentEnds);
        }
        else {
          commentStarts.add(0);
          commentEnds.add(file.getTextLength());
        }
      }
    }
    else {
      // collect comment offsets to prevent long locks by PsiManagerImpl.LOCK
      synchronized (PsiLock.LOCK) {
        final Language lang = file.getLanguage();
        Lexer lexer = lang.getSyntaxHighlighter(file.getProject()).getHighlightingLexer();
        TokenSet commentTokens = null;
        if (file instanceof PsiJavaFile) {
          commentTokens = TokenSet.orSet(ElementType.COMMENT_BIT_SET, XML_COMMENT_BIT_SET, JavaDocTokenType.ALL_JAVADOC_TOKENS, XML_DATA_CHARS);
        }
        else if (file instanceof JspFile) {
          final JspFile jspFile = (JspFile)file;
          commentTokens = TokenSet.orSet(XML_COMMENT_BIT_SET, ElementType.COMMENT_BIT_SET);
          final ParserDefinition parserDefinition = jspFile.getBaseLanguage().getParserDefinition();
          if (parserDefinition != null) {
            commentTokens = TokenSet.orSet(commentTokens, parserDefinition.getCommentTokens());
          }
        }
        else if (file instanceof XmlFile) {
          commentTokens = XML_COMMENT_BIT_SET;
        }
        else {
          final ParserDefinition parserDefinition = lang.getParserDefinition();
          if (parserDefinition != null) {
            commentTokens = parserDefinition.getCommentTokens();
          }
        }

        if (commentTokens == null) return EMPTY_TODO_ITEMS;

        findComments(lexer, chars, range, commentTokens, commentStarts, commentEnds);
      }
    }

    ArrayList<TodoItem> list = new ArrayList<TodoItem>();

    for (int i = 0; i < commentStarts.size(); i++) {
      int commentStart = commentStarts.get(i);
      int commentEnd = commentEnds.get(i);

      TodoPattern[] patterns = TodoConfiguration.getInstance().getTodoPatterns();
      for (TodoPattern toDoPattern : patterns) {
        Pattern pattern = toDoPattern.getPattern();
        if (pattern != null) {
          ProgressManager.getInstance().checkCanceled();

          CharSequence input = new CharArrayCharSequence(chars, commentStart, commentEnd);
          Matcher matcher = pattern.matcher(input);
          while (true) {
            //long time1 = System.currentTimeMillis();
            boolean found = matcher.find();
            //long time2 = System.currentTimeMillis();
            //System.out.println("scanned text of length " + (lexer.getTokenEnd() - lexer.getTokenStart() + " in " + (time2 - time1) + " ms"));

            if (!found) break;
            int start = matcher.start() + commentStart;
            int end = matcher.end() + commentStart;
            if (start != end) {
              if (range == null || range.getStartOffset() <= start && end <= range.getEndOffset()) {
                list.add(new TodoItemImpl(file, start, end, toDoPattern));
              }
            }

            ProgressManager.getInstance().checkCanceled();
          }
        }
      }
    }

    return list.toArray(new TodoItem[list.size()]);
  }

  private static void findComments(final Lexer lexer,
                            final char[] chars,
                            final TextRange range,
                            final TokenSet commentTokens,
                            final TIntArrayList commentStarts, final TIntArrayList commentEnds) {
    for (lexer.start(chars); ; lexer.advance()) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType instanceof CopyCreatorLexer.HighlightingCopyElementType) {
        tokenType = ((CopyCreatorLexer.HighlightingCopyElementType)tokenType).getBase();
      }
      if (tokenType == null) break;

      if (range != null) {
        if (lexer.getTokenEnd() <= range.getStartOffset()) continue;
        if (lexer.getTokenStart() >= range.getEndOffset()) break;
      }

      boolean isComment = commentTokens.isInSet(tokenType);
      if (!isComment) {
        final Language commentLang = tokenType.getLanguage();
        final ParserDefinition parserDefinition = commentLang.getParserDefinition();
        if (parserDefinition != null) {
          final TokenSet langCommentTokens = parserDefinition.getCommentTokens();
          isComment = langCommentTokens.isInSet(tokenType);
        }
      }

      if (isComment) {
        commentStarts.add(lexer.getTokenStart());
        commentEnds.add(lexer.getTokenEnd());
      }
    }
  }

  public int getTodoItemsCount(PsiFile file) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile());
    if (count != -1) return count;
    return findTodoItems(file).length;
  }

  public int getTodoItemsCount(PsiFile file, TodoPattern pattern) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), pattern);
    if (count != -1) return count;
    TodoItem[] items = findTodoItems(file);
    count = 0;
    for (TodoItem item : items) {
      if (item.getPattern().equals(pattern)) count++;
    }
    return count;
  }

  public PsiIdentifier[] findIdentifiers(String identifier, SearchScope searchScope, short searchContext) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiIdentifier> processor = new PsiElementProcessor.CollectElements<PsiIdentifier>();
    processIdentifiers(processor, identifier, searchScope, searchContext);
    return processor.toArray(PsiIdentifier.EMPTY_ARRAY);
  }

  public boolean processIdentifiers(final PsiElementProcessor<PsiIdentifier> processor,
                                    final String identifier,
                                    SearchScope searchScope,
                                    short searchContext) {
    LOG.assertTrue(searchScope != null);

    TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element instanceof PsiIdentifier) {
          return processor.execute((PsiIdentifier)element);
        }
        return true;
      }
    };
    return processElementsWithWord(processor1, searchScope, identifier, searchContext, true);
  }


  public PsiElement[] findCommentsContainingIdentifier(String identifier, SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element.getContainingFile().findReferenceAt(element.getTextRange().getStartOffset() + offsetInElement) == null) {
          results.add(element);
        }
        return true;
      }
    };
    processElementsWithWord(processor, searchScope, identifier, UsageSearchContext.IN_COMMENTS, true);
    return results.toArray(new PsiElement[results.size()]);
  }

  public PsiLiteralExpression[] findStringLiteralsContainingIdentifier(String identifier, SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    final ArrayList<PsiLiteralExpression> results = new ArrayList<PsiLiteralExpression>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element instanceof PsiLiteralExpression) {
          results.add((PsiLiteralExpression)element);
        }
        return true;
      }
    };
    processElementsWithWord(processor,
                            searchScope,
                            identifier,
                            UsageSearchContext.IN_STRINGS,
                            true);
    return results.toArray(new PsiLiteralExpression[results.size()]);
  }

  public boolean processAllClasses(final PsiElementProcessor<PsiClass> processor, SearchScope searchScope) {
    if (searchScope instanceof GlobalSearchScope) {
      return processAllClassesInGlobalScope((GlobalSearchScope)searchScope, processor);
    }

    PsiElement[] scopeRoots = ((LocalSearchScope)searchScope).getScope();
    for (final PsiElement scopeRoot : scopeRoots) {
      if (!processScopeRootForAllClasses(scopeRoot, processor)) return false;
    }
    return true;
  }

  private static boolean processScopeRootForAllClasses(PsiElement scopeRoot, final PsiElementProcessor<PsiClass> processor) {
    if (scopeRoot == null) return true;
    final boolean[] stopped = new boolean[]{false};

    scopeRoot.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (!stopped[0]) {
          visitElement(expression);
        }
      }

      public void visitClass(PsiClass aClass) {
        stopped[0] = !processor.execute(aClass);
        super.visitClass(aClass);
      }
    });

    return !stopped[0];
  }

  private boolean processAllClassesInGlobalScope(GlobalSearchScope searchScope, PsiElementProcessor<PsiClass> processor) {
    myManager.getRepositoryManager().updateAll();

    LinkedList<PsiDirectory> queue = new LinkedList<PsiDirectory>();
    PsiDirectory[] roots = myManager.getRootDirectories(PsiRootPackageType.SOURCE_PATH);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
    for (final PsiDirectory root : roots) {
      if (fileIndex.isInContent(root.getVirtualFile())) {
        queue.addFirst(root);
      }
    }

    roots = myManager.getRootDirectories(PsiRootPackageType.CLASS_PATH);
    for (PsiDirectory root1 : roots) {
      queue.addFirst(root1);
    }

    while (!queue.isEmpty()) {
      PsiDirectory dir = queue.removeFirst();
      Module module = ModuleUtil.findModuleForPsiElement(dir);
      if (!(module != null ? searchScope.isSearchInModuleContent(module) : searchScope.isSearchInLibraries())) continue;

      PsiDirectory[] subdirectories = dir.getSubdirectories();
      for (PsiDirectory subdirectory : subdirectories) {
        queue.addFirst(subdirectory);
      }

      PsiFile[] files = dir.getFiles();
      for (PsiFile file : files) {
        if (!searchScope.contains(file.getVirtualFile())) continue;
        if (!(file instanceof PsiJavaFile)) continue;

        long fileId = myManager.getRepositoryManager().getFileId(file.getVirtualFile());
        if (fileId >= 0) {
          long[] allClasses = myManager.getRepositoryManager().getFileView().getAllClasses(fileId);
          for (long allClass : allClasses) {
            PsiClass psiClass = (PsiClass)myManager.getRepositoryElementsManager().findOrCreatePsiElementById(allClass);
            if (!processor.execute(psiClass)) return false;
          }
        }
        else {
          if (!processAllClasses(processor, new LocalSearchScope(file))) return false;
        }
      }
    }

    return true;
  }

  public PsiClass[] findAllClasses(SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processAllClasses(processor, searchScope);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  public boolean processElementsWithWord(TextOccurenceProcessor processor,
                                          SearchScope searchScope,
                                          String text,
                                          short searchContext,
                                          boolean caseSensitive) {
    LOG.assertTrue(searchScope != null);

    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text);
      searcher.setCaseSensitive(caseSensitive);

      return processElementsWithTextInGlobalScope(processor,
                                                  (GlobalSearchScope)searchScope,
                                                  searcher,
                                                  searchContext);
    }
    else {
      LocalSearchScope _scope = (LocalSearchScope)searchScope;
      PsiElement[] scopeElements = _scope.getScope();

      for (final PsiElement scopeElement : scopeElements) {
        if (!processElementsWithWordInScopeElement(scopeElement, processor, text, caseSensitive, searchContext)) return false;
      }
      return true;
    }
  }

  private static boolean processElementsWithWordInScopeElement(PsiElement scopeElement,
                                                               TextOccurenceProcessor processor,
                                                               String word,
                                                               boolean caseSensitive,
                                                               final short searchContext) {
    if (SourceTreeToPsiMap.hasTreeElement(scopeElement)) {
      StringSearcher searcher = new StringSearcher(word);
      searcher.setCaseSensitive(caseSensitive);

      return LowLevelSearchUtil.processElementsContainingWordInElement(processor,
                                                                       scopeElement,
                                                                       searcher,
                                                                       null, searchContext);
    }
    else {
      return true;
    }
  }

  private boolean processElementsWithTextInGlobalScope(TextOccurenceProcessor processor,
                                                       GlobalSearchScope scope,
                                                       StringSearcher searcher,
                                                       short searchContext) {

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }
    myManager.startBatchFilesProcessingMode();

    try {
      String[] words = StringUtil.getWordsIn(searcher.getPattern()).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
      if(words.length == 0) return true;

      Set<PsiFile> fileSet = new HashSet<PsiFile>();
      fileSet.addAll(Arrays.asList(myManager.getCacheManager().getFilesWithWord(words[0], searchContext, scope)));
      for (int i = 1; i < words.length; i++) {
        fileSet.retainAll(Arrays.asList(myManager.getCacheManager().getFilesWithWord(words[i], searchContext, scope)));
      }
      PsiFile[] files = fileSet.toArray(new PsiFile[fileSet.size()]);

      if (progress != null) {
        progress.setText(PsiBundle.message("psi.search.for.word.progress", searcher.getPattern()));
      }

      for (int i = 0; i < files.length; i++) {
        ProgressManager.getInstance().checkCanceled();

        PsiFile file = files[i];
        PsiElement[] psiRoots = file.getPsiRoots();
        for (PsiElement psiRoot : psiRoots) {
          if (!LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, progress, searchContext)) {
            return false;
          }
        }

        if (progress != null) {
          double fraction = (double)i / files.length;
          progress.setFraction(fraction);
        }

        myManager.dropResolveCaches();
      }
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
      myManager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  public PsiFile[] findFilesWithPlainTextWords(String word) {
    return myManager.getCacheManager().getFilesWithWord(word,
                                                        UsageSearchContext.IN_PLAIN_TEXT,
                                                        GlobalSearchScope.projectScope(myManager.getProject()));
  }


  public void processUsagesInNonJavaFiles(String qName,
                                          PsiNonJavaFileReferenceProcessor processor,
                                          GlobalSearchScope searchScope) {
    processUsagesInNonJavaFiles(null, qName, processor, searchScope);
  }

  public void processUsagesInNonJavaFiles(@Nullable PsiElement originalElement,
                                          String qName,
                                          PsiNonJavaFileReferenceProcessor processor,
                                          GlobalSearchScope searchScope) {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();

    int dotIndex = qName.lastIndexOf('.');
    int dollarIndex = qName.lastIndexOf('$');
    int maxIndex = Math.max(dotIndex, dollarIndex);
    String wordToSearch = maxIndex >= 0 ? qName.substring(maxIndex + 1) : qName;
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, searchScope);

    StringSearcher searcher = new StringSearcher(qName);
    searcher.setCaseSensitive(true);
    searcher.setForwardDirection(true);

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.search.in.non.java.files.progress"));
    }

    AllFilesLoop:
    for (int i = 0; i < files.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      PsiFile psiFile = files[i];
      char[] text = psiFile.textToCharArray();
      for (int index = LowLevelSearchUtil.searchWord(text, 0, text.length, searcher); index >= 0;) {
        PsiReference referenceAt = psiFile.findReferenceAt(index);
        if (referenceAt == null ||
            originalElement != null && !PsiSearchScopeUtil.isInScope(getUseScope(originalElement).intersectWith(searchScope), psiFile)) {
          if (!processor.process(psiFile, index, index + searcher.getPattern().length())) break AllFilesLoop;
        }

        index = LowLevelSearchUtil.searchWord(text, index + searcher.getPattern().length(), text.length, searcher);
      }

      if (progress != null) {
        progress.setFraction((double)(i + 1) / files.length);
      }
    }

    if (progress != null) {
      progress.popState();
    }
  }

  public PsiFile[] findFormsBoundToClass(String className) {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myManager.getProject());
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(className, UsageSearchContext.IN_FOREIGN_LANGUAGES,
                                                                   projectScope);
    List<PsiFile> boundForms = new ArrayList<PsiFile>(files.length);
    for (PsiFile psiFile : files) {
      if (psiFile.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;

      String text = psiFile.getText();
      try {
        String boundClass = Utils.getBoundClassName(text);
        if (className.equals(boundClass)) boundForms.add(psiFile);
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }

    return boundForms.toArray(new PsiFile[boundForms.size()]);
  }

  public boolean isFieldBoundToForm(PsiField field) {
    PsiClass aClass = field.getContainingClass();
    if (aClass != null && aClass.getQualifiedName() != null) {
      PsiFile[] formFiles = findFormsBoundToClass(aClass.getQualifiedName());
      for (PsiFile file : formFiles) {
        final PsiReference[] references = file.getReferences();
        for (final PsiReference reference : references) {
          if (reference.isReferenceTo(field)) return true;
        }
      }
    }

    return false;
  }

  public void processAllFilesWithWord(String word, GlobalSearchScope scope, Processor<PsiFile> processor) {
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(word, UsageSearchContext.IN_CODE, scope);

    for (PsiFile file : files) {
      if (!processor.process(file)) return;
    }
  }

  public void processAllFilesWithWordInComments(String word, GlobalSearchScope scope, Processor<PsiFile> processor) {
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(word, UsageSearchContext.IN_COMMENTS, scope);

    for (PsiFile file : files) {
      if (!processor.process(file)) return;
    }
  }

  public void processAllFilesWithWordInLiterals(String word, GlobalSearchScope scope, Processor<PsiFile> processor) {
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(word, UsageSearchContext.IN_STRINGS, scope);

    for (PsiFile file : files) {
      if (!processor.process(file)) return;
    }
  }

  private static PsiMethod[] getOverloadsMayBeOverriden(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};
    PsiMethod[] methods = aClass.findMethodsByName(method.getName(), false);
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    for (PsiMethod psiMethod : methods) {
      PsiModifierList modList = psiMethod.getModifierList();
      if (!modList.hasModifierProperty(PsiModifier.STATIC) &&
          !modList.hasModifierProperty(PsiModifier.FINAL)) {
        result.add(psiMethod);
      }
    }

    //Should not happen
    if (result.size() == 0) return new PsiMethod[]{method};

    return result.toArray(new PsiMethod[result.size()]);
  }
}
