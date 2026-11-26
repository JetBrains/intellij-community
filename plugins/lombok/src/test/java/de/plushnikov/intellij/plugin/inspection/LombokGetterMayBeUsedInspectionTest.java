// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokDaemonAnalyzerFixture;

@TestApplication
public class LombokGetterMayBeUsedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lombokDaemonAnalyzerFixture(projectFixture, "/inspection/lombokGetterMayBeUsed/", () -> List.of(new LombokGetterMayBeUsedInspection()));

  private void doTest() {
    LightDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(fixture.getFixture().getTestName(false) + ".java", true, false);
  }

  @Test
  public void testFieldsWithGetter() {
    doTest();
  }

  @Test
  public void testInstanceAndStaticFields() {
    doTest();
  }

  @Test
  public void testShortMethods() {
    doTest();
  }

  @Test
  public void testRecord() {
    doTest();
  }

  @Test
  public void testCompactSourceFile() {
    doTest();
  }
}
