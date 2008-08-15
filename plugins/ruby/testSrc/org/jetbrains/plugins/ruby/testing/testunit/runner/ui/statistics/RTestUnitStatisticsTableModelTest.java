package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestProxyTreeSelectionListener;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsTableModelTest extends BaseRUnitTestsTestCase {
  private RTestUnitStatisticsTableModel myStatisticsTableModel;
  private TestProxyTreeSelectionListener mySelectionListener;
  private RTestUnitEventsListener myTestEventsListener;
  private RTestUnitTestProxy myRootSuite;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myStatisticsTableModel = new RTestUnitStatisticsTableModel();
    mySelectionListener = myStatisticsTableModel.createSelectionListener();
    myTestEventsListener = myStatisticsTableModel.createTestEventsListener();

    myRootSuite = createSuiteProxy("root");
  }

  public void testOnSelected_UnsupportedType() {
    assertEmpty(getItems());
    mySelectionListener.onSelected(new PrintableTestProxy() {
       public boolean isRoot() {
         return false;
       }

       public boolean isInProgress() {
         return false;
       }

       public boolean isDefect() {
         return false;
       }

       public boolean shouldRun() {
         return false;
       }

       public int getMagnitude() {
         return 0;
       }

       public boolean isLeaf() {
         return true;
       }

       public String getName() {
         return "printable";
       }

       public Location getLocation(final Project project) {
         return null;
       }

       public Navigatable getDescriptor(final Location location) {
         return null;
       }

       public AbstractTestProxy getParent() {
         return null;
       }

       public List<? extends AbstractTestProxy> getChildren() {
         return Collections.emptyList();
       }

       public List<? extends AbstractTestProxy> getAllTests() {
         return Collections.emptyList();
       }

       public void setPrintLinstener(final Printer printer) {
         // Do nothing
       }

       public void printOn(final Printer printer) {
         // Do nothing
       }
     });

    assertEmpty(getItems());
  }

  public void testOnSelected_Null() {
    mySelectionListener.onSelected(null);

    assertEmpty(getItems());
  }

  public void testOnSelected_Test() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", myRootSuite);
    mySelectionListener.onSelected(test1);

    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnSelected_Suite() {
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite1);

    final RTestUnitTestProxy suite2 = createSuiteProxy("suite2", myRootSuite);

    mySelectionListener.onSelected(suite1);
    assertSameElements(getItems(), suite1, test1, test2);

    mySelectionListener.onSelected(suite2);
    assertSameElements(getItems(), suite2);

    mySelectionListener.onSelected(myRootSuite);
    assertSameElements(getItems(), myRootSuite, suite1, suite2);
  }

  public void testOnSuiteStarted_NoCurrent() {
    mySelectionListener.onSelected(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteStarted(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteStarted_Current() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelected(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(suite);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteStarted_Child() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelected(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(test1);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteStarted_Other() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final RTestUnitTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    mySelectionListener.onSelected(suite);
    assertSameElements(getItems(), suite);

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(other_suite);
    assertSameElements(getItems(), suite);
  }

  public void testOnSuiteFinished_NoCurrent() {
    mySelectionListener.onSelected(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteFinished(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteFinished_Current() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelected(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(suite);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteFinished_Child() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelected(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(test1);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteFinished_Other() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final RTestUnitTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    mySelectionListener.onSelected(suite);
    assertSameElements(getItems(), suite);

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(other_suite);
    assertSameElements(getItems(), suite);
  }

  public void testOnTestStarted_NoCurrent() {
    mySelectionListener.onSelected(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestStarted(test1);
    assertEmpty(getItems());
  }

  public void testOnTestStarted_Child() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    mySelectionListener.onSelected(test1);
    assertSameElements(getItems(), myRootSuite, test1);

    final RTestUnitTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(test1);
    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnTestStarted_Other() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy other_test = createTestProxy("other_test", suite);

    mySelectionListener.onSelected(test1);
    assertSameElements(getItems(), myRootSuite, test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(other_test);
    assertSameElements(getItems(), myRootSuite, test1, suite);
  }

  public void testOnTestFinished_NoCurrent() {
    mySelectionListener.onSelected(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestFinished(test1);
    assertEmpty(getItems());

  }

  public void testOnTestFinished_Child() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    mySelectionListener.onSelected(test1);
    assertSameElements(getItems(), myRootSuite, test1);

    final RTestUnitTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(test1);
    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnTestFinished_Other() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy other_test = createTestProxy("other_test", suite);

    mySelectionListener.onSelected(test1);
    assertSameElements(getItems(), myRootSuite, test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(other_test);
    assertSameElements(getItems(), myRootSuite, test1, suite);
  }

  private List<RTestUnitTestProxy> getItems() {
    return myStatisticsTableModel.getItems();
  }
}
