// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.junit.Assert;

import java.util.Collections;

public class MavenGeneralSettingsTest extends CodeInsightFixtureTestCase {

  public void testUpdateFromMavenConfigDisabled() {
    VirtualFile virtualFile = myFixture
      .addFileToProject(".mvn/maven.config", "-o -U -N -T3 -q -X -e -C -ff -s user-settings.xml -gs global-settings.xml")
      .getVirtualFile().getParent();

    MavenGeneralSettings settings = new MavenGeneralSettings(getProject());
    settings.updateFromMavenConfig(Collections.singletonList(virtualFile));

    Assert.assertEquals(MavenExecutionOptions.ChecksumPolicy.NOT_SET, settings.getChecksumPolicy());
    Assert.assertEquals(MavenExecutionOptions.FailureMode.NOT_SET, settings.getFailureBehavior());
    Assert.assertEquals(MavenExecutionOptions.LoggingLevel.INFO, settings.getOutputLevel());
    Assert.assertFalse(settings.isAlwaysUpdateSnapshots());
    Assert.assertFalse(settings.isWorkOffline());
    Assert.assertFalse(settings.isPrintErrorStackTraces());
    Assert.assertFalse(settings.isNonRecursive());
    Assert.assertTrue(StringUtil.isEmpty(settings.getThreads()));
  }

  public void testUpdateFromMavenConfig() {
    VirtualFile virtualFile = myFixture.addFileToProject("user-settings.xml", "<settings/>").getVirtualFile();
    myFixture.addFileToProject("global-settings.xml", "<settings/>");
    myFixture.addFileToProject(".mvn/maven.config", "-o -U -N -T3 -q -X -e -C -ff -s user-settings.xml -gs global-settings.xml");

    MavenGeneralSettings settings = new MavenGeneralSettings(getProject());
    settings.setUseMavenConfig(true);
    settings.updateFromMavenConfig(Collections.singletonList(virtualFile));
    Assert.assertEquals(MavenExecutionOptions.ChecksumPolicy.FAIL, settings.getChecksumPolicy());
    Assert.assertEquals(MavenExecutionOptions.FailureMode.FAST, settings.getFailureBehavior());
    Assert.assertEquals(MavenExecutionOptions.LoggingLevel.DISABLED, settings.getOutputLevel());
    Assert.assertTrue(settings.isAlwaysUpdateSnapshots());
    Assert.assertTrue(settings.isWorkOffline());
    Assert.assertTrue(settings.isPrintErrorStackTraces());
    Assert.assertTrue(settings.isNonRecursive());
    Assert.assertEquals("3", settings.getThreads());
  }
}