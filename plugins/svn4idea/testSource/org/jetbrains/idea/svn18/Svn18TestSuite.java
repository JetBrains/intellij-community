package org.jetbrains.idea.svn18;

import org.jetbrains.idea.SvnTestCase;
import org.jetbrains.idea.svn.*;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @author Konstantin Kolosovsky.
 */
// TODO: Add svn clients for mac and linux
@RunWith(Suite.class)
//@Suite.SuiteClasses({SvnAddTest.class, SvnDeleteTest.class, SvnQuickMergeTest.class})
@Suite.SuiteClasses({SvnMergeInfoTest.class})
public class Svn18TestSuite {

  @ClassRule
  public static TestRule configuration = new ExternalResource() {
    @Override
    protected void before() throws Throwable {
      SvnTestCase.ourGlobalTestDataDir = "testData18";
      SvnTestCase.ourGlobalUseNativeAcceleration = true;
    }

    @Override
    protected void after() {
      SvnTestCase.ourGlobalTestDataDir = null;
      SvnTestCase.ourGlobalUseNativeAcceleration = null;
    }
  };
}
