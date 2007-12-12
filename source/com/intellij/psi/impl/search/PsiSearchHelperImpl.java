package com.intellij.psi.impl.search;

import com.intellij.concurrency.JobUtil;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
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
    AllClassesSearch.INSTANCE.registerExecutor(new AllClassesSearchExecutor());

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
          aPackage = JavaPsiFacade.getInstance(element.getManager().getProject()).findPackage(((PsiJavaFile)file).getPackageName());
        }

        if (aPackage == null) {
          PsiDirectory dir = file.getContainingDirectory();
          if (dir != null) {
            aPackage = JavaDirectoryService.getInstance().getPackage(dir);
          }
        }

        if (aPackage != null) {
          SearchScope scope = PackageScope.packageScope(aPackage, false);
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
        PsiPackage aPackage = file instanceof PsiJavaFile ? JavaPsiFacade.getInstance(myManager.getProject())
          .findPackage(((PsiJavaFile)file).getPackageName()) : null;
        if (aPackage != null) {
          SearchScope scope = PackageScope.packageScope(aPackage, false);
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
  public PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope) {
    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
        if (parserDefinition == null) return true;

        if (element.getNode() != null && !parserDefinition.getCommentTokens().contains(element.getNode().getElementType())) return true;
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
      LocalSearchScope scope = (LocalSearchScope)searchScope;
      PsiElement[] scopeElements = scope.getScope();
      final boolean ignoreInjectedPsi = scope.isIgnoreInjectedPsi();

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
    }).booleanValue();
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
      List<String> words = StringUtil.getWordsIn(searcher.getPattern());
      if (words.isEmpty()) return true;
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
      final AtomicBoolean pceThrown = new AtomicBoolean(false);

      boolean completed = JobUtil.invokeConcurrentlyForAll(files, new Processor<PsiFile>() {
        public boolean process(final PsiFile file) {
          if (file instanceof PsiBinaryFile) return true;

          ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                public void run() {
                  try {
                    PsiElement[] psiRoots = file.getPsiRoots();
                    Set<PsiElement> processed = new HashSet<PsiElement>(psiRoots.length * 2, (float)0.5);
                    for (PsiElement psiRoot : psiRoots) {
                      ProgressManager.getInstance().checkCanceled();
                      if (!processed.add(psiRoot)) continue;
                      if (!LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, false)) {
                        canceled.set(true);
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
                    canceled.set(true);
                    pceThrown.set(true);
                  }
                }
              });
            }
          }, progress);
          return !canceled.get();
        }
      }, "Process usages in files");

      if (pceThrown.get()) {
        throw new ProcessCanceledException();
      }

      return completed;
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
