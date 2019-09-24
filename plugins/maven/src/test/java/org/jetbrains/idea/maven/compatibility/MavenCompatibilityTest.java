// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compatibility;


import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

@RunWith(value = Parameterized.class)
public abstract class MavenCompatibilityTest extends MavenImportingTestCase {
  @Rule public TestName name = new TestName();

  @NotNull
  protected MavenWrapperTestFixture myWrapperTestFixture;

  @Parameter
  public String myMavenVersion;



  protected void assumeVersionMoreThan(String version) {
    Assume.assumeTrue("Version should be more than " + version, VersionComparatorUtil.compare(myMavenVersion, version) > 0);
  }

  protected void assumeVersionLessThan(String version) {
    Assume.assumeTrue("Version should be less than " + version, VersionComparatorUtil.compare(myMavenVersion, version) > 0);
  }

  protected void assumeVersionNot(String version) {
    Assume.assumeTrue("Version " + version + " skipped", VersionComparatorUtil.compare(myMavenVersion, version) != 0);
  }

  protected void doTest(ThrowableRunnable<Throwable> throwableRunnable) throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    Runnable runnable = () -> {
      try {
        TestLoggerFactory.onTestStarted();
        assertEquals(myMavenVersion, MavenServerManager.getInstance().getCurrentMavenVersion());
        throwableRunnable.run();
        TestLoggerFactory.onTestFinished(true);
      }
      catch (InvocationTargetException e) {
        TestLoggerFactory.onTestFinished(false);
        e.fillInStackTrace();
        throwables[0] = e.getTargetException();
      }
      catch (IllegalAccessException e) {
        TestLoggerFactory.onTestFinished(false);
        e.fillInStackTrace();
        throwables[0] = e;
      }
      catch (Throwable e) {
        TestLoggerFactory.onTestFinished(false);
        throwables[0] = e;
      }
    };

    try {
      WriteAction.runAndWait(() -> {
        invokeTestRunnable(runnable);
      });
    }
    catch (Throwable throwable) {
      ExceptionUtil.rethrowAllAsUnchecked(throwable);
    }
    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myWrapperTestFixture = new MavenWrapperTestFixture(myMavenVersion);
    myWrapperTestFixture.setUp();
  }

  @Override
  public String getName() {
    return name.getMethodName() == null ? super.getName() : FileUtil.sanitizeFileName(name.getMethodName());
  }

  @Override
  @After
  public void tearDown() {
    new RunAll(
      () -> myWrapperTestFixture.tearDown(),
      ()-> super.tearDown()
    ).run();
  }


  @Parameterized.Parameters(name = "with Maven-{0}")
  public static List<String[]> getMavenVersions() {
    return Arrays.asList(
      new String[]{"3.6.2"},
      new String[]{"3.6.1"},
      new String[]{"3.6.0"},
      new String[]{"3.5.4"},
      new String[]{"3.5.3"},
      new String[]{"3.5.2"},
      new String[]{"3.5.0"},
      new String[]{"3.3.9"},
      new String[]{"3.3.3"},
      new String[]{"3.3.1"},
      new String[]{"3.2.5"},
      new String[]{"3.2.3"},
      new String[]{"3.2.2"},
      new String[]{"3.2.1"},
      new String[]{"3.1.1"},
      new String[]{"3.1.0"},
      new String[]{"3.0.5"},
      new String[]{"3.0.4"},
      new String[]{"3.0.3"},
      new String[]{"3.0.2"},
      new String[]{"3.0.1"},
      new String[]{"3.0"}
    );
  }
}
