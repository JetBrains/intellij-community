package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GuiUtils;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.ruby.ruby.lang.TextUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runner.model.*;
import org.jetbrains.plugins.ruby.testing.testunit.runner.properties.RTestUnitConsoleProperties;
import org.jetbrains.plugins.ruby.utils.IdeaInternalUtil;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitResultsForm implements TestFrameworkRunningModel, LogConsoleManager, Disposable {
  @NonNls private static final String RTEST_UNIT_SPLITTER_PROPERTY = "RubyTestUnit.Splitter.Proportion";

  private JPanel myContentPane;
  private JSplitPane splitPane;
  private ColorProgressBar myProgressLine;
  private JLabel myStatusLabel;
  private JTabbedPane myTabbedPane;
  private JPanel toolbarPanel; //TODO[romeo] add toolbar
  private RTestUnitTestTreeView tree;

  private final TestsProgressAnimator myAnimator; 
  private final SplitterProportionsData splitterProportions = new SplitterProportionsDataImpl();
  //private final SplitterProportionsData splitterProportions = PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();

  private final RTestUnitTestProxy myTestsRootNode;
  private final RTestUnitTreeBuilder myTreeBuilder;
  private final RTestUnitConsoleProperties myConsoleProperties;
  private final List<ModelListener> myListeners = new ArrayList<ModelListener>();

  // Manages console tabs for runConfigurations's log files
  private final LogFilesManager myLogFilesManager;

  // Additional tabs of LogFilesManager
  private Map<AdditionalTabComponent, Integer> myAdditionalComponents = new HashMap<AdditionalTabComponent, Integer>();

  private final Project myProject;
  private ProcessHandler myRunProcess;

  // Run configuration for Test::Unit
  private final RTestsRunConfiguration myRunConfiguration;

  public RTestUnitResultsForm(final RTestsRunConfiguration runConfiguration,
                              final RTestUnitConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
    myRunConfiguration = runConfiguration;

    final Project project = runConfiguration.getProject();
    myProject = project;
    myLogFilesManager = new LogFilesManager(project, this);

    myTestsRootNode = new RTestUnitTestProxy(TextUtil.EMPTY_STRING);

    tree.attachToModel(this);
    final RTestUnitTreeStructure structure = new RTestUnitTreeStructure(project, myTestsRootNode);
    myTreeBuilder = new RTestUnitTreeBuilder(tree, structure);
    myAnimator = new MyAnimator(myTreeBuilder);

    splitterProportions.externalizeFromDimensionService(RTEST_UNIT_SPLITTER_PROPERTY);
        final Container container = splitPane.getParent();
        GuiUtils.replaceJSplitPaneWithIDEASplitter(splitPane);
        final Splitter splitter = (Splitter)container.getComponent(0);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            splitterProportions.restoreSplitterProportions(container);
            splitter.addPropertyChangeListener(new PropertyChangeListener() {
              public void propertyChange(final PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(Splitter.PROP_PROPORTION)) {
                  splitterProportions.saveSplitterProportions(container);
                  splitterProportions.externalizeToDimensionService(RTEST_UNIT_SPLITTER_PROPERTY);
                }
              }
            });
          }
        });
        Disposer.register(this, new Disposable() {
          public void dispose() {
            splitter.dispose();
          }
        });
  }

  public JComponent getContentPane() {
    return myContentPane;
  }

  public void addTab(@NotNull final String tabTitle, @NotNull final Component component) {
    myTabbedPane.addTab(tabTitle, component);
  }

  public void addLogConsole(@NotNull final String name, @NotNull final String path,
                            final long skippedContent){
    final LogConsole log = new LogConsole(myProject,
                                          new File(path),
                                          skippedContent, name, true) {
      public boolean isActive() {
        return myTabbedPane.getSelectedComponent() == this;
      }
    };

    if (myRunProcess != null) {
      log.attachStopLogConsoleTrackingListener(myRunProcess);
    }
    addAdditionalTabComponent(log, path);
    myTabbedPane.addChangeListener(log);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myTabbedPane.removeChangeListener(log);
      }
    });
  }


  public void addAdditionalTabComponent(@NotNull final AdditionalTabComponent tabComponent,
                                        @NotNull final String id) {
    myAdditionalComponents.put(tabComponent, myTabbedPane.getTabCount());
    myTabbedPane.addTab(tabComponent.getTabTitle(), null, tabComponent.getComponent(), tabComponent.getTooltip());
    Disposer.register(this, new Disposable() {
      public void dispose() {
        removeAdditionalTabComponent(tabComponent);
      }
    });
  }

  public void attachStopLogConsoleTrackingListeners(@NotNull final ProcessHandler process) {
    myRunProcess = process;
    for (AdditionalTabComponent component: myAdditionalComponents.keySet()) {
      if (component instanceof LogConsole){
        ((LogConsole)component).attachStopLogConsoleTrackingListener(process);
      }
    }
  }


  public void initLogConsole() {
    myLogFilesManager.registerFileMatcher(myRunConfiguration);
    myLogFilesManager.initLogConsoles(myRunConfiguration, myRunProcess);
  }

  public void removeLogConsole(@NotNull final String path) {
    LogConsole componentToRemove = null;
    for (AdditionalTabComponent tabComponent : myAdditionalComponents.keySet()) {
      if (tabComponent instanceof LogConsole) {
        final LogConsole console = (LogConsole)tabComponent;
        if (Comparing.strEqual(console.getPath(), path)) {
          componentToRemove = console;
          break;
        }
      }
    }
    if (componentToRemove != null) {
      myTabbedPane.removeChangeListener(componentToRemove);
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    myTabbedPane.removeTabAt(myAdditionalComponents.get(component).intValue());
    myAdditionalComponents.remove(component);
    component.dispose();
  }

  public void startTesting() {
    // Status line
    myProgressLine.setColor(ColorProgressBar.GREEN);

    // Tests tree
    tree.getSelectionModel().setSelectionPath(new TreePath(myTreeBuilder.getNodeForElement(myTestsRootNode)));
  }

  public void finishTesting() {
    myAnimator.stopMovie();

    //TODO[romeo] selection
    //if (RTestUnitConsoleProperties.SELECT_FIRST_DEFECT.value(consoleProperties)) {
    //    selectTest(myTestsRootNode.getFirstDefect());
    //} else {
        tree.getSelectionModel().setSelectionPath(new TreePath(myTreeBuilder.getNodeForElement(myTestsRootNode)));
    //}
    tree.repaint();

    LvcsHelper.addLabel(this);
  }

  public void addTestNode(final RTestUnitTestProxy testProxy,
                         final int testsTotal, final int testsCurrentCount) {
    // update progress if total is set
    myProgressLine.setFraction(testsTotal != 0 ? (double)testsCurrentCount / testsTotal : 0);

    // Tree
    //TODO[romeo] suite instad of root node
    myTreeBuilder.addItem(myTestsRootNode, testProxy);
    myTreeBuilder.repaintWithParents(testProxy);

    IdeaInternalUtil.runInEventDispatchThread(new Runnable() {
      public void run() {
        myAnimator.setCurrentTestCase(testProxy);
      }
    }, ModalityState.NON_MODAL);
  }

  public RTestUnitTestProxy getTestsRootNode() {
    return myTestsRootNode;
  }

  public void updateStatusLabel(final long startTime, final long endTime,
                                  final int testsTotal, final int testsCount,
                                  final Set<AbstractTestProxy> failedTestsSet) {

    final int failuresCount = failedTestsSet.size();

    if (failuresCount > 0) {
      myProgressLine.setColor(ColorProgressBar.RED);
    }
    myStatusLabel.setText(TestsPresentationUtil.getProgressStatus_Text(startTime, endTime,
                                                                       testsTotal, testsCount,
                                                                       failuresCount));
  }

  public TestConsoleProperties getProperties() {
    return myConsoleProperties;
  }

  public void setFilter(final Filter filter) {
    //TODO[romeo] implement
    //getTreeStructure().setFilter(filter);
    //myTreeBuilder.updateFromRoot();
  }
  //
  //public TestTreeStructure getTreeStructure() {
  //    return (TestTreeStructure) myTreeBuilder.getTreeStructure();
  //}


  public void addListener(final ModelListener l) {
    myListeners.add(l);
  }

  public boolean isRunning() {
    return getRoot().isInProgress();
  }

  public TestTreeView getTreeView() {
    return tree;
  }

  public boolean hasTestSuites() {
    return getRoot().getChildren().size() > 0;
  }

  @NotNull
  public AbstractTestProxy getRoot() {
    return myTestsRootNode;
  }

  public void selectAndNotify(final AbstractTestProxy testProxy) {
    //TODO[romeo] implement
  }

  public void dispose() {
    for (ModelListener listener : myListeners) {
      listener.onDispose();
    }
    myAnimator.dispose();
    myTreeBuilder.dispose();
    myLogFilesManager.unregisterFileMatcher();
  }



  private static class MyAnimator extends TestsProgressAnimator {
    public MyAnimator(final AbstractTestTreeBuilder builder) {
      init(builder);
    }
  }
}