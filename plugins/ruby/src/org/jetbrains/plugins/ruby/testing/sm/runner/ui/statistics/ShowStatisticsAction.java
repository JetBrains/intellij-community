package org.jetbrains.plugins.ruby.testing.sm.runner.ui.statistics;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.SMTRunnerTestTreeView;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.TestResultsViewer;

import java.awt.*;

/**
 * @author Roman Chernyatchik
 */
public class ShowStatisticsAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    //To change body of implemented methods use File | Settings | File Templates.
    final SMTRunnerTestTreeView sender = (SMTRunnerTestTreeView)e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    assert sender != null;

    final TestResultsViewer resultsViewer = sender.getResultsViewer();
    assert resultsViewer != null;

    //final SMTestProxy test = (SMTestProxy)getSelectedTestProxy(e);

    resultsViewer.showStatisticsForSelectedProxy();
  }

  @Override
  public void update(final AnActionEvent e) {
    final Component sender = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);

    final Presentation presentation = getTemplatePresentation();
    // visible onle in SMTRunnerTestTreeView 
    presentation.setVisible(sender instanceof SMTRunnerTestTreeView);
    // enabled if some proxy is selected
    presentation.setEnabled(getSelectedTestProxy(e) != null);
  }

  @Nullable
  private Object getSelectedTestProxy(final AnActionEvent e) {
    return e.getDataContext().getData (AbstractTestProxy.DATA_CONSTANT);
  }
}
