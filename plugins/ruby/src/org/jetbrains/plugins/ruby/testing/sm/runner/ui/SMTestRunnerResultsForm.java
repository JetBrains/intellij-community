package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.support.UIUtil;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTRunnerEventsListener;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTRunnerTreeBuilder;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTRunnerTreeStructure;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;
import org.jetbrains.plugins.ruby.utils.IdeaInternalUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: Roman Chernyatchik
 */
public class SMTestRunnerResultsForm implements TestFrameworkRunningModel, LogConsoleManager, TestResultsViewer,
                                                SMTRunnerEventsListener {
  @NonNls private static final String SM_RUNNER_SPLITTER_PROPERTY = "SMTestRunner.Splitter.Proportion";

  private JPanel myContentPane;
  private JSplitPane splitPane;
  private ColorProgressBar myProgressLine;
  private JLabel myStatusLabel;
  private JPanel toolbarPanel;
  private SMTRunnerTestTreeView myTreeView;
  private JPanel myTabbedPaneConatiner;

  private final TestsProgressAnimator myAnimator;
  private final SMTRunnerToolbarPanel myToolbar;
  private JBTabs myTabs;

  /**
   * Fake parent suite for all tests and suites
   */
  private final SMTestProxy myTestsRootNode;
  private final SMTRunnerTreeBuilder myTreeBuilder;
  private final TestConsoleProperties myConsoleProperties;

  private final List<ModelListener> myListeners = new ArrayList<ModelListener>();
  private Map<LogConsole, TabsListener> myLogToListenerMap = new HashMap<LogConsole, TabsListener>();
  private final List<EventsListener> myEventListeners = new ArrayList<EventsListener>();
  private final List<TestProxySelectionChangedListener> myTestsTreeSelectionListeners = new ArrayList<TestProxySelectionChangedListener>();

  private TestProxySelectionChangedListener myShowStatisticForProxyHandler;

  // Manages console tabs for runConfigurations's log files
  private final LogFilesManager myLogFilesManager;

  // Additional tabs of LogFilesManager
  private Map<AdditionalTabComponent, Integer> myAdditionalComponents = new HashMap<AdditionalTabComponent, Integer>();

  private final Project myProject;
  private ProcessHandler myRunProcess;

  // Run configuration for Test::Unit
  private final RunConfigurationBase myRunConfiguration;

  private int myTestsCurrentCount;
  private int myTestsTotal;
  private int myTestsFailuresCount;
  private long myStartTime;
  private long myEndTime;


  public SMTestRunnerResultsForm(final RunConfigurationBase runConfiguration,
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
    myTestsRootNode = new SMTestProxy("[root]", true);

    //Adds tabs view component
    myTabs = new JBTabsImpl(project,
                            ActionManager.getInstance(),
                            IdeFocusManager.getInstance(myProject),
                            this);
    //TODO: popup menu
    //myTabs.setPopupGroup(, ViewContext.CELL_POPUP_PLACE);
    myTabbedPaneConatiner.setLayout(new BorderLayout());
    myTabbedPaneConatiner.add(myTabs.getComponent());

    // Setup tests tree
    myTreeView.setLargeModel(true);
    myTreeView.attachToModel(this);
    myTreeView.setTestResultsViewer(this);

    final SMTRunnerTreeStructure structure = new SMTRunnerTreeStructure(project, myTestsRootNode);
    myTreeBuilder = new SMTRunnerTreeBuilder(myTreeView, structure);
    myAnimator = new MyAnimator(myTreeBuilder);

    myToolbar = initToolbarPanel(consoleProperties, runnerSettings, configurationSettings);

    makeSplitterSettingsExternalizable(splitPane);

    // Fire selection changed and move focus on SHIFT+ENTER
    final KeyStroke shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
    UIUtil.registerAsAction(shiftEnterKey, "show-statistics-for-test-proxy",
                            new Runnable() {
                              public void run() {
                                showStatisticsForSelectedProxy();
                              }
                            },
                            myTreeView);
  }

  public void addTestsTreeSelectionListener(final TreeSelectionListener listener) {
    myTreeView.getSelectionModel().addTreeSelectionListener(listener);
  }

  /**
   * Is used for navigation from tree view to other UI components
   * @param handler
   */
  public void setShowStatisticForProxyHandler(final TestProxySelectionChangedListener handler) {
    myShowStatisticForProxyHandler = handler;
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

  public void addTab(@NotNull final String tabTitle, @Nullable final String tooltip,
                     @Nullable final Icon icon,
                     @NotNull final JComponent component) {
    final TabInfo tabInfo = new TabInfo(component);
    tabInfo.setTooltipText(tooltip);
    tabInfo.setText(tabTitle);
    tabInfo.setIcon(icon);

    myTabs.addTab(tabInfo);
  }

  public void addTestsProxySelectionListener(final TestProxyTreeSelectionListener listener) {
     addTestsTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        //We should fire event only if it was generated by this component,
        //e.g. it is focused. Otherwise it is side effect of selecing proxy in
        //try by other component
        //if (myTreeView.isFocusOwner()) {
          final PrintableTestProxy selectedProxy = (PrintableTestProxy)getTreeView().getSelectedTest();
          listener.onSelected(selectedProxy);
        //}
      }
    });
  }

  public void addTestTreeSelectionRequestedListener(final TestProxySelectionChangedListener l)  {
    myTestsTreeSelectionListeners.add(l);
  }

  public void addLogConsole(@NotNull final String name, @NotNull final String path,
                            final long skippedContent){
    final LogConsole log = new LogConsole(myProject,
                                          new File(path),
                                          skippedContent, name, true) {
      public boolean isActive() {
        final TabInfo info = myTabs.getSelectedInfo();
        return info != null && info.getComponent() == this;
      }
    };

    if (myRunProcess != null) {
      log.attachStopLogConsoleTrackingListener(myRunProcess);
    }
    addAdditionalTabComponent(log, path);
    final TabsListener listener = new TabsListener() {
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        log.stateChanged(new ChangeEvent(myTabs));
      }
    };
    myTabs.addListener(listener);
    myLogToListenerMap.put(log, listener);
  }


  public void addAdditionalTabComponent(@NotNull final AdditionalTabComponent tabComponent,
                                        @NotNull final String id) {
    myAdditionalComponents.put(tabComponent, myTabs.getTabCount());
    addTab(tabComponent.getTabTitle(), tabComponent.getTooltip(), null, tabComponent.getComponent());
    Disposer.register(this, new Disposable() {
      public void dispose() {
        removeAdditionalTabComponent(tabComponent);
      }
    });
  }

  public JBTabs getTabs() {
    return myTabs;
  }

  public void initLogFilesManager() {
    myLogFilesManager.registerFileMatcher(myRunConfiguration);
    myLogFilesManager.initLogConsoles(myRunConfiguration, myRunProcess);
  }

  public void removeLogConsole(@NotNull final String path) {
    LogConsole logConsoleToRemove = null;
    for (AdditionalTabComponent tabComponent : myAdditionalComponents.keySet()) {
      if (tabComponent instanceof LogConsole) {
        final LogConsole console = (LogConsole)tabComponent;
        if (Comparing.strEqual(console.getPath(), path)) {
          logConsoleToRemove = console;
          break;
        }
      }
    }
    if (logConsoleToRemove != null) {
      myTabs.removeListener(myLogToListenerMap.get(logConsoleToRemove));
      myLogToListenerMap.remove(logConsoleToRemove);

      removeAdditionalTabComponent(logConsoleToRemove);
    }
  }

  public void removeAdditionalTabComponent(final AdditionalTabComponent component) {

    TabInfo tabInfoFoComponent = null;
    final List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo tab : tabs) {
      if (tab.getComponent() == component) {
        tabInfoFoComponent = tab;
        break;
      }
    }
    myTabs.removeTab(tabInfoFoComponent);
    myAdditionalComponents.remove(component);
    Disposer.dispose(component);
  }

  /**
   * Returns root node, fake parent suite for all tests and suites
   * @return
   */
  public void onTestingStarted() {
    myAnimator.setCurrentTestCase(myTestsRootNode);
    
    // Status line
    myProgressLine.setColor(ColorProgressBar.GREEN);

    // Tests tree
    selectAndNotify(myTestsRootNode);

    myStartTime = System.currentTimeMillis();
    updateStatusLabel();
  }

  public void onTestingFinished() {
    myEndTime = System.currentTimeMillis();
    updateStatusLabel();


    myAnimator.stopMovie();
    myTreeBuilder.updateFromRoot();

    LvcsHelper.addLabel(this);

    fireOnTestingFinished();
  }

  public void onTestsCountInSuite(final int count) {
    //This is for beter support groups of TestSuites
    //Each group notifies about it's size
    myTestsTotal += count;
  }

  /**
   * Adds test to tree and updates status line.
   * Test proxy should be initialized, proxy parent must be some suite (already added to tree)
   *
   * @param testProxy Proxy
   */
  public void onTestStarted(@NotNull final SMTestProxy testProxy) {
    // Counters
    myTestsCurrentCount++;
    // fix total count if it is corrupted
    if (myTestsCurrentCount > myTestsTotal) {
      myTestsTotal = myTestsCurrentCount;
    }

    // update progress if total is set
    myProgressLine.setFraction(myTestsTotal != 0 ? (double)myTestsCurrentCount / myTestsTotal : 0);

    _addTestOrSuite(testProxy);


    updateStatusLabel();

    fireOnTestNodeAdded(testProxy);
  }

  public void onTestFailed(@NotNull final SMTestProxy test) {
    myTestsFailuresCount++;
    updateStatusLabel();
  }

  public void onTestIgnored(@NotNull final SMTestProxy test) {
    //Do nothing
  }

  /**
   * Adds suite to tree
   * Suite proxy should be initialized, proxy parent must be some suite (already added to tree)
   * If parent is null, then suite will be added to tests root.
   *
   * @param newSuite Tests suite
   */
  public void onSuiteStarted(@NotNull final SMTestProxy newSuite) {
    _addTestOrSuite(newSuite);
  }

  public void onTestFinished(@NotNull final SMTestProxy test) {
    //Do nothing
  }

  public void onSuiteFinished(@NotNull final SMTestProxy suite) {
    //Do nothing
  }

  public SMTestProxy getTestsRootNode() {
    return myTestsRootNode;
  }

  public TestConsoleProperties getProperties() {
    return myConsoleProperties;
  }

  public void setFilter(final Filter filter) {
    // is usded by Test Runner actions, e.g. hide passed, etc
    final SMTRunnerTreeStructure treeStructure = myTreeBuilder.getRTestUnitTreeStructure();
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
    return myTreeView;
  }

  public boolean hasTestSuites() {
    return getRoot().getChildren().size() > 0;
  }

  @NotNull
  public AbstractTestProxy getRoot() {
    return myTestsRootNode;
  }

  /**
   * Will select proxy in Event Dispatch Thread. Invocation of this
   * method may be not in event dispatch thread
   * @param testProxy Test or suite
   */
  public void selectAndNotify(@Nullable final AbstractTestProxy testProxy) {
    selectWithoutNotify(testProxy);

    // Is used by Statistic tab to differ use selection in tree
    // from programmatical selection from test runner events
    fireOnSelectedRequest((SMTestProxy)testProxy);
  }

  public void addEventsListener(final EventsListener listener) {
    myEventListeners.add(listener);
  }

  public void dispose() {
    for (ModelListener listener : myListeners) {
      listener.onDispose();
    }
    myShowStatisticForProxyHandler = null;
    myEventListeners.clear();
    myTestsTreeSelectionListeners.clear();

    myAnimator.dispose();
    myToolbar.dispose();
    Disposer.dispose(myTreeBuilder);
    myLogFilesManager.unregisterFileMatcher();
  }

  public void showStatisticsForSelectedProxy() {
    final AbstractTestProxy selectedProxy = myTreeView.getSelectedTest();
    if (selectedProxy instanceof SMTestProxy && myShowStatisticForProxyHandler != null) {
      myShowStatisticForProxyHandler.onChangeSelection((SMTestProxy)selectedProxy, this, true);
    }
  }

  protected int getTestsCurrentCount() {
    return myTestsCurrentCount;
  }

  protected int getTestsTotal() {
    return myTestsTotal;
  }

  protected long getStartTime() {
    return myStartTime;
  }

  protected long getEndTime() {
    return myEndTime;
  }

  private void _addTestOrSuite(@NotNull final SMTestProxy newTestOrSuite) {

    final SMTestProxy parentSuite = newTestOrSuite.getParent();
    assert parentSuite != null;

    // Tree
    myTreeBuilder.updateTestsSubtree(parentSuite);
    myTreeBuilder.repaintWithParents(newTestOrSuite);

    myAnimator.setCurrentTestCase(newTestOrSuite);
  }

  private SMTRunnerToolbarPanel initToolbarPanel(final TestConsoleProperties consoleProperties,
                                                 final RunnerSettings runnerSettings,
                                                 final ConfigurationPerRunnerSettings configurationSettings) {
    toolbarPanel.setLayout(new BorderLayout());
    final SMTRunnerToolbarPanel toolbar =
        new SMTRunnerToolbarPanel(consoleProperties, runnerSettings, configurationSettings, this);
    toolbarPanel.add(toolbar);

    return toolbar;
  }

  private void fireOnTestNodeAdded(final SMTestProxy test) {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestNodeAdded(this, test);
    }
  }

  private void fireOnTestingFinished() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingFinished(this);
    }
  }

  private void fireOnSelectedRequest(final SMTestProxy selectedTestProxy) {
    for (TestProxySelectionChangedListener selectionListener : myTestsTreeSelectionListeners) {
      selectionListener.onChangeSelection(selectedTestProxy, this, false);
    }
  }

  private void selectWithoutNotify(final AbstractTestProxy testProxy) {
    if (testProxy == null) {
      return;
    }

    IdeaInternalUtil.runInEventDispatchThread(new Runnable() {
      public void run() {
        //TODO remove manual update!
        myTreeBuilder.performUpdate();

        myTreeBuilder.select(testProxy, null);
      }
    }, ModalityState.NON_MODAL);
  }

  private void makeSplitterSettingsExternalizable(final JSplitPane splitPane) {
    final SplitterProportionsData splitterProportions = new SplitterProportionsDataImpl();
    //PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();

    splitterProportions.externalizeFromDimensionService(getSplitterPropertyName());
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
              splitterProportions.externalizeToDimensionService(getSplitterPropertyName());
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

  protected String getSplitterPropertyName() {
    return SM_RUNNER_SPLITTER_PROPERTY;
  }

  private void updateStatusLabel() {
    if (myTestsFailuresCount > 0) {
      myProgressLine.setColor(ColorProgressBar.RED);
    }
    myStatusLabel.setText(TestsPresentationUtil.getProgressStatus_Text(myStartTime, myEndTime,
                                                                       myTestsTotal, myTestsCurrentCount,
                                                                       myTestsFailuresCount));
  }

  /**
   * for java unit tests
   */
  public void performUpdate() {
    myTreeBuilder.performUpdate();
  }

  /**
   * On event change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   * @return Listener
   */
  public TestProxySelectionChangedListener createOnChangeSelectionListener() {
    return new TestProxySelectionChangedListener() {
      public void onChangeSelection(@Nullable final SMTestProxy selectedTestProxy, @NotNull final Object sender,
                                    final boolean requestFocus) {
        UIUtil.addToInvokeLater(new Runnable() {
          public void run() {
            selectWithoutNotify(selectedTestProxy);

            // Request focus if necessary
            if (requestFocus) {
              //myTreeView.requestFocusInWindow();
              IdeFocusManager.getInstance(myProject).requestFocus(myTreeView, true);
            }
          }
        });
      }
    };
  }


  private static class MyAnimator extends TestsProgressAnimator {
    public MyAnimator(final AbstractTestTreeBuilder builder) {
      init(builder);
    }
  }
}