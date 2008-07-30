package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTreeBuilder;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTreeStructure;
import org.jetbrains.plugins.ruby.utils.IdeaInternalUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitResultsForm implements TestFrameworkRunningModel, LogConsoleManager, TestResultsViewer {
  @NonNls private static final String RTEST_UNIT_SPLITTER_PROPERTY = "RubyTestUnit.Splitter.Proportion";

  private JPanel myContentPane;
  private JSplitPane splitPane;
  private ColorProgressBar myProgressLine;
  private JLabel myStatusLabel;
  private JTabbedPane myTabbedPane;
  private JPanel toolbarPanel;
  private RTestUnitTestTreeView tree;

  private final TestsProgressAnimator myAnimator;
  private final RTestUnitToolbarPanel myToolbar;

  /**
   * Fake parent suite for all tests and suites
   */
  private final RTestUnitTestProxy myTestsRootNode;
  private final RTestUnitTreeBuilder myTreeBuilder;
  private final TestConsoleProperties myConsoleProperties;
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
                              final TestConsoleProperties consoleProperties,
                              final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationSettings) {
    myConsoleProperties = consoleProperties;
    myRunConfiguration = runConfiguration;

    final Project project = runConfiguration.getProject();
    myProject = project;

    myLogFilesManager = new LogFilesManager(project, this);

    //Create tests common suite root
    //noinspection HardCodedStringLiteral
    myTestsRootNode = new RTestUnitTestProxy("[root]", true);

    // Setup tests tree
    tree.setLargeModel(true);
    tree.attachToModel(this);
    final RTestUnitTreeStructure structure = new RTestUnitTreeStructure(project, myTestsRootNode);
    myTreeBuilder = new RTestUnitTreeBuilder(tree, structure);
    myAnimator = new MyAnimator(myTreeBuilder);

    myToolbar = initToolbarPanel(consoleProperties, runnerSettings, configurationSettings);

    makeSplitterSettingsExternalizable(splitPane);
  }

  public void addTestsTreeSelectionListener(final TreeSelectionListener listener) {
    tree.getSelectionModel().addTreeSelectionListener(listener);
  }

  public void attachToProcess(final ProcessHandler processHandler) {
    myRunProcess = processHandler;
    
    //attachStopLogConsoleTrackingListener
    for (AdditionalTabComponent component: myAdditionalComponents.keySet()) {
      if (component instanceof LogConsole){
        ((LogConsole)component).attachStopLogConsoleTrackingListener(null);
      }
    }
  }

  public JComponent getContentPane() {
    return myContentPane;
  }

  public void addTab(@NotNull final String tabTitle, @NotNull final Component component) {
    myTabbedPane.addTab(tabTitle, component);
  }

  public void addTestsProxySelectionListener(final TestProxyTreeSelectionListener listener) {
     addTestsTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        final PrintableTestProxy selectedProxy =(PrintableTestProxy)getTreeView().getSelectedTest();
        listener.onSelected(selectedProxy);
      }
    });
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


  public void initLogFilesManager() {
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
    Disposer.dispose(component);
  }

  /**
   * Returns root node, fake parent suite for all tests and suites
   * @return
   */
  public void onStartTesting() {
    // Status line
    myProgressLine.setColor(ColorProgressBar.GREEN);

    // Tests tree
    selectWithoutNotify(myTestsRootNode);
  }

  public void onFinishTesting() {
    myAnimator.stopMovie();


    //TODO implement
    //if (RTestUnitConsoleProperties.SELECT_FIRST_DEFECT.value(consoleProperties)) {
    //    selectTest(myTestsRootNode.getFirstDefect());
    //} else {
        selectWithoutNotify(myTestsRootNode);
        //tree.getSelectionModel().setSelectionPath(new TreePath(myTreeBuilder.getNodeForElement(myTestsRootNode)));
    //}
    tree.repaint();

    LvcsHelper.addLabel(this);
  }

  /**
   * Adds test to tree and updates status line.
   * Test proxy should be initialized, proxy parent must be some suite (already added to tree)
   * If parent is null, then test will be added to tests root.
   *
   * @param testProxy Proxy
   * @param testsTotal Total tests
   * @param testsCurrentCount Current count
   */
  public void onTestStarted(final RTestUnitTestProxy testProxy,
                          final int testsTotal, final int testsCurrentCount) {
    // update progress if total is set
    myProgressLine.setFraction(testsTotal != 0 ? (double)testsCurrentCount / testsTotal : 0);

    _addTestOrSuite(testProxy);

    //TODO if Console.properites.TRACK_RUNNING_TEST.consoleProperties
    // select(test)
  }

  /**
   * Adds suite to tree
   * Suite proxy should be initialized, proxy parent must be some suite (already added to tree)
   * If parent is null, then suite will be added to tests root.
   *
   * @param newSuite Tests suite
   */
  public void onSuiteStarted(final RTestUnitTestProxy newSuite) {
    _addTestOrSuite(newSuite);
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
    // is usded by Test Runner actions, e.g. hide passed, etc
    final RTestUnitTreeStructure treeStructure = myTreeBuilder.getRTestUnitTreeStructure();
    treeStructure.setFilter(filter);
    myTreeBuilder.updateFromRoot();
  }

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

  public void selectAndNotify(@Nullable final AbstractTestProxy testProxy) {
    //is used by Test Runner actions - go to next failed, passed, first failed, etc

    selectWithoutNotify(testProxy);
  }

  private void selectWithoutNotify(final AbstractTestProxy testProxy) {
    if (testProxy == null) {
      return;
    }

    myTreeBuilder.select(testProxy, null);
  }

  public void dispose() {
    for (ModelListener listener : myListeners) {
      listener.onDispose();
    }
    myAnimator.dispose();
    myToolbar.dispose();
    Disposer.dispose(myTreeBuilder);
    myLogFilesManager.unregisterFileMatcher();
  }


  private void _addTestOrSuite(@NotNull final RTestUnitTestProxy newTestOrSuite) {

    final RTestUnitTestProxy parentSuite = newTestOrSuite.getParent();
    assert parentSuite != null;



    // Tree
    myTreeBuilder.updateTestsSubtree(parentSuite);
    myTreeBuilder.repaintWithParents(newTestOrSuite);

    IdeaInternalUtil.runInEventDispatchThread(new Runnable() {
      public void run() {
        myAnimator.setCurrentTestCase(newTestOrSuite);
      }
    }, ModalityState.NON_MODAL);
  }

  private RTestUnitToolbarPanel initToolbarPanel(final TestConsoleProperties consoleProperties,
                                                 final RunnerSettings runnerSettings,
                                                 final ConfigurationPerRunnerSettings configurationSettings) {
    toolbarPanel.setLayout(new BorderLayout());
    final RTestUnitToolbarPanel toolbar =
        new RTestUnitToolbarPanel(consoleProperties, runnerSettings, configurationSettings, this);
    toolbarPanel.add(toolbar);

    return toolbar;
  }

  private void makeSplitterSettingsExternalizable(final JSplitPane splitPane) {
    final SplitterProportionsData splitterProportions = new SplitterProportionsDataImpl();
    //PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();

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

  private static class MyAnimator extends TestsProgressAnimator {
    public MyAnimator(final AbstractTestTreeBuilder builder) {
      init(builder);
    }
  }
}