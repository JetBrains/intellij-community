package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.MatchingProcess;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.ui.actions.DoSearchAction;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.Processor;

import javax.swing.*;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 15, 2004
 * Time: 4:49:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class SearchCommand {
  protected UsageViewContext context;
  private MatchingProcess process;
  protected Project project;

  public SearchCommand(Project _project, UsageViewContext _context) {
    project = _project;
    context = _context;
  }

  public void findUsages(final Processor<Usage> processor) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    final Runnable findUsagesRunnable2 = new Runnable() {
      public void run() {
        DoSearchAction.execute(
          project,
          new MatchResultSink() {
            int count;

            public void setMatchingProcess(MatchingProcess _process) {
              process = _process;
              findStarted();
            }

            public void processFile(PsiFile element) {
              progress.setText( "Looking in "+element.getVirtualFile().getPresentableName() );
            }

            public void matchingFinished() {
              findEnded();
              progress.setText( "Found " + count + " occurences" );
            }

            public ProgressIndicator getProgressIndicator() {
              return progress;
            }

            public void newMatch(MatchResult result) {
              UsageInfo info;

              if (MatchResult.MULTI_LINE_MATCH.equals(result.getName())) {
                int start = -1;
                int end = -1;
                PsiElement parent = result.getMatchRef().getElement().getParent();

                for(Iterator i=((MatchResultImpl)result).getMatches().iterator();i.hasNext();) {
                  PsiElement el = ((MatchResult)i.next()).getMatchRef().getElement();
                  if (start==-1 || start > el.getTextOffset()) {
                    start = el.getTextOffset();
                  }
                  final int newend = el.getTextOffset() + el.getTextLength();

                  if (newend > end) {
                    end = newend;
                  }
                }
                info = new UsageInfo(parent,start - parent.getTextOffset(),end - parent.getTextOffset());
              } else {
                PsiElement element = result.getMatch();
                info = new UsageInfo(
                  element,
                  result.getStart(),
                  result.getEnd()==-1?element.getTextLength() : result.getEnd()
                );
              }

              Usage usage = new UsageInfo2UsageAdapter(info);
              processor.process(usage);
              foundUsage(result, usage);
              ++count;
            }
          },
          context.getConfiguration()
        );
      }
    };


    SwingUtilities.invokeLater( findUsagesRunnable2 );

    synchronized(SearchCommand.this) {
      try {
        SearchCommand.this.wait(0);
      } catch(InterruptedException ex) {
      }
    }

    //Runnable endSearchRunnable = new Runnable() {
    //  public void run() {
    //    if (consumer.getCount()==0) {
    //      if (!progress.isCanceled()) {
    //        int option = Messages.showDialog(project,
    //                                   "No occurrences found in " + config.getMatchOptions().getScope().getDisplayName(),
    //                                   "Information",
    //                                   new String[] { "OK", "Edit &Query" },
    //                                   0,
    //                                   Messages.getInformationIcon());
    //
    //        if (option==1) {
    //          // editing options again
    //          UIUtil.invokeActionAnotherTime(config,mySearchContext);
    //        }
    //      }
    //    }
    //  }
    //};
    //
    //FindInProjectUtil.runProcessWithProgress(progress, findUsagesRunnable, endSearchRunnable, project);
  }

  public void stopAsyncSearch() {
    if (process!=null) process.stop();
  }

  protected void findStarted() {
    StructuralSearchPlugin.getInstance(project).setSearchInProgress(true);
  }

  protected void findEnded() {
    StructuralSearchPlugin.getInstance(project).setSearchInProgress(false);
    synchronized(this) {
      notify();
    }
  }

  protected void foundUsage(MatchResult result, Usage usage) {
  }
}
