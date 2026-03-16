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
    Assert.assertTrue(config.hasOption(MavenConfigSettings.OFFLINE));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.UPDATE_SNAPSHOTS));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.NON_RECURSIVE));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.QUIET));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.ERRORS));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.DEBUG));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_WARNING_POLICY));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_FAILURE_POLICY));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.FAIL_AT_END));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.FAIL_FAST));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.FAIL_NEVER));
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
    Assert.assertTrue(config.hasOption(MavenConfigSettings.OFFLINE));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.UPDATE_SNAPSHOTS));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.NON_RECURSIVE));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.QUIET));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.ERRORS));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.DEBUG));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_WARNING_POLICY));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_FAILURE_POLICY));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.FAIL_AT_END));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.FAIL_FAST));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.FAIL_NEVER));
    Assert.assertEquals("3", config.getOptionValue(MavenConfigSettings.THREADS));
    Assert.assertEquals("user-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_USER_SETTINGS));
    Assert.assertEquals("global-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS));
  }


  public void testParseJavaOptions() {
    myFixture.addFileToProject(".mvn/maven.config",
                               "-Dkey1=value -Dkey2=\"value with spaces\" " +
                               "\"-Dkey3=another value with spaces\" -Dkey4");
    MavenConfig config = MavenConfigParser.parse(myFixture.getTempDirPath());
    Assert.assertEquals("value", config.getJavaProperties().get("key1"));
    Assert.assertEquals("value with spaces", config.getJavaProperties().get("key2"));
    Assert.assertEquals("another value with spaces", config.getJavaProperties().get("key3"));
    Assert.assertEquals("", config.getJavaProperties().get("key4"));
    Assert.assertNull(config.getJavaProperties().get("key"));
  }

  public void testParseJavaOptionsTogetherWithMaven() {
    myFixture.addFileToProject(".mvn/maven.config",
                               "-Dkey1=value -Dkey2=\"value with spaces\"  \"-Dkey3=another value with spaces\" -Dkey4 --offline --threads 3");
    MavenConfig config = MavenConfigParser.parse(myFixture.getTempDirPath());
    Assert.assertEquals("value", config.getJavaProperties().get("key1"));
    Assert.assertEquals("value with spaces", config.getJavaProperties().get("key2"));
    Assert.assertEquals("another value with spaces", config.getJavaProperties().get("key3"));
    Assert.assertEquals("", config.getJavaProperties().get("key4"));
    Assert.assertNull(config.getJavaProperties().get("key"));
    Assert.assertTrue(config.hasOption(MavenConfigSettings.OFFLINE));
    Assert.assertEquals("3", config.getOptionValue(MavenConfigSettings.THREADS));

  }

  public void testUnknownNames() {
    myFixture.addFileToProject(".mvn/maven.config", "-unknown -ZZ --badprop");
    MavenConfig config = MavenConfigParser.parse(myFixture.getTempDirPath());
    Assert.assertTrue(config.isEmpty());
  }
}