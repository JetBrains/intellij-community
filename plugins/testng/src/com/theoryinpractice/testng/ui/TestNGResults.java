/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 6, 2005
 * Time: 10:49:05 PM
 */
package com.theoryinpractice.testng.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit2.ui.Formatters;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.table.TableView;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.*;
import org.jetbrains.annotations.NonNls;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestNGResults  implements TestFrameworkRunningModel, LogConsoleManager
{
    @NonNls private static final String TESTNG_SPLITTER_PROPERTY = "TestNG.Splitter.Proportion";

    private final SplitterProportionsData splitterProportions = PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();

    private Map<AdditionalTabComponent, Integer> myAdditionalComponents = new HashMap<AdditionalTabComponent, Integer>();

    private TableView resultsTable;
    private JPanel main;
    private ColorProgressBar progress;

    private TestNGResultsTableModel model;
    private TestNGTestTreeView tree;
    private JTabbedPane tabbedPane;

    private Project project;
    private int count;
    private int total;
    private int failed;
    private long start;
    private long end;
    private JLabel statusLabel;
    private TestTreeBuilder treeBuilder;
    private Animator animator;

    private Pattern packagePattern = Pattern.compile("(.*)\\.(.*)");
    private TreeRootNode rootNode;
    private TestNGConsoleProperties consoleProperties;
    private JPanel toolbarPanel;
    private JSplitPane splitPane;
    private static final String NO_PACKAGE = "No Package";
    private TestNGToolbarPanel toolbar;
    private final LogFilesManager myLogFilesManager;
    private TestNGResults.OpenSourceSelectionListener openSourceListener;
  private List<ModelListener> myListeners = new ArrayList<ModelListener>();
  private final TestNGConfiguration myConfiguration;
  private ProcessHandler myRunProcess;

  public TestNGResults(final TestNGConfiguration configuration, final TestNGConsoleView console, final RunnerSettings runnerSettings,
                         final ConfigurationPerRunnerSettings configurationSettings) {
        myConfiguration = configuration;
        this.project = myConfiguration.getProject();

        myLogFilesManager = new LogFilesManager(project, this);

        model = new TestNGResultsTableModel();
        consoleProperties = console.getConsoleProperties();
        resultsTable = new TableView(model);
        resultsTable.addMouseListener(new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2){
              final Object result = resultsTable.getSelectedObject();
              if (result instanceof TestResultMessage) {
                final String testClass = ((TestResultMessage)result).getTestClass();
                final PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), testClass);
                if (psiClass != null) {
                  final String method = ((TestResultMessage)result).getMethod();
                  if (method != null) {
                    final PsiMethod[] psiMethods = psiClass.findMethodsByName(method, false);
                    for (PsiMethod psiMethod : psiMethods) {
                      psiMethod.navigate(true);
                      return;
                    }
                  }
                  psiClass.navigate(true);
                }
              }
            }
          }
        });
        rootNode = new TreeRootNode();
        final TestTreeStructure structure = new TestTreeStructure(project, rootNode);
        tree.attachToModel(this);
        treeBuilder = new TestTreeBuilder(tree, structure);
        toolbarPanel.setLayout(new BorderLayout());
        toolbar = new TestNGToolbarPanel(console.getConsoleProperties(), this, runnerSettings, configurationSettings);
        toolbarPanel.add(toolbar);
        animator = new Animator(treeBuilder);
        openSourceListener = new OpenSourceSelectionListener(structure, console);
        tree.getSelectionModel().addTreeSelectionListener(openSourceListener);
        progress.setColor(ColorProgressBar.GREEN);
        splitterProportions.externalizeFromDimensionService(TESTNG_SPLITTER_PROPERTY);
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
                  splitterProportions.externalizeToDimensionService(TESTNG_SPLITTER_PROPERTY);
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

  public void initLogConsole() {
    myLogFilesManager.registerFileMatcher(myConfiguration);
    myLogFilesManager.initLogConsoles(myConfiguration, myRunProcess);
  }

  private void updateLabel(JLabel label) {
        StringBuffer sb = new StringBuffer();
        if (end == 0) {
            sb.append("Running: ");
        } else {
            sb.append("Done: ");
        }
        sb.append(count).append(" of ").append(total);
        if (failed > 0)
            sb.append("   Failed: ").append(failed).append(' ');
        if (end != 0) {
            sb.append(" (").append(Formatters.printTime(end - start)).append(")  ");
        }
        label.setText(sb.toString());
    }

    public void attachStopLogConsoleTrackingListeners(ProcessHandler process) {
      myRunProcess = process;
      for (AdditionalTabComponent component: myAdditionalComponents.keySet()) {
        if (component instanceof LogConsole){
          ((LogConsole)component).attachStopLogConsoleTrackingListener(process);
        }
      }
    }

    public void addTestResult(TestResultMessage result, List<Printable> output, int exceptionMark) {

        // TODO This should be an action button which rebuilds the tree when toggled.
        boolean flattenPackages = true;

        TestProxy classNode;
        if (flattenPackages) {
            classNode = getPackageClassNodeFor(result);
        } else {
            classNode = getClassNodeFor(result);
        }
        if (result.getResult() == MessageHelper.TEST_STARTED) {
            TestProxy proxy = new TestProxy();
            proxy.setParent(classNode);
            proxy.setResultMessage(result);
            animator.setCurrentTestCase(proxy);
            treeBuilder.addItem(classNode, proxy);
            treeBuilder.repaintWithParents(proxy);
            count++;
            if (count > total) total = count;
            if (TestNGConsoleProperties.TRACK_RUNNING_TEST.value(consoleProperties)) {
                selectTest(proxy);
            }
        } else {
            model.addTestResult(result);
            final TestProxy testCase = animator.getCurrentTestCase();
            if (testCase != null) {
              testCase.setResultMessage(result);
            }
            animator.setCurrentTestCase(null);
            Object[] children = treeBuilder.getTreeStructure().getChildElements(classNode);
            for (Object child : children) {
                TestProxy proxy = (TestProxy) child;
                if (result.equals(proxy.getResultMessage())) {
                    proxy.setResultMessage(result);
                    proxy.setOutput(output);
                    proxy.setExceptionMark(exceptionMark);
                    treeBuilder.repaintWithParents(proxy);
                }
            }
        }

        if (result.getResult() == MessageHelper.PASSED_TEST) {
            //passed++;
        } else if (result.getResult() == MessageHelper.FAILED_TEST) {
            failed++;
            progress.setColor(ColorProgressBar.RED);
        }
        progress.setFraction((double) count / total);
        updateLabel(statusLabel);
    }

    private String packageNameFor(String fqnClassName) {
        Matcher matcher = packagePattern.matcher(fqnClassName);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return NO_PACKAGE;
        }
    }

    private String classNameFor(String fqnClassName) {
        Matcher matcher = packagePattern.matcher(fqnClassName);
        if (matcher.matches()) {
            return matcher.group(2);
        } else {
            return fqnClassName;
        }
    }

    private TestProxy getPackageClassNodeFor(final TestResultMessage result) {
        TestProxy owner = treeBuilder.getRoot();
        String packageName = packageNameFor(result.getTestClass());
        owner = getChildNodeNamed(owner, packageName);
        if (owner.getPsiElement() == null) {
            owner.setPsiElement(PsiManager.getInstance(project).findPackage(packageName));
        }
        owner = getChildNodeNamed(owner, classNameFor(result.getTestClass()));
        //look up the psiclass now
        if (owner.getPsiElement() == null) {
            final TestProxy finalOwner = owner;
            ApplicationManager.getApplication().runReadAction(new Runnable()
            {
                public void run() {
                    finalOwner.setPsiElement(ClassUtil.findPsiClass(PsiManager.getInstance(project), result.getTestClass()));
                }
            });
        }
        return owner;
    }

    private TestProxy getClassNodeFor(TestResultMessage result) {

        String[] nodes = result.getTestClass().split("\\.");
        TestProxy owner = treeBuilder.getRoot();
        for (String node : nodes) {
            owner = getChildNodeNamed(owner, node);
        }
        return owner;
    }

    private TestProxy getChildNodeNamed(TestProxy currentNode, String node) {
        for (TestProxy child : currentNode.getResults()) {
            if (child.getName().equals(node)) {
                return child;
            }
        }

        TestProxy child = new TestProxy(node);
        treeBuilder.addItem(currentNode, child);
        return child;
    }

    public void selectTest(TestProxy proxy) {
        if (proxy == null) return;
        DefaultMutableTreeNode node = treeBuilder.ensureTestVisible(proxy);
        if (node == null) return;
        TreePath path = TreeUtil.getPath((TreeNode) tree.getModel().getRoot(), node);
        if (path == null) return;
        tree.setSelectionPath(path);
        tree.makeVisible(path);
        tree.scrollPathToVisible(path);
    }

    public JTable getResultsTable() {
        return resultsTable;
    }

    public JPanel getMain() {
        return main;
    }

    public ColorProgressBar getProgress() {
        return progress;
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }


    public void setTotal(int total) {
        this.total = total;
    }

    public void start() {
        start = System.currentTimeMillis();
        tree.getSelectionModel().setSelectionPath(new TreePath(treeBuilder.getNodeForElement(rootNode)));
        rootNode.setInProgress(true);
        rootNode.setStarted(true);
    }

    public void finish() {
        if (end > 0) return;
        end = System.currentTimeMillis();
        animator.stop();
        updateLabel(statusLabel);
        rootNode.setInProgress(false);
        if (TestNGConsoleProperties.SELECT_FIRST_DEFECT.value(consoleProperties)) {
            selectTest(rootNode.getFirstDefect());
        } else {
            tree.getSelectionModel().setSelectionPath(new TreePath(treeBuilder.getNodeForElement(rootNode)));
        }
        tree.repaint();
        LvcsHelper.addLabel(this);
    }

    public TestNGConsoleProperties getProperties() {
        return consoleProperties;
    }

  public void setFilter(final Filter filter) {
    getTreeStructure().setFilter(filter);
    treeBuilder.updateFromRoot();
  }

  public void addListener(ModelListener l) {
    myListeners.add(l);
  }

  public boolean isRunning() {
    return rootNode.isInProgress();
  }

  public TestTreeView getTreeView() {
    return tree;
  }

  public boolean hasTestSuites() {
    return rootNode.getResults().size() > 0;
  }

  public TestProxy getRoot() {
        return rootNode;
    }

  public void selectAndNotify(final AbstractTestProxy testProxy) {
    selectTest((TestProxy)testProxy);
  }

  public TestTreeStructure getTreeStructure() {
        return (TestTreeStructure) treeBuilder.getTreeStructure();
    }

    public void rebuildTree() {
        treeBuilder.updateFromRoot();
        tree.invalidate();
    }

    public void dispose() {
      for (ModelListener listener : myListeners) {
        listener.onDispose();
      }
        Disposer.dispose(treeBuilder);
        animator.dispose();
        toolbar.dispose();
        openSourceListener.structure = null;
        openSourceListener.console = null;
        tree.getSelectionModel().removeTreeSelectionListener(openSourceListener);
    }

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent) {
    myAdditionalComponents.put(tabComponent, tabbedPane.getTabCount());
    tabbedPane.addTab(tabComponent.getTabTitle(), null, tabComponent.getComponent(), tabComponent.getTooltip());
    Disposer.register(this, new Disposable() {
      public void dispose() {
        removeAdditionalTabComponent(tabComponent);
      }
    });
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    tabbedPane.removeTabAt(myAdditionalComponents.get(component).intValue());
    myAdditionalComponents.remove(component);
    component.dispose();
  }

  public void addLogConsole(final String name, final String path, final long skippedContent){
    final LogConsole log = new LogConsole(project, new File(path), skippedContent, name) {
      public boolean isActive() {
        return tabbedPane.getSelectedComponent() == this;
      }
    };

    if (myRunProcess != null) {
      log.attachStopLogConsoleTrackingListener(myRunProcess);
    }
    addAdditionalTabComponent(log);
    tabbedPane.addChangeListener(log);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        tabbedPane.removeChangeListener(log);
      }
    });
  }

  public void removeLogConsole(final String path) {
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
      tabbedPane.removeChangeListener(componentToRemove);
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  private class OpenSourceSelectionListener implements TreeSelectionListener
    {
        private TestTreeStructure structure;
        private TestNGConsoleView console;

        public OpenSourceSelectionListener(TestTreeStructure structure, TestNGConsoleView console) {
            this.structure = structure;
            this.console = console;
        }

        public void valueChanged(TreeSelectionEvent e) {
            TreePath path = e.getPath();
            if (path == null) return;
            TestProxy proxy = (TestProxy)tree.getSelectedTest();
            if (proxy == null) return;
            if (ScrollToTestSourceAction.isScrollEnabled(TestNGResults.this)) {
                OpenSourceUtil.openSourcesFrom(tree, false);
            }
            if (proxy == structure.getRootElement()) {
                console.reset();
            } else {
              console.setView(proxy.getOutput(),
                              TestNGConsoleProperties.SCROLL_TO_STACK_TRACE.value(getProperties()) ? proxy.getExceptionMark() : 0);
            }
        }
    }
}