package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.*;

import javax.swing.Timer;
import java.util.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

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
  private static CompiledPattern lastPattern;
  private static MatchOptions lastOptions;

  protected MatcherImpl(Project _project) {
    project = _project;
    matchContext = new MatchContext();
    matchContext.setMatcher(visitor);
  }

  public static void validate(Project project, MatchOptions _options) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    synchronized(MatcherImpl.class) {
      lastPattern =  PatternCompiler.compilePattern(project,_options);
      lastOptions = _options;
    }
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @throws com.intellij.structuralsearch.MalformedPatternException
   * @throws com.intellij.structuralsearch.UnsupportedPatternException
   */
  protected void findMatches(MatchResultSink sink,MatchOptions _options) throws MalformedPatternException, UnsupportedPatternException
  {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    matchContext.clear();
    matchContext.setSink(
      new MatchConstraintsSink(
        sink,
        _options.getMaxMatchesCount(),
        _options.isDistinct(),
        _options.isCaseSensitiveMatch()
      )
    );
    matchContext.setOptions(_options);
    matchContext.setMatcher(visitor);

    CompiledPattern compiledPattern = null;

    synchronized(getClass()) {
      if (_options==lastOptions) {
        compiledPattern = lastPattern;
      }
      lastOptions = null;
      lastPattern = null;
    }

    if (compiledPattern==null) {
      compiledPattern =  PatternCompiler.compilePattern(project,_options);
    }

    if (compiledPattern!=null) {
      matchContext.setPattern(compiledPattern);
      matchContext.getSink().setMatchingProcess( scheduler );
      scheduler.init();
      progress = matchContext.getSink().getProgressIndicator();
      visitor.setMatchContext(matchContext);

      if(isTesting) {
        // testing mode;
        final PsiElement[] elements = ((LocalSearchScope)_options.getScope()).getScope();

        for (int i = 0; i < elements.length; i++) {
          match( elements[i] );
        }

        matchContext.getSink().matchingFinished();
        return;
      }

      SearchScope searchScope = compiledPattern.getScope();
      if (searchScope==null) searchScope = _options.getScope();
      if (searchScope instanceof GlobalSearchScope) {
        final GlobalSearchScope scope = (GlobalSearchScope)searchScope;
        ContentIterator ci = new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            if (!scope.contains(fileOrDir)) return true;

            if (!fileOrDir.isDirectory()) {
              final PsiFile file = PsiManager.getInstance(project).findFile(fileOrDir);

              if (file instanceof PsiJavaFile ) {
                ++totalFilesToScan;
                scheduler.addOneTask(
                  new MatchOneFile(file)
                );
              }
            }
            return true;
          }
        };

        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        projectFileIndex.iterateContent(ci);
        /* @ todo factor out handlers and scheduling system, etc*/
      } else {
        final PsiElement[] elementsToScan = ((LocalSearchScope)searchScope).getScope();
        totalFilesToScan = elementsToScan.length;

        for(int i=0;i<elementsToScan.length;++i) {
          scheduler.addOneTask(new MatchOneFile(elementsToScan[i]));
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
      PsiElement[] elements = MatcherImplUtil.createTreeFromText(source, filePattern, options.getFileType(), project);

      options.setSearchPattern(pattern);
      options.setScope( new LocalSearchScope(elements) );
      testFindMatches(sink,options);
    } catch (IncorrectOperationException e) {
      throw new MalformedPatternException();
    }

    return sink.getMatches();
  }

  // Class that performs queuing the tasks to be performed in AWT thread
  private class TaskScheduler implements ActionListener, MatchingProcess {
    private LinkedList<Runnable> tasks = new LinkedList<Runnable>();
    private boolean ended;
    private Runnable taskQueueEndAction;

    private static final int WAIT_TIME = 1;
    private Timer timer = new Timer(WAIT_TIME,this);

    private boolean suspended;
    private LinkedList<Runnable> tempList = new LinkedList<Runnable>();

    public LinkedList<Runnable> getTempList() {
      return tempList;
    }

    public void stop() {
      clearSchedule();
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

    void setTaskQueueEndAction(Runnable _taskQueueEndAction) {
      taskQueueEndAction = _taskQueueEndAction;
    }
    Runnable getTaskQueueEndAction () {
      return taskQueueEndAction;
    }

    void addOneTask(Runnable runnable) {
      tasks.add(runnable);
    }

    void addNestedTasks(List<Runnable> list) {
      tasks.addAll(0,list);
    }

    private void executeNext() {
      timer.start();
    }

    void init() {
      ended = false;
      suspended = false;
      PsiManager.getInstance(project).startBatchFilesProcessingMode();
    }

    private void clearSchedule() {
      if (!ended) {
        if (tasks.size()!=0) tasks.clear();
        ended=true;
        taskQueueEndAction.run();

        PsiManager.getInstance(project).finishBatchFilesProcessingMode();
      }
    }

    public void actionPerformed(ActionEvent event) {
      timer.stop();

      if (tasks.size() == 0) {
        if (!ended) clearSchedule();
        return;
      }

      Runnable task = ((Runnable)tasks.removeFirst());
      task.run();
      if (!suspended) executeNext();
    }

    public void addEndTasks(LinkedList<Runnable> tempList) {
      tasks.addAll(tempList);
    }
  };

  class ScanSelectedFiles implements Runnable {
    private LocalSearchScope scope;

    ScanSelectedFiles(LocalSearchScope _scope) {
      scope = _scope;
    }

    public void run() {
      final LinkedList<Runnable> tempList = scheduler.getTempList();
      final PsiElement[] elementsToScan = scope.getScope();
      totalFilesToScan = elementsToScan.length;

      for(int i=0;i<elementsToScan.length;++i) {
        tempList.add(new MatchOneFile(elementsToScan[i]));
      }
      scheduler.addNestedTasks(tempList);
      tempList.clear();
    }
  }

  class MatchOneFile implements Runnable {
    private PsiElement file;

    MatchOneFile(PsiElement _file) {
      file = _file;
    }

    public void run() {
      final PsiFile psiFile = file.getContainingFile();

      if (psiFile!=null) {
        final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        manager.commitDocument( manager.getDocument( psiFile ) );
      }
      
      if (file instanceof PsiFile)
        matchContext.getSink().processFile((PsiFile)file);

      if (progress!=null) {
        progress.setFraction((double)scannedFilesCount/totalFilesToScan);
      }

      ++scannedFilesCount;
      if (file instanceof PsiIdentifier) {
        // Searching in previous results
        file = file.getParent();
      }
      match(file);
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
