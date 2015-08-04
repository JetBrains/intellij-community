/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 6, 2005
 * Time: 10:49:05 PM
 */
package com.theoryinpractice.testng.ui;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.OpenSourceUtil;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.*;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

public class TestNGResults extends TestResultsPanel implements TestFrameworkRunningModel {
  @NonNls private static final String TESTNG_SPLITTER_PROPERTY = "TestNG.Splitter.Proportion";

  private final TableView resultsTable;

  private final TestNGResultsTableModel model;
  private final TestNGConfiguration configuration;
  private TestNGTestTreeView tree;

  private final Project project;
  private int count;
  private int total = 0;
  private final Set<TestProxy> failed = new HashSet<TestProxy>();
  private final Map<TestResultMessage, List<TestProxy>> started = new HashMap<TestResultMessage, List<TestProxy>>();
  private TestProxy failedToStart = null;
  private long start;
  private long end;
  private TestTreeBuilder treeBuilder;

  private final TreeRootNode rootNode;
  private static final String NO_PACKAGE = "No Package";
  private TestNGResults.OpenSourceSelectionListener openSourceListener;
  private int myStatus = MessageHelper.PASSED_TEST;
  private Set<String> startedMethods = new HashSet<String>();
  private TestProxy myLastSelected;
  private TestsProgressAnimator animator;

  public TestNGResults(final JComponent component,
                       final TestNGConfiguration configuration,
                       final TestNGConsoleView console) {
    super(component, console.getConsole().createConsoleActions(), console.getProperties(),
          TESTNG_SPLITTER_PROPERTY, 0.5f);
    this.configuration = configuration;
    this.project = configuration.getProject();

    model = new TestNGResultsTableModel(project);
    resultsTable = new TableView(model);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        final Object result = resultsTable.getSelectedObject();
        if (result instanceof TestResultMessage) {
          final String testClass = ((TestResultMessage)result).getTestClass();
          final PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), testClass);
          if (psiClass != null) {
            final String method = ((TestResultMessage)result).getMethod();
            if (method != null) {
              final PsiMethod[] psiMethods = psiClass.findMethodsByName(method, false);
              if (psiMethods.length > 0) {
                psiMethods[0].navigate(true);
              }
            }
            psiClass.navigate(true);
            return true;
          }
        }
        return false;
      }
    }.installOn(resultsTable);

    rootNode = new TreeRootNode();
    console.getUnboundOutput().addChild(rootNode);
  }

  protected JComponent createTestTreeView() {
    tree = new TestNGTestTreeView();

    final TestTreeStructure structure = new TestTreeStructure(project, rootNode);
    tree.attachToModel(this);
    treeBuilder = new TestTreeBuilder(tree, structure);
    Disposer.register(this, treeBuilder);

    animator = new TestsProgressAnimator(treeBuilder);

    openSourceListener = new OpenSourceSelectionListener();
    tree.getSelectionModel().addTreeSelectionListener(openSourceListener);

    TrackRunningTestUtil.installStopListeners(tree, this, new Pass<AbstractTestProxy>() {
      @Override
      public void pass(AbstractTestProxy abstractTestProxy) {
        myLastSelected = (TestProxy)abstractTestProxy;
      }
    });

    return tree;
  }

  @Override
  protected ToolbarPanel createToolbarPanel() {
    final ToolbarPanel panel = new ToolbarPanel(getProperties(), this);
    panel.setModel(this);
    return panel;
  }

  public TestConsoleProperties getProperties() {
    return myProperties;
  }

  protected JComponent createStatisticsPanel() {
    final JPanel panel = new JPanel(new BorderLayout()); //do not remove wrapper panel 
    panel.add(ScrollPaneFactory.createScrollPane(resultsTable), BorderLayout.CENTER);
    return panel;
  }

  private void updateStatusLine() {
    myStatusLine.setText(getStatusLine());
  }

  public int getStatus() {
    return myStatus;
  }

  public String getStatusLine() {
    StringBuffer sb = new StringBuffer();
    if (end == 0 && start > 0) {
      sb.append("Running: ");
    }
    else {
      if (failed.size() > 0) sb.append("Failed: ").append(failed.size()).append("   ");
      sb.append("Done: ");
    }
    sb.append(count).append(" of ").append(total);
    if (end == 0) {
      if (failed.size() > 0) {
        sb.append("   Failed: ").append(failed.size());
      }
    }
    else {
      final long time = end - start;
      sb.append(" (").append(time == 0 ? "0.0 s" : NumberFormat.getInstance().format((double)time / 1000.0) + " s").append(")  ");
    }
    return sb.toString();
  }
  
  public String getTime() {
    final long time = end - start;
    return time == 0 ? "0.0 s" : NumberFormat.getInstance().format((double)time / 1000.0) + " s";
  }

  public TestProxy testStarted(TestResultMessage result) {
    return testStarted(result, true);
  }

  public TestProxy testStarted(TestResultMessage result, boolean registerDups) {
    TestProxy classNode = getPackageClassNodeFor(result);
    TestProxy proxy = new TestProxy();
    proxy.setParent(classNode);
    proxy.setResultMessage(result);
    synchronized (started) {
      if (registerDups) {
        List<TestProxy> dups = started.get(result);
        if (dups == null) {
          dups = new ArrayList<TestProxy>();
          started.put(result, dups);
        }
        dups.add(proxy);
      }
    }
    final String testMethodDescriptor = result.getTestClass() + TestProxy.toDisplayText(result, project);
    if (startedMethods.contains(testMethodDescriptor)) {
      total++;
    }
    else {
      startedMethods.add(testMethodDescriptor);
    }
    animator.setCurrentTestCase(proxy);
    treeBuilder.addItem(classNode, proxy);
    //treeBuilder.repaintWithParents(proxy);
    count++;
    if (count > total) total = count;
    if (myLastSelected == proxy) {
      myLastSelected = null;
    }
    if (myLastSelected == null && TestConsoleProperties.TRACK_RUNNING_TEST.value(myProperties)) {
      selectTest(proxy);
    }
    return proxy;
  }

  public void addTestResult(final TestResultMessage result, int exceptionMark) {
    TestProxy testCase;
    synchronized (started) {
      final List<TestProxy> dups = started.get(result);
      testCase = dups == null || dups.isEmpty() ? null : dups.remove(0);
    }
    if (testCase == null) {
      final PsiElement element = getPackageClassNodeFor(result).getPsiElement();
      if (element instanceof PsiClass) {
        final PsiMethod[] methods = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiMethod[]>() {
            public PsiMethod[] compute() {
              return ((PsiClass)element).findMethodsByName(result.getMethod(), true);
            }
          }
        );
        if (methods.length > 0 &&
            methods[0] != null &&
            !AnnotationUtil.isAnnotated(methods[0], Arrays.asList(TestNGUtil.CONFIG_ANNOTATIONS_FQN))) {
          for (List<TestProxy> proxies : started.values()) {
            if (proxies != null) {
              for (TestProxy proxy : proxies) {
                if (methods[0].equals(proxy.getPsiElement())) {
                  testCase = proxy;
                  break;
                }
              }
            }
          }
          if (testCase == null) {
            testCase = testStarted(result, false);
            testCase.appendStacktrace(result);
          }
        }
      }
    }

    if (testCase != null) {
      testCase.setResultMessage(result);
      testCase.setTearDownFailure(failedToStart != null);
      failedToStart = null;

      if (result.getResult() == MessageHelper.FAILED_TEST) {
        failed.add(testCase);
      }
      model.addTestResult(result);
    }
    else {
      //do not remember testresultmessage: test hierarchy is not set
      testCase = new TestProxy(TestProxy.toDisplayText(result, project));
      testCase.appendStacktrace(result);
      if (failedToStart != null) {
        failedToStart.addChild(testCase);
        failedToStart.setTearDownFailure(true);
      }
      else {
        failedToStart = testCase;
      }
    }

    testCase.setExceptionMark(exceptionMark);
    AbstractTestProxy.flushOutput(testCase);

    if (result.getResult() == MessageHelper.FAILED_TEST) {
      myStatusLine.setStatusColor(ColorProgressBar.RED);
      myStatus = MessageHelper.FAILED_TEST;
    }
    else if (result.getResult() == MessageHelper.SKIPPED_TEST && myStatus == MessageHelper.PASSED_TEST) {
      myStatus = MessageHelper.SKIPPED_TEST;
    }
    myStatusLine.setFraction((double)count / total);
    updateStatusLine();
    TestsUIUtil.showIconProgress(project, count, total, failed.size(), false);
  }

  private TestProxy getPackageClassNodeFor(final TestResultMessage result) {
    TestProxy owner = treeBuilder.getRoot();
    final String packageName1 = StringUtil.getPackageName(result.getTestClass());
    String packageName = packageName1.length() == 0 ? NO_PACKAGE : packageName1;
    owner = getChildNodeNamed(owner, packageName);
    if (owner.getPsiElement() == null) {
      owner.setPsiElement(JavaPsiFacade.getInstance(project).findPackage(packageName));
    }
    owner = getChildNodeNamed(owner, StringUtil.getShortName(result.getTestClass()));
    //look up the psiclass now
    if (owner.getPsiElement() == null) {
      final TestProxy finalOwner = owner;
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          finalOwner.setPsiElement(ClassUtil.findPsiClass(PsiManager.getInstance(project), result.getTestClass()));
        }
      });
    }
    return owner;
  }

  private TestProxy getChildNodeNamed(TestProxy currentNode, String node) {
    for (TestProxy child : currentNode.getChildren()) {
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
    treeBuilder.select(proxy, null);
  }

  public void setTotal(int total) {
    this.total += total;
  }

  public void start() {
    if (start == 0) {
      start = System.currentTimeMillis();
    }
    treeBuilder.select(rootNode);
    rootNode.setInProgress(true);
    rootNode.setStarted(true);
  }

  public void finish(final boolean started) {
    if (start > 0) {
      end = System.currentTimeMillis();
    }
    LvcsHelper.addLabel(this);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        animator.stopMovie();
        updateStatusLine();
        if (total > count || myStatus == MessageHelper.SKIPPED_TEST) {
          myStatusLine.setStatusColor(ColorProgressBar.YELLOW);
        }
        else if (myStatus == MessageHelper.FAILED_TEST) {
          myStatusLine.setStatusColor(ColorProgressBar.RED);
        }
        else {
          myStatusLine.setStatusColor(ColorProgressBar.GREEN);
        }
        rootNode.setInProgress(false);
        if (TestNGConsoleProperties.SELECT_FIRST_DEFECT.value(myProperties)) {
          selectTest(rootNode.getFirstDefect());
        }
        else {
          final DefaultMutableTreeNode node = treeBuilder.getNodeForElement(rootNode);
          if (node != null && myLastSelected == null) {
            tree.getSelectionModel().setSelectionPath(new TreePath(node));
          }
        }
        tree.repaint();
        if (total > 0 ||
            !ResetConfigurationModuleAdapter.tryWithAnotherModule(configuration, getProperties().isDebug())) {
          TestsUIUtil.notifyByBalloon(project, started, rootNode, getProperties(), "in " + getTime());
        }
      }
    });
  }

  public void setFilter(final Filter filter) {
    getTreeStructure().setFilter(filter);
    treeBuilder.updateFromRoot();
  }

  public boolean isRunning() {
    return rootNode.isInProgress();
  }

  public TestTreeView getTreeView() {
    return tree;
  }

  @Override
  public TestTreeBuilder getTreeBuilder() {
    return treeBuilder;
  }

  public boolean hasTestSuites() {
    return rootNode.getChildren().size() > 0;
  }

  public TestProxy getRoot() {
    return rootNode;
  }

  public void selectAndNotify(final AbstractTestProxy testProxy) {
    selectTest((TestProxy)testProxy);
  }

  public TestTreeStructure getTreeStructure() {
    return (TestTreeStructure)treeBuilder.getTreeStructure();
  }

  public void rebuildTree() {
    treeBuilder.updateFromRoot();
    tree.invalidate();
  }

  public void dispose() {
    super.dispose();
    tree.getSelectionModel().removeTreeSelectionListener(openSourceListener);
    TestsUIUtil.clearIconProgress(project);
  }

  public TestProxy getFailedToStart() {
    return failedToStart;
  }

  public void setFailedToStart(TestProxy failedToStart) {
    this.failedToStart = failedToStart;
  }

  public boolean hasFinishedTests() {
    return count > 0;
  }

  private class OpenSourceSelectionListener implements TreeSelectionListener {

    public void valueChanged(TreeSelectionEvent e) {
      TreePath path = e.getPath();
      if (path == null) return;
      TestProxy proxy = (TestProxy)tree.getSelectedTest();
      if (proxy == null) return;
      if (ScrollToTestSourceAction.isScrollEnabled(TestNGResults.this)) {
        OpenSourceUtil.openSourcesFrom(tree, false);
      }
    }
  }
}
