// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compatibility;


import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

@RunWith(value = Parameterized.class)
public class MavenCompatibilityTest extends MavenImportingTestCase {
  @Rule public TestName name = new TestName();

  @NotNull
  private MavenWrapperTestFixture myWrapperTestFixture;

  @Parameter
  public String myMavenVersion;


  @Test
  public void testSmokeImport() throws Throwable {

    doTest(() -> {

      assertEquals(myMavenVersion, MavenServerManager.getInstance().getCurrentMavenVersion());
      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>");


      assertModules("project");
    });
  }

  @Test
  public void testInterpolateModel() throws Throwable {
    doTest(() -> {

      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +

                    "<properties>\n" +
                    "    <junitVersion>4.0</junitVersion>" +
                    "  </properties>" +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>junit</groupId>" +
                    "    <artifactId>junit</artifactId>" +
                    "    <version>${junitVersion}</version>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("project");

      assertModuleLibDep("project", "Maven: junit:junit:4.0");
    });
  }

  private void doTest(ThrowableRunnable<Throwable> throwableRunnable) throws Throwable {
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
