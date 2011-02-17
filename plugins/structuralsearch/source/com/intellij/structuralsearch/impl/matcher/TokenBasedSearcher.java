package com.intellij.structuralsearch.impl.matcher;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.tokenindex.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class TokenBasedSearcher {
  private final boolean myTesting;

  private final MatcherImpl myMatcher;
  private List<Token> myTokens;
  private int[] myOccurences;

  private final MyMatchingResultSink mySink;
  private boolean myToFilterLocalScope;

  public TokenBasedSearcher(MatcherImpl matcher) {
    myTesting = ApplicationManager.getApplication().isUnitTestMode();
    myMatcher = matcher;
    mySink = new MyMatchingResultSink(myMatcher.getMatchContext().getSink());
  }

  private static class MyMatchingResultSink implements MatchResultSink {
    private final MatchResultSink myDelegate;
    private final Set<TokenBasedMatchResult> myResultsWithoutLexicalInfo = new HashSet<TokenBasedMatchResult>();

    private int myStart;
    private int myEnd;

    private MyMatchingResultSink(MatchResultSink delegate) {
      myDelegate = delegate;
    }

    public void processLexicalOccurence(int start, int end) {
      myStart = start;
      myEnd = end;
      for (Iterator<TokenBasedMatchResult> iterator = myResultsWithoutLexicalInfo.iterator(); iterator.hasNext();) {
        TokenBasedMatchResult result = iterator.next();
        if (isOurElement(result.getMatch())) {
          result.setTextRangeInFile(new TextRange(start, end));
          iterator.remove();
          myDelegate.newMatch(result);
        }
      }
    }

    private boolean isOurElement(@NotNull PsiElement element) {
      PsiElement parent = element;
      while (parent != null) {
        if (parent.getTextRange().contains(myStart)) {
          return true;
        }
        PsiElement gp = parent.getParent();
        if (gp != null && gp.getFirstChild() != parent) {
          break;
        }
        parent = gp;
      }
      PsiElement child = element.getFirstChild();
      while (child != null) {
        if (child.getTextRange().contains(myStart)) {
          return true;
        }
        child = child.getFirstChild();
      }
      return false;
    }

    public void newMatch(MatchResult result) {
      PsiElement element = result.getMatch();
      TextRange range = element.getTextRange();
      if (myStart <= range.getStartOffset() && myEnd > range.getEndOffset()) {
        // inner match
        // TokenBasedProfile replacement cannot process it
        myDelegate.newMatch(new TokenBasedMatchResult(result, null));
      }
      else if (myStart >= 0 && isOurElement(element)) {
        myDelegate.newMatch(new TokenBasedMatchResult(result, new TextRange(myStart, myEnd)));
        myStart = -1;
      }
      else {
        TokenBasedMatchResult res = new TokenBasedMatchResult(result, null);
        myResultsWithoutLexicalInfo.add(res);
      }
    }

    public void processFile(PsiFile element) {
      myDelegate.processFile(element);
    }

    public void setMatchingProcess(MatchingProcess matchingProcess) {
      myDelegate.setMatchingProcess(matchingProcess);
    }

    public void matchingFinished() {
      myDelegate.matchingFinished();
    }

    public ProgressIndicator getProgressIndicator() {
      return myDelegate.getProgressIndicator();
    }
  }

  public int search(CompiledPattern compiledPattern) {
    final Set<Language> patternLanguages = getLanguages(compiledPattern);
    assert patternLanguages.size() == 1;
    final List<PsiElement> patternRoots = getNodes(compiledPattern);
    MatchOptions options = myMatcher.getMatchContext().getOptions();
    final SearchScope scope = compiledPattern.getScope() != null ? compiledPattern.getScope() : options.getScope();

    if (scope instanceof LocalSearchScope) {
      for (PsiElement element : ((LocalSearchScope)scope).getScope()) {
        if (!(element instanceof PsiFile)) {
          myToFilterLocalScope = true;
          break;
        }
      }
    }

    RecursiveTokenizingVisitor visitor = new RecursiveTokenizingVisitor();
    for (PsiElement patternRoot : patternRoots) {
      patternRoot.accept(visitor);
    }
    final List<Token> patternTokens = visitor.getTokens();

    addTask(new Runnable() {
      public void run() {
        readTokens(patternLanguages, scope);
        ProgressIndicator progress = myMatcher.getProgress();
        if (progress != null) {
          progress.setFraction(0.15);
        }
      }
    });

    return ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
      public Integer compute() {
        return search(patternTokens, scope);
      }
    });
  }

  private void addTask(Runnable r) {
    if (myTesting) {
      r.run();
    }
    else {
      myMatcher.getScheduler().addOneTask(r);
    }
  }

  private void readTokens(Set<Language> patternLanguages, SearchScope scope) {
    assert scope instanceof GlobalSearchScope || scope instanceof LocalSearchScope;

    if (scope instanceof GlobalSearchScope) {
      myTokens = getTokensFromIndex(myMatcher.getProject(), patternLanguages, scope);
    }
    else {
      Set<PsiFile> files = new HashSet<PsiFile>();
      PsiElement[] elements = ((LocalSearchScope)scope).getScope();
      if (elements.length == 0) {
        return;
      }
      for (PsiElement element : elements) {
        files.add(element instanceof PsiFile ? (PsiFile)element : element.getContainingFile());
      }
      List<PsiElement> elementList = new ArrayList<PsiElement>();
      Collections.addAll(elementList, elements);
      myTokens = new ArrayList<Token>();
      for (PsiFile file : files) {
        RecursiveTokenizingVisitor visitor = new RecursiveTokenizingVisitor(myTokens, patternLanguages);
        int before = myTokens.size();
        file.accept(visitor);
        if (before < myTokens.size()) {
          myTokens.add(new PsiMarkerToken(file));
        }
      }
    }
  }

  @Nullable
  private static List<PsiElement> computeScope(PsiFile file, int start, int end) {
    if (start == end) {
      return Collections.emptyList();
    }
    int length = end - start;
    PsiElement element = InjectedLanguageUtil.findElementAtNoCommit(file, start);
    while (element != null) {
      if (element == file || element.getTextLength() >= length) {
        return Arrays.asList(element);
      }
      element = element.getParent();
    }
    return null;
  }

  private static TextRange computeTextRangeInsideInjection(int start, int end, PsiElement context) {
    PsiFile file = context instanceof PsiFile ? (PsiFile)context : context.getContainingFile();
    PsiElement fileContext = file.getContext();
    if (fileContext instanceof PsiLanguageInjectionHost) {
      final int[] hostStart = {0};
      final int[] hostEnd = {0};
      ((PsiLanguageInjectionHost)fileContext).processInjectedPsi(new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        @Override
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          if (places.size() == 0) return;
          RangeMarker rangeMarker = places.get(0).getHostRangeMarker();
          hostStart[0] = rangeMarker.getStartOffset();
          hostEnd[0] = rangeMarker.getEndOffset();
        }
      });
      return new TextRange(start - hostStart[0], end - hostStart[0]);
    }
    return new TextRange(start, end);
  }

  private class MySearcher implements Runnable {
    private final TIntObjectHashMap<PsiFile> myPsiFiles = new TIntObjectHashMap<PsiFile>();
    private final int myPatternLength;
    private final Set<PsiElement> myVisitedScopes = new HashSet<PsiElement>();
    private final SearchScope myScope;
    private double myStartFraction = -1;

    private int myIndex = 0;
    private int myBound = 0;

    private MySearcher(int patternLength, SearchScope scope) {
      myPatternLength = patternLength;
      myScope = scope;
    }

    public void run() {
      ProgressIndicator progress = myMatcher.getProgress();
      if (progress != null && myStartFraction < 0) {
        myStartFraction = progress.getFraction();
      }
      if (myIndex >= myOccurences.length) {
        return;
      }
      if (myIndex > 0) {
        if (progress != null) {
          progress.setFraction(myStartFraction + (1.0 - myStartFraction) / myOccurences.length * myIndex);
        }
      }
      int occurence = myOccurences[myIndex];
      if (occurence < myBound) {
        doContinue();
        return;
      }
      final PsiFile psiFile = getPsiFileForOccurence(myTokens, occurence, myPsiFiles);
      if (psiFile == null) {
        doContinue();
        return;
      }
      myPsiFiles.put(occurence, psiFile);
      final int start = myTokens.get(occurence).getStart();
      assert start >= 0;
      final int end = myTokens.get(occurence + myPatternLength - 1).getEnd();

      final MatchContext context = myMatcher.getMatchContext();

      List<PsiElement> elementList;
      if (myToFilterLocalScope && myScope instanceof LocalSearchScope && !ApplicationManager.getApplication().isUnitTestMode()) {
        elementList = filterScope((LocalSearchScope)myScope, psiFile, start, end);
      }
      else {
        elementList = ApplicationManager.getApplication().runReadAction(new Computable<List<PsiElement>>() {
          @Nullable
          @Override
          public List<PsiElement> compute() {
            return computeScope(psiFile, start, end);
          }
        });
      }
      assert elementList != null;
      final PsiElement[] elements = PsiUtilBase.toPsiElementArray(elementList);
      if (elements.length > 0) {
        context.setResult(null);
        myBound = occurence + myPatternLength;
        final TextRange range = computeTextRangeInsideInjection(start, end, elements[0]);
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            mySink.processLexicalOccurence(range.getStartOffset(), range.getEndOffset());
            if (myVisitedScopes.add(elements[0])) {
              context.setSink(mySink);
              context.getMatcher().matchContext(new FilteringNodeIterator(new ArrayBackedNodeIterator(elements)));
            }
          }
        });
      }
      doContinue();
    }

    private void doContinue() {
      myIndex++;
      addTask(this);
    }
  }

  private int search(final List<Token> patternTokens, SearchScope scope) {
    addTask(new Runnable() {
      public void run() {
        if (myTokens == null || myTokens.size() == 0) {
          myOccurences = ArrayUtil.EMPTY_INT_ARRAY;
          return;
        }
        myOccurences = kmp(myTokens, patternTokens);
        ProgressIndicator progress = myMatcher.getProgress();
        if (progress != null) {
          progress.setFraction(0.3);
        }
      }
    });

    final int patternLength = patternTokens.size();
    assert patternLength > 0;

    addTask(new MySearcher(patternLength, scope));

    return myOccurences != null ? myOccurences.length : 0;
  }

  @Nullable
  private PsiFile getPsiFileForOccurence(List<Token> tokens, int occurence, TIntObjectHashMap<PsiFile> myPsiFiles) {
    PsiFile psiFile = null;
    for (int i = occurence; i < tokens.size(); i++) {
      if (myPsiFiles.contains(i)) {
        psiFile = myPsiFiles.get(i);
        break;
      }
      Token token = tokens.get(i);
      if (token instanceof PathMarkerToken) {
        String path = ((PathMarkerToken)token).getPath();
        final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file != null) {
          psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            @Nullable
            public PsiFile compute() {
              return PsiManager.getInstance(myMatcher.getProject()).findFile(file);
            }
          });
        }
        break;
      }
      else if (token instanceof PsiMarkerToken) {
        psiFile = ((PsiMarkerToken)token).getFile();
        break;
      }
    }
    return psiFile;
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private static int[] kmp(List<Token> tokens, List<Token> pattern) {
    int m = pattern.size();
    int n = tokens.size();

    if (n < m) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    int[] pf = new int[m];
    pf[0] = 0;
    for (int k = 0, i = 1; i < m; i++) {
      while (k > 0 && !pattern.get(i).equals(pattern.get(k))) {
        k = pf[k - 1];
      }

      if (pattern.get(i).equals(pattern.get(k))) {
        k++;
      }

      pf[i] = k;
    }

    IntArrayList result = new IntArrayList();
    for (int k = 0, i = 0; i < n; i++) {
      while ((k > 0) && !pattern.get(k).equals(tokens.get(i))) {
        k = pf[k - 1];
      }

      if (pattern.get(k).equals(tokens.get(i))) {
        k++;
      }

      if (k == m) {
        result.add(i - m + 1);
        k = pf[k - 1];
      }
    }
    return result.toArray();
  }

  private static List<PsiElement> filterScope(LocalSearchScope scope, PsiFile file, int start, int end) {
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (PsiElement element : scope.getScope()) {
      if (file == element) {
        elements.add(element);
        break;
      }
      else if (file == element.getContainingFile()) {
        TextRange range = element.getTextRange();
        if (end > range.getStartOffset() && start < range.getEndOffset()) {
          elements.add(element);
          continue;
        }
      }
      if (elements.size() > 0) {
        break;
      }
    }
    return elements;
  }

  private static <T> boolean intersect(Set<T> set1, Set<T> set2) {
    for (T o : set1) {
      if (set2.contains(o)) {
        return true;
      }
    }
    return false;
  }

  private static List<Token> getTokensFromIndex(final Project project, final Collection<Language> languages, final SearchScope scope) {
    final Set<String> languageIds = new HashSet<String>();
    for (Language language : languages) {
      languageIds.add(language.getID());
    }
    final List<Token> result = new ArrayList<Token>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
        Collection<TokenIndexKey> keys = fileBasedIndex.getAllKeys(TokenIndex.INDEX_ID, project);
        for (TokenIndexKey key : keys) {
          if (intersect(key.getLanguages(), languageIds)) {
            List<List<Token>> list = fileBasedIndex.getValues(TokenIndex.INDEX_ID, key, (GlobalSearchScope)scope);
            for (List<Token> tokens : list) {
              result.addAll(tokens);
            }
          }
        }
      }
    });
    return result;
  }

  private static Set<Language> getLanguages(CompiledPattern compiledPattern) {
    Set<Language> languages = new HashSet<Language>();
    NodeIterator iterator = compiledPattern.getNodes();
    while (iterator.hasNext()) {
      PsiElement element = iterator.current();
      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
      languages.add(profile != null ? profile.getLanguage(element) : element.getLanguage());
      iterator.advance();
    }
    iterator.reset();
    return languages;
  }

  private static Set<Language> getLanguages(PsiElement[] elements) {
    Set<Language> languages = new HashSet<Language>();
    for (PsiElement element : elements) {
      languages.add(element.getLanguage());
    }
    return languages;
  }

  private static List<PsiElement> getNodes(CompiledPattern compiledPattern) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    NodeIterator iterator = compiledPattern.getNodes();
    while (iterator.hasNext()) {
      PsiElement element = iterator.current();
      result.add(element);
      iterator.advance();
    }
    iterator.reset();
    return result;
  }

  public static boolean canProcess(CompiledPattern compiledPattern) {
    Set<Language> patternLanguages = getLanguages(compiledPattern);
    if (!checkLanguages(patternLanguages)) {
      return false;
    }
    return true;
  }

  private static boolean checkLanguages(Collection<Language> languages) {
    if (languages.size() != 1) {
      // multi languages is not yet support
      return false;
    }
    for (Language language : languages) {
      if (!TokenIndex.supports(language)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isApplicableResult(TokenBasedMatchResult result, boolean replacement) {
    return !(replacement && result.getTextRangeInFile() == null);
  }
}
