// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.config;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.junit.Assert;

public class MavenConfigParserTest extends CodeInsightFixtureTestCase {

  public void testParseShortNames() {
    myFixture.addFileToProject(".mvn/maven.config",
                               "-o -U -N -T3 -q -X -e -C -c -ff -fae -fn" +
                               " -s user-settings.xml -gs global-settings.xml");
    MavenConfig config = MavenConfigParser.parse(myFixture.getTempDirPath());
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.OFFLINE));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.UPDATE_SNAPSHOTS));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.NON_RECURSIVE));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.QUIET));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.ERRORS));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.DEBUG));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.CHECKSUM_WARNING_POLICY));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.CHECKSUM_FAILURE_POLICY));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.FAIL_AT_END));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.FAIL_FAST));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.FAIL_NEVER));
    Assert.assertEquals("3", config.getOptionValue(MavenConfigSettings.THREADS));
    Assert.assertEquals("user-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_USER_SETTINGS));
    Assert.assertEquals("global-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS));
  }

  public void testParseLongNames() {
    myFixture.addFileToProject(".mvn/maven.config",
                               "--offline --update-snapshots --non-recursive --quiet --debug --errors --strict-checksums " +
                               "--lax-checksums --fail-fast --fail-at-end --fail-never --threads 3 " +
                               "--settings user-settings.xml --global-settings global-settings.xml");
    MavenConfig config = MavenConfigParser.parse(myFixture.getTempDirPath());
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.OFFLINE));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.UPDATE_SNAPSHOTS));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.NON_RECURSIVE));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.QUIET));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.ERRORS));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.DEBUG));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.CHECKSUM_WARNING_POLICY));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.CHECKSUM_FAILURE_POLICY));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.FAIL_AT_END));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.FAIL_FAST));
    Assert.assertNotNull(config.hasOption(MavenConfigSettings.FAIL_NEVER));
    Assert.assertEquals("3", config.getOptionValue(MavenConfigSettings.THREADS));
    Assert.assertEquals("user-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_USER_SETTINGS));
    Assert.assertEquals("global-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS));
  }

  public void testUnknownNames() {
    myFixture.addFileToProject(".mvn/maven.config", "-unknown -ZZ --badprop");
    MavenConfig config = MavenConfigParser.parse(myFixture.getTempDirPath());
    Assert.assertTrue(config.isEmpty());
  }
}