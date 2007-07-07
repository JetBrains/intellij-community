package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.FileIndexImplUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.Language;
import com.intellij.lang.jsp.JspxFileViewProvider;

import java.util.*;
import java.lang.ref.SoftReference;

/**
 * This class makes program structure tree matching:
 */
public class MatcherImpl {
  // project being worked on
  private Project project;

  // context of matching
  private MatchContext matchContext;
  private boolean isTesting;

  // visitor to delegate the real work
  private MatchingVisitor visitor = new MatchingVisitor();
  private ProgressIndicator progress;
  private TaskScheduler scheduler = new TaskScheduler();

  private int totalFilesToScan;
  private int scannedFilesCount;

  static class LastMatchData {
    CompiledPattern lastPattern;
    MatchOptions lastOptions;
  }

  private static SoftReference<LastMatchData> lastMatchData;

  protected MatcherImpl(Project project) {
    this.project = project;
    matchContext = new MatchContext();
    matchContext.setMatcher(visitor);
  }

  public static void validate(Project project, MatchOptions options) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    CompiledPattern lastPattern;

    synchronized(MatcherImpl.class) {
      final LastMatchData data = new LastMatchData();
      lastPattern = data.lastPattern =  PatternCompiler.compilePattern(project,options);
      data.lastOptions = options;
      lastMatchData = new SoftReference<LastMatchData>(data);
    }

    class ValidatingVisitor extends PsiRecursiveElementVisitor {
      public void visitAnnotation(PsiAnnotation annotation) {
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

          } else {
            final String name = StringUtil.stripQuotesAroundValue(value.getText());
            checkModifier(name);
          }
        }
      }

      private void checkModifier(final String name) {
        if (!MatchOptions.INSTANCE_MODIFIER_NAME.equals(name) &&
            !MatchOptions.PACKAGE_LOCAL_MODIFIER_NAME.equals(name) &&
            Arrays.binarySearch(MatchingVisitor.MODIFIERS, name) < 0
           ) {
          throw new MalformedPatternException(SSRBundle.message("invalid.modifier.type",name));
        }
      }
    }

    ValidatingVisitor visitor = new ValidatingVisitor();
    final NodeIterator nodes = lastPattern.getNodes();
    while(nodes.hasNext()) {
      nodes.current().accept( visitor );
      nodes.advance();
    }
    nodes.reset();
  }

  public static class CompiledOptions {
    private final List<MatchContext> matchContexts;
    private final List<Configuration> myConfigurations;

    public CompiledOptions(final List<MatchContext> matchContexts, final List<Configuration> configurations) {
      this.matchContexts = matchContexts;
      myConfigurations = configurations;
    }

    public List<Configuration> getConfigurations() {
      return myConfigurations;
    }
  }

  public Collection<Pair<MatchResult, Configuration>> findMatchesInFile(CompiledOptions compiledOptions, PsiFile psiFile) {
    LocalSearchScope scope = new LocalSearchScope(psiFile);
    List<Pair<MatchResult, Configuration>> result = new ArrayList<Pair<MatchResult, Configuration>>();
    for (int i = 0; i < compiledOptions.matchContexts.size(); i++) {
      MatchContext context = compiledOptions.matchContexts.get(i);
      Configuration configuration = compiledOptions.myConfigurations.get(i);
      matchContext.clear();
      matchContext.setMatcher(visitor);
      MatchOptions options = context.getOptions();
      matchContext.setOptions(options);
      matchContext.setPattern(context.getPattern());
      visitor.setMatchContext(matchContext);
      CollectingMatchResultSink sink = new CollectingMatchResultSink();
      matchContext.setSink(
        new MatchConstraintsSink(
          sink,
          options.getMaxMatchesCount(),
          options.isDistinct(),
          options.isCaseSensitiveMatch()
        )
      );
      options.setScope(scope);
      match(psiFile);

      List<MatchResult> matches = sink.getMatches();
      for (MatchResult matchResult : matches) {
        result.add(Pair.create(matchResult, configuration));
      }
    }
    return result;
  }

  public CompiledOptions precompileOptions(List<Configuration> configurations) {
    List<MatchContext> contexts = new ArrayList<MatchContext>();
    for (Configuration configuration : configurations) {
      MatchContext matchContext = new MatchContext();
      matchContext.setMatcher(visitor);
      MatchOptions matchOptions = configuration.getMatchOptions();
      matchContext.setOptions(matchOptions);
      CompiledPattern compiledPattern = PatternCompiler.compilePattern(project, matchOptions);
      matchContext.setPattern(compiledPattern);
      contexts.add(matchContext);
    }
    return new CompiledOptions(contexts, configurations);
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @throws com.intellij.structuralsearch.MalformedPatternException
   * @throws com.intellij.structuralsearch.UnsupportedPatternException
   */
  protected void findMatches(MatchResultSink sink, final MatchOptions options) throws MalformedPatternException, UnsupportedPatternException
  {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    matchContext.clear();
    matchContext.setSink(
      new MatchConstraintsSink(
        sink,
        options.getMaxMatchesCount(),
        options.isDistinct(),
        options.isCaseSensitiveMatch()
      )
    );
    matchContext.setOptions(options);
    matchContext.setMatcher(visitor);

    CompiledPattern compiledPattern = null;

    synchronized(getClass()) {
      final LastMatchData data = lastMatchData != null ? lastMatchData.get():null;
      if (data != null && options == data.lastOptions) {
        compiledPattern = data.lastPattern;
      }
      lastMatchData = null;
    }

    if (compiledPattern==null) {
      compiledPattern =  PatternCompiler.compilePattern(project,options);
    }

    if (compiledPattern== null) {
      return;
    }
    matchContext.setPattern(compiledPattern);
    matchContext.getSink().setMatchingProcess( scheduler );
    scheduler.init();
    progress = matchContext.getSink().getProgressIndicator();
    visitor.setMatchContext(matchContext);

    if(isTesting) {
      // testing mode;
      final PsiElement[] elements = ((LocalSearchScope)options.getScope()).getScope();

      for (PsiElement element : elements) {
        match(element);
      }

      matchContext.getSink().matchingFinished();
      return;
    }

    final Language ourPatternLanguage = ((LanguageFileType)options.getFileType()).getLanguage();
    SearchScope searchScope = compiledPattern.getScope();
    boolean ourOptimizedScope = searchScope != null;
    if (!ourOptimizedScope) searchScope = options.getScope();

    if (searchScope instanceof GlobalSearchScope) {
      final GlobalSearchScope scope = (GlobalSearchScope)searchScope;
      final ContentIterator ci = new ContentIterator() {
        public boolean processFile(VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory()) {
            final PsiFile file = PsiManager.getInstance(project).findFile(fileOrDir);

            if ((options.getFileType() == StdFileTypes.JAVA && file instanceof PsiJavaFile) ||
                (options.getFileType() != StdFileTypes.JAVA && file instanceof XmlFile)
               ) {
              final FileViewProvider viewProvider = file.getViewProvider();

              for(Language lang: viewProvider.getPrimaryLanguages()) {
                if (lang != ourPatternLanguage) continue;
                ++totalFilesToScan;
                scheduler.addOneTask( new MatchOneFile(viewProvider.getPsi(lang)) );
              }
            }
          }
          return true;
        }
      };

      final ProjectRootManager instance = ProjectRootManager.getInstance(project);
      final ProjectFileIndex projectFileIndex = instance.getFileIndex();

      final VirtualFile[] rootFiles = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
        public VirtualFile[] compute() {
          // We search from content roots even for java files due to jsps
          final VirtualFile[] contentRoots = instance.getContentRoots();
          List<VirtualFile> result = new ArrayList<VirtualFile>(contentRoots.length);
          result.addAll(Arrays.asList(contentRoots));

          if (scope.isSearchInLibraries()) {
            for(VirtualFile file: instance.getRootFiles(ProjectRootType.SOURCE)) {
              if (projectFileIndex.isInLibrarySource(file)) {
                result.add(file);
              }
            }
          }
          return result.toArray(VirtualFile.EMPTY_ARRAY);
        }
      });

      HashSet<VirtualFile> visited = new HashSet<VirtualFile>(rootFiles.length);
      final VirtualFileFilter filter = new VirtualFileFilter() {
        public boolean accept(VirtualFile file) {
          return (file.isDirectory() && !FileTypeManager.getInstance().isFileIgnored(file.getName())) ||
                 scope.contains(file);
        }
      };

      for (final VirtualFile rootFile : rootFiles) {
        if (visited.contains(rootFile)) continue;

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            FileIndexImplUtil.iterateRecursively(
              rootFile,
              filter,
              ci
            );
          }
        });

        visited.add(rootFile);
      }
    }
    else {
      final PsiElement[] elementsToScan = ((LocalSearchScope)searchScope).getScope();
      if (elementsToScan == null) return;
      totalFilesToScan = elementsToScan.length;

      for (int i = 0; i < elementsToScan.length; ++i) {
        final PsiElement psiElement = elementsToScan[i];
        if (psiElement == null) continue;
        final Language language = psiElement.getLanguage();

        if (language != JspxFileViewProvider.JAVA_HOLDER_METHOD_TREE_LANGUAGE && language == ourPatternLanguage) { // prevent duplicated usages
          scheduler.addOneTask(new MatchOneFile(psiElement));
        }
        if (ourOptimizedScope) elementsToScan[i] = null; // to prevent long PsiElement reference
      }
    }

    if (scheduler.getTaskQueueEndAction()==null) {
      scheduler.setTaskQueueEndAction(
        new Runnable() {
          public void run() {
            matchContext.getSink().matchingFinished();
          }
        }
      );
    }

    scheduler.executeNext();
  }

  /**
     * Finds the matches of given pattern starting from given tree element.
     * @param sink match result destination
     * @throws MalformedPatternException
     * @throws UnsupportedPatternException
     */
    protected void testFindMatches(MatchResultSink sink, MatchOptions options)
      throws MalformedPatternException, UnsupportedPatternException {
      isTesting = true;
      try {
        findMatches(sink,options);
      } finally {
        isTesting = false;
      }
    }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param source string for search
   * @param pattern to be searched
   * @return list of matches found
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  protected List testFindMatches(String source,String pattern, MatchOptions options, boolean filePattern)
    throws MalformedPatternException, UnsupportedPatternException {

    CollectingMatchResultSink sink = new CollectingMatchResultSink();

    try {
      PsiElement[] elements = MatcherImplUtil.createTreeFromText(
        source,
        filePattern ? MatcherImplUtil.TreeContext.File : MatcherImplUtil.TreeContext.Block, 
        options.getFileType(),
        project
      );

      options.setSearchPattern(pattern);
      options.setScope( new LocalSearchScope(elements) );
      testFindMatches(sink,options);
    } catch (IncorrectOperationException e) {
      throw new MalformedPatternException();
    }

    return sink.getMatches();
  }

  private class TaskScheduler implements MatchingProcess {
    private LinkedList<Runnable> tasks = new LinkedList<Runnable>();
    private boolean ended;
    private Runnable taskQueueEndAction;

    private boolean suspended;
    private LinkedList<Runnable> tempList = new LinkedList<Runnable>();

    public LinkedList<Runnable> getTempList() {
      return tempList;
    }

    public void stop() {
      ended = true;
    }

    public void pause() {
      suspended = true;
    }

    public void resume() {
      if (!suspended) return;
      suspended = false;
      executeNext();
    }

    public boolean isSuspended() {
      return suspended;
    }

    public boolean isEnded() {
      return ended;
    }

    void setTaskQueueEndAction(Runnable taskQueueEndAction) {
      this.taskQueueEndAction = taskQueueEndAction;
    }
    Runnable getTaskQueueEndAction () {
      return taskQueueEndAction;
    }

    void addOneTask(Runnable runnable) {
      tasks.add(runnable);
    }

    private void executeNext() {
      while(!suspended && !ended) {
        if (tasks.isEmpty()) {
          ended = true;
          break;
        }

        Runnable task = tasks.removeFirst();
        task.run();
      }

      if (ended) clearSchedule();
    }

    private void init() {
      ended = false;
      suspended = false;
      PsiManager.getInstance(project).startBatchFilesProcessingMode();
    }

    private void clearSchedule() {
      if (tasks != null) {
        taskQueueEndAction.run();
        PsiManager.getInstance(project).finishBatchFilesProcessingMode();
        tasks = null;
      }
    }

    public void addEndTasks(LinkedList<Runnable> tempList) {
      tasks.addAll(tempList);
    }
  }

  private class MatchOneFile implements Runnable {
    private PsiElement file;

    MatchOneFile(PsiElement file) {
      this.file = file;
    }

    public void run() {
      final PsiFile psiFile = file.getContainingFile();

      if (psiFile!=null) {
        final Runnable action = new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
                manager.commitDocument(manager.getDocument(psiFile));
              }
            });
          }
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
          action.run();
        } else {
          ApplicationManager.getApplication().invokeAndWait(
            action,
            ModalityState.defaultModalityState()
          );
        }
      }

      if (file instanceof PsiFile) {
        matchContext.getSink().processFile((PsiFile)file);
      }

      if (progress!=null) {
        progress.setFraction((double)scannedFilesCount/totalFilesToScan);
      }

      ++scannedFilesCount;
      if (file instanceof PsiIdentifier) {
        // Searching in previous results
        file = file.getParent();
      }

      ApplicationManager.getApplication().runReadAction(
        new Runnable() {
          public void run() {
            match(file);
          }
        }
      );

      file = null;
    }
  }

  // Initiates the matching process for given element
  // @param element the current search tree element
  private void match(PsiElement element) {
    MatchingStrategy strategy = matchContext.getPattern().getStrategy();

    if (strategy.continueMatching(element)) {
      visitor.matchContext(new ArrayBackedNodeIterator(new PsiElement[] {element}));
      return;
    }
    for(PsiElement el=element.getFirstChild();el!=null;el=el.getNextSibling()) {
      match(el);
    }
  }
}
