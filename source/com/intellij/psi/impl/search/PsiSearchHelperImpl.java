package com.intellij.psi.impl.search;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");

  private final PsiManagerEx myManager;
  private static final TodoItem[] EMPTY_TODO_ITEMS = new TodoItem[0];

  static {
    ReferencesSearch.INSTANCE.registerExecutor(new CachesBasedRefSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new PsiAnnotationMethodReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new ConstructorReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new SimpleAccessorReferenceSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new PropertyReferenceViaLastWordSearcher());

    DirectClassInheritorsSearch.INSTANCE.registerExecutor(new JavaDirectInheritorsSearcher());

    OverridingMethodsSearch.INSTANCE.registerExecutor(new JavaOverridingMethodsSearcher());

    AllOverridingMethodsSearch.INSTANCE.registerExecutor(new JavaAllOverridingMethodsSearcher());

    MethodReferencesSearch.INSTANCE.registerExecutor(new MethodUsagesSearcher());

    AnnotatedMembersSearch.INSTANCE.registerExecutor(new AnnotatedMembersSearcher());

    SuperMethodsSearch.SUPER_METHODS_SEARCH_INSTANCE.registerExecutor(new MethodSuperSearcher());
    DeepestSuperMethodsSearch.DEEPEST_SUPER_METHODS_SEARCH_INSTANCE.registerExecutor(new MethodDeepestSuperSearcher());

    IndexPatternSearch.INDEX_PATTERN_SEARCH_INSTANCE = new IndexPatternSearchImpl();
  }

  @NotNull
  public SearchScope getUseScope(@NotNull PsiElement element) {
    final GlobalSearchScope maximalUseScope = myManager.getFileManager().getUseScope(element);
    if (element instanceof PsiPackage) {
      return maximalUseScope;
    }
    else if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        return new LocalSearchScope(element);
      }
      PsiFile file = element.getContainingFile();
      if (PsiUtil.isInJspFile(file)) return maximalUseScope;
      PsiClass aClass = (PsiClass)element;
      final PsiClass containingClass = aClass.getContainingClass();
      if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return containingClass != null ? containingClass.getUseScope() : maximalUseScope;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return containingClass != null ? containingClass.getUseScope() : maximalUseScope;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PRIVATE) || aClass instanceof PsiTypeParameter) {
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
      if (PsiUtil.isInJspFile(file)) return maximalUseScope;

      PsiClass aClass = member.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        //member from anonymous class can be called from outside the class
        PsiElement methodCallExpr = PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression.class);
        return new LocalSearchScope(methodCallExpr != null ? methodCallExpr : aClass);
      }

      if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
        return aClass != null ? aClass.getUseScope() : maximalUseScope;
      }
      else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
        return aClass != null ? aClass.getUseScope() : maximalUseScope;
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
    else if (element instanceof Property) {
      // property ref can occur in any file
      return GlobalSearchScope.allScope(myManager.getProject());
    }
    else {
      return maximalUseScope;
    }
  }


  public PsiSearchHelperImpl(PsiManagerEx manager) {
    myManager = manager;
  }

  @NotNull
  public PsiReference[] findReferences(@NotNull PsiElement element, @NotNull SearchScope searchScope, boolean ignoreAccessScope) {
    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferences(processor, element, searchScope, ignoreAccessScope);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferences(@NotNull final PsiReferenceProcessor processor,
                                   @NotNull final PsiElement refElement,
                                   @NotNull SearchScope originalScope,
                                   boolean ignoreAccessScope) {
    final Query<PsiReference> query = ReferencesSearch.search(refElement, originalScope, ignoreAccessScope);
    return query.forEach(new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference psiReference) {
        return processor.execute(psiReference);
      }
    });
  }

  @NotNull
  public PsiMethod[] findOverridingMethods(@NotNull PsiMethod method, @NotNull SearchScope searchScope, boolean checkDeep) {
    PsiElementProcessor.CollectElements<PsiMethod> processor = new PsiElementProcessor.CollectElements<PsiMethod>();
    processOverridingMethods(processor, method, searchScope, checkDeep);

    return processor.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public boolean processOverridingMethods(@NotNull final PsiElementProcessor<PsiMethod> processor,
                                          @NotNull final PsiMethod method,
                                          @NotNull SearchScope searchScope,
                                          final boolean checkDeep) {
    return OverridingMethodsSearch.search(method, searchScope, checkDeep).forEach(new ReadActionProcessor<PsiMethod>() {
      public boolean processInReadAction(final PsiMethod psiMethod) {
        return processor.execute(psiMethod);
      }
    });
  }

  @NotNull
  public PsiReference[] findReferencesIncludingOverriding(@NotNull final PsiMethod method,
                                                          @NotNull SearchScope searchScope,
                                                          boolean isStrictSignatureSearch) {
    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferencesIncludingOverriding(processor, method, searchScope, isStrictSignatureSearch);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferencesIncludingOverriding(@NotNull final PsiReferenceProcessor processor,
                                                      @NotNull final PsiMethod method,
                                                      @NotNull SearchScope searchScope) {
    return processReferencesIncludingOverriding(processor, method, searchScope, true);
  }

  public boolean processReferencesIncludingOverriding(@NotNull final PsiReferenceProcessor processor,
                                                      @NotNull final PsiMethod method,
                                                      @NotNull SearchScope searchScope,
                                                      final boolean isStrictSignatureSearch) {
    return MethodReferencesSearch.search(method, searchScope, isStrictSignatureSearch).forEach(new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference psiReference) {
        return processor.execute(psiReference);
      }
    });
  }

  @NotNull
  public PsiClass[] findInheritors(@NotNull PsiClass aClass, @NotNull SearchScope searchScope, boolean checkDeep) {
    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processInheritors(processor, aClass, searchScope, checkDeep);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  public boolean processInheritors(@NotNull PsiElementProcessor<PsiClass> processor,
                                   @NotNull PsiClass aClass,
                                   @NotNull SearchScope searchScope,
                                   boolean checkDeep) {
    return processInheritors(processor, aClass, searchScope, checkDeep, true);
  }

  public boolean processInheritors(@NotNull final PsiElementProcessor<PsiClass> processor,
                                   @NotNull PsiClass aClass,
                                   @NotNull SearchScope searchScope,
                                   boolean checkDeep,
                                   boolean checkInheritance) {
    return ClassInheritorsSearch.search(aClass, searchScope, checkDeep, checkInheritance).forEach(new ReadActionProcessor<PsiClass>() {
      public boolean processInReadAction(final PsiClass psiClass) {
        return processor.execute(psiClass);
      }
    });
  }

  @NotNull
  public PsiFile[] findFilesWithTodoItems() {
    return myManager.getCacheManager().getFilesWithTodoItems();
  }

  @NotNull
  public TodoItem[] findTodoItems(@NotNull PsiFile file) {
    return doFindTodoItems(file, new TextRange(0, file.getTextLength()));
  }

  @NotNull
  public TodoItem[] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
    return doFindTodoItems(file, new TextRange(startOffset, endOffset));
  }

  private static TodoItem[] doFindTodoItems(final PsiFile file, final TextRange textRange) {
    final Collection<IndexPatternOccurrence> occurrences = IndexPatternSearch.search(file, TodoConfiguration.getInstance()).findAll();
    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

    List<TodoItem> items = new ArrayList<TodoItem>(occurrences.size());
    for(IndexPatternOccurrence occurrence: occurrences) {
      if (!textRange.contains(occurrence.getTextRange())) continue;
      items.add(new TodoItemImpl(occurrence.getFile(), occurrence.getTextRange().getStartOffset(), occurrence.getTextRange().getEndOffset(),
                                 mapPattern(occurrence.getPattern())));
    }

    return items.toArray(new TodoItem[items.size()]);
  }

  private static TodoPattern mapPattern(final IndexPattern pattern) {
    for(TodoPattern todoPattern: TodoConfiguration.getInstance().getTodoPatterns()) {
      if (todoPattern.getIndexPattern() == pattern) {
        return todoPattern;
      }
    }
    LOG.assertTrue(false, "Could not find matching TODO pattern for index pattern " + pattern.getPatternString());
    return null;
  }

  public int getTodoItemsCount(@NotNull PsiFile file) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), TodoConfiguration.getInstance());
    if (count != -1) return count;
    return findTodoItems(file).length;
  }

  public int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), pattern.getIndexPattern());
    if (count != -1) return count;
    TodoItem[] items = findTodoItems(file);
    count = 0;
    for (TodoItem item : items) {
      if (item.getPattern().equals(pattern)) count++;
    }
    return count;
  }

  @NotNull
  public PsiIdentifier[] findIdentifiers(@NotNull String identifier, @NotNull SearchScope searchScope, short searchContext) {
    PsiElementProcessor.CollectElements<PsiIdentifier> processor = new PsiElementProcessor.CollectElements<PsiIdentifier>();
    processIdentifiers(processor, identifier, searchScope, searchContext);
    return processor.toArray(PsiIdentifier.EMPTY_ARRAY);
  }

  public boolean processIdentifiers(@NotNull final PsiElementProcessor<PsiIdentifier> processor,
                                    @NotNull final String identifier,
                                    @NotNull SearchScope searchScope,
                                    short searchContext) {
    TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        return !(element instanceof PsiIdentifier) || processor.execute((PsiIdentifier)element);
      }
    };
    return processElementsWithWord(processor1, searchScope, identifier, searchContext, true);
  }

  private static final TokenSet COMMENT_BIT_SET = TokenSet.create(JavaDocTokenType.DOC_COMMENT_DATA, JavaDocTokenType.DOC_TAG_VALUE_TOKEN,
                                                                  JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT);

  @NotNull
  public PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope) {
    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element.getNode() != null && !COMMENT_BIT_SET.contains(element.getNode().getElementType())) return true;
        if (element.findReferenceAt(offsetInElement) == null) {
          synchronized (results) {
            results.add(element);
          }
        }
        return true;
      }
    };
    processElementsWithWord(processor, searchScope, identifier, UsageSearchContext.IN_COMMENTS, true);
    return results.toArray(new PsiElement[results.size()]);
  }

  @NotNull
  public PsiLiteralExpression[] findStringLiteralsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope) {
    final ArrayList<PsiLiteralExpression> results = new ArrayList<PsiLiteralExpression>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element instanceof PsiLiteralExpression) {
          synchronized (results) {
            results.add((PsiLiteralExpression)element);
          }
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

  public boolean processAllClasses(@NotNull final PsiElementProcessor<PsiClass> processor, @NotNull SearchScope searchScope) {
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

  private boolean processAllClassesInGlobalScope(final GlobalSearchScope searchScope, final PsiElementProcessor<PsiClass> processor) {
    myManager.getRepositoryManager().updateAll();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
    return fileIndex.iterateContent(new ContentIterator() {
      public boolean processFile(final VirtualFile fileOrDir) {
        return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            if (!fileOrDir.isDirectory() && searchScope.contains(fileOrDir)) {
              final PsiFile psiFile = myManager.findFile(fileOrDir);
              if (psiFile instanceof PsiJavaFile) {
                long fileId = myManager.getRepositoryManager().getFileId(fileOrDir);
                if (fileId >= 0) {
                  long[] allClasses = myManager.getRepositoryManager().getFileView().getAllClasses(fileId);
                  for (long allClass : allClasses) {
                    PsiClass psiClass = (PsiClass)myManager.getRepositoryElementsManager().findOrCreatePsiElementById(allClass);
                    if (!processor.execute(psiClass)) return false;
                  }
                }
                else {
                  if (!processScopeRootForAllClasses(psiFile, processor)) return false;
                }
              }
            }
            return true;
          }
        });
      }
    });
  }

  @NotNull
  public PsiClass[] findAllClasses(@NotNull SearchScope searchScope) {
    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processAllClasses(processor, searchScope);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  public boolean processElementsWithWord(@NotNull TextOccurenceProcessor processor,
                                          @NotNull SearchScope searchScope,
                                          @NotNull String text,
                                          short searchContext,
                                          boolean caseSensitively) {
    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text);
      searcher.setCaseSensitive(caseSensitively);

      return processElementsWithTextInGlobalScope(processor,
                                                  (GlobalSearchScope)searchScope,
                                                  searcher,
                                                  searchContext, caseSensitively);
    }
    else {
      LocalSearchScope _scope = (LocalSearchScope)searchScope;
      PsiElement[] scopeElements = _scope.getScope();
      final boolean ignoreInjectedPsi = _scope.isIgnoreInjectedPsi();

      for (final PsiElement scopeElement : scopeElements) {
        if (!processElementsWithWordInScopeElement(scopeElement, processor, text, caseSensitively, ignoreInjectedPsi)) return false;
      }
      return true;
    }
  }

  private static boolean processElementsWithWordInScopeElement(final PsiElement scopeElement,
                                                               final TextOccurenceProcessor processor,
                                                               final String word,
                                                               final boolean caseSensitive,
                                                               final boolean ignoreInjectedPsi) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        StringSearcher searcher = new StringSearcher(word);
        searcher.setCaseSensitive(caseSensitive);

        return LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher, ignoreInjectedPsi);
      }
    });
  }

  private boolean processElementsWithTextInGlobalScope(final TextOccurenceProcessor processor,
                                                       final GlobalSearchScope scope,
                                                       final StringSearcher searcher,
                                                       final short searchContext,
                                                       final boolean caseSensitively) {
    LOG.assertTrue(!Thread.holdsLock(PsiLock.LOCK), "You must not run search from within updating PSI activity. Please consider invokeLatering it instead.");
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }
    myManager.startBatchFilesProcessingMode();

    try {
      String[] words = StringUtil.getWordsIn(searcher.getPattern()).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
      if (words.length == 0) return true;

      Set<PsiFile> fileSet = new THashSet<PsiFile>();
      final Application application = ApplicationManager.getApplication();
      for (final String word : words) {
        List<PsiFile> psiFiles = application.runReadAction(new Computable<List<PsiFile>>() {
          public List<PsiFile> compute() {
            return Arrays.asList(myManager.getCacheManager().getFilesWithWord(word, searchContext, scope, caseSensitively));
          }
        });
        if (fileSet.isEmpty()) {
          fileSet.addAll(psiFiles);
        }
        else {
          fileSet.retainAll(psiFiles);
        }
        if (fileSet.isEmpty()) break;
      }
      final PsiFile[] files = fileSet.toArray(new PsiFile[fileSet.size()]);

      if (progress != null) {
        progress.setText(PsiBundle.message("psi.search.for.word.progress", searcher.getPattern()));
      }

      final AtomicInteger counter = new AtomicInteger(0);
      final AtomicBoolean canceled = new AtomicBoolean(false);
      final Job<?> processFilesJob =
        JobScheduler.getInstance().createJob("Process usages in files", Job.DEFAULT_PRIORITY); // TODO: Better name
      for (final PsiFile file : files) {
        processFilesJob.addTask(new Runnable() {
          public void run() {
            ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  public void run() {
                    try {
                      PsiElement[] psiRoots = file.getPsiRoots();
                      Set<PsiElement> processed = new HashSet<PsiElement>(psiRoots.length * 2, (float)0.5);
                      for (PsiElement psiRoot : psiRoots) {
                        if (CachesBasedRefSearcher.DEBUG) {
                          System.out.println("Scanning root:" + psiRoot + " lang:" + psiRoot.getLanguage() + " file:" +
                                             psiRoot.getContainingFile().getName());
                        }
                        ProgressManager.getInstance().checkCanceled();
                        if (!processed.add(psiRoot)) continue;
                        if (!LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, false)) {
                          if (CachesBasedRefSearcher.DEBUG) System.out.println(" cancelling subsequent file scan");
                          processFilesJob.cancel();
                          return;
                        }
                      }
                      if (progress != null) {
                        double fraction = (double)counter.incrementAndGet() / files.length;
                        progress.setFraction(fraction);
                      }
                      myManager.dropResolveCaches();
                    }
                    catch (ProcessCanceledException e) {
                      processFilesJob.cancel();
                      canceled.set(true);
                    }
                  }
                });
              }
            }, progress);
          }
        });
      }

      try {
        processFilesJob.scheduleAndWaitForResults();
        if (CachesBasedRefSearcher.DEBUG) {
          System.out.println("Job finished");
        }
      }
      catch (Throwable throwable) {
        LOG.error(throwable);
        return false;
      }

      if (canceled.get()) {
        throw new ProcessCanceledException();
      }

      return !processFilesJob.isCanceled();
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
      myManager.finishBatchFilesProcessingMode();
    }
  }

  @NotNull
  public PsiFile[] findFilesWithPlainTextWords(@NotNull String word) {
    return myManager.getCacheManager().getFilesWithWord(word,
                                                        UsageSearchContext.IN_PLAIN_TEXT,
                                                        GlobalSearchScope.projectScope(myManager.getProject()), true);
  }


  public void processUsagesInNonJavaFiles(@NotNull String qName,
                                          @NotNull PsiNonJavaFileReferenceProcessor processor,
                                          @NotNull GlobalSearchScope searchScope) {
    processUsagesInNonJavaFiles(null, qName, processor, searchScope);
  }

  public void processUsagesInNonJavaFiles(@Nullable final PsiElement originalElement,
                                          @NotNull String qName,
                                          @NotNull final PsiNonJavaFileReferenceProcessor processor,
                                          @NotNull GlobalSearchScope searchScope) {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();

    int dotIndex = qName.lastIndexOf('.');
    int dollarIndex = qName.lastIndexOf('$');
    int maxIndex = Math.max(dotIndex, dollarIndex);
    String wordToSearch = maxIndex >= 0 ? qName.substring(maxIndex + 1) : qName;
    if (originalElement != null && myManager.isInProject(originalElement) && searchScope.isSearchInLibraries()) {
      searchScope = searchScope.intersectWith(GlobalSearchScope.projectScope(myManager.getProject()));
    }
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, searchScope, true);

    final StringSearcher searcher = new StringSearcher(qName);
    searcher.setCaseSensitive(true);
    searcher.setForwardDirection(true);

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.search.in.non.java.files.progress"));
    }

    final Ref<Boolean> cancelled = new Ref<Boolean>(Boolean.FALSE);
    final GlobalSearchScope finalScope = searchScope;
    for (int i = 0; i < files.length; i++) {
      progressManager.checkCanceled();

      final PsiFile psiFile = files[i];

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          CharSequence text = psiFile.getViewProvider().getContents();
          for (int index = LowLevelSearchUtil.searchWord(text, 0, text.length(), searcher); index >= 0;) {
            PsiReference referenceAt = psiFile.findReferenceAt(index);
            if (referenceAt == null || originalElement == null ||
                !PsiSearchScopeUtil.isInScope(getUseScope(originalElement).intersectWith(finalScope), psiFile)) {
              if (!processor.process(psiFile, index, index + searcher.getPattern().length())) {
                cancelled.set(Boolean.TRUE);
                return;
              }
            }

            index = LowLevelSearchUtil.searchWord(text, index + searcher.getPattern().length(), text.length(), searcher);
          }
        }
      });
      if (cancelled.get()) break;
      if (progress != null) {
        progress.setFraction((double)(i + 1) / files.length);
      }
    }

    if (progress != null) {
      progress.popState();
    }
  }

  @NotNull
  public PsiFile[] findFormsBoundToClass(String className) {
    if (className == null) return PsiFile.EMPTY_ARRAY;
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myManager.getProject());
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(className, UsageSearchContext.IN_FOREIGN_LANGUAGES, projectScope, true);
    if (files.length == 0) return PsiFile.EMPTY_ARRAY;
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

  public boolean isFieldBoundToForm(@NotNull PsiField field) {
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

  public void processAllFilesWithWord(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor, final boolean caseSensitively) {
    myManager.getCacheManager().processFilesWithWord(processor,word, UsageSearchContext.IN_CODE, scope, caseSensitively);
  }

  public void processAllFilesWithWordInText(@NotNull final String word, @NotNull final GlobalSearchScope scope, @NotNull final Processor<PsiFile> processor,
                                            final boolean caseSensitively) {
    myManager.getCacheManager().processFilesWithWord(processor,word, UsageSearchContext.IN_PLAIN_TEXT, scope, caseSensitively);
  }

  public void processAllFilesWithWordInComments(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor) {
    myManager.getCacheManager().processFilesWithWord(processor, word, UsageSearchContext.IN_COMMENTS, scope, true);
  }

  public void processAllFilesWithWordInLiterals(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor) {
    myManager.getCacheManager().processFilesWithWord(processor, word, UsageSearchContext.IN_STRINGS, scope, true);
  }
}
