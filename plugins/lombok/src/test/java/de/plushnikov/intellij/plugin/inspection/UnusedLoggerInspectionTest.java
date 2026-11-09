// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class UnusedLoggerInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new UnusedLoggerInspection(), "/inspection/unusedLogger");

  @Test
  public void testUnusedLogger() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testRemoveUnusedLoggerImport() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testInvalidAnnotationTargetsWithoutClassLogger() {
    checkInvalidAnnotationTargets("", "");
  }

  @Test
  public void testInvalidAnnotationTargetsWithClassLogger() {
    checkInvalidAnnotationTargets("@Log", "Object logger = log;");
  }

  private void checkInvalidAnnotationTargets(String classAnnotation, String loggerUsage) {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("InvalidTargets.java", """
      import lombok.extern.java.Log;

      %s
      class InvalidTargets {
        <error>@Log</error>
        private String field;

        <error>@Log</error>
        InvalidTargets() {}

        <error>@Log</error>
        void method(<error>@Log</error> String parameter) {
          <error>@Log</error> String local = parameter;
          %s
        }

        Object value = new <error>@Log</error> Object();
      }
      """.formatted(classAnnotation, loggerUsage));
    myFixture.checkHighlighting();
  }
}
