package com.jetbrains.performanceScripts.lang;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.performancePlugin.CommandProvider;
import junit.framework.TestCase;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IJPerfCompletionTest extends BasePlatformTestCase {

  public void testEmptyFile() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNotNull(lookupElements);
    List<String> expectedCommands = new ArrayList<>(CommandProvider.getAllCommandNames());
    expectedCommands.add("%%project");
    expectedCommands.add("%%profileIndexing");
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue(strings.containsAll(expectedCommands));
  }

  public void testAfterCommand() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNull(lookupElements);
  }

  public void testBetweenStatements() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNotNull(lookupElements);
    List<String> expectedCommands = new ArrayList<>(CommandProvider.getAllCommandNames());
    expectedCommands.add("%%project");
    expectedCommands.add("%%profileIndexing");
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue(strings.containsAll(expectedCommands));
  }

  public void testDoublePrefix() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    assertNotNull(lookupElements);
    assertNotEmpty(Arrays.asList(lookupElements));
    List<String> expectedCommands = Arrays.asList("%%project", "%%profileIndexing");
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue(expectedCommands.containsAll(strings));
  }

  public void testEndOfStatement() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNotNull(lookupElements);
    assertEmpty(lookupElements);
  }

  public void testInOptionList() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNotNull(lookupElements);
    assertEmpty(lookupElements);
  }

  public void testSinglePrefix() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNotNull(lookupElements);
    List<String> expectedCommands = new ArrayList<>(CommandProvider.getAllCommandNames());
    List<String> strings = myFixture.getLookupElementStrings();
    TestCase.assertTrue(strings.containsAll(expectedCommands));
  }

  public void testOnCommand() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNotNull(lookupElements);
    List<String> expectedCommands = ContainerUtil.filter(CommandProvider.getAllCommandNames(), command -> command.startsWith("%start"));
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue(strings.containsAll(expectedCommands));
  }

  public void testWithoutPrefix() {
    myFixture.configureByFile(getTestName(true) + "." + IJPerfFileType.DEFAULT_EXTENSION);
    final LookupElement[] lookupElements = myFixture.completeBasic();
    Assert.assertNotNull(lookupElements);
    List<String> expectedCommands = ContainerUtil.filter(CommandProvider.getAllCommandNames(), command -> command.contains("proj"));
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue(strings.containsAll(expectedCommands));
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + TestUtil.getDataSubPath("completion");
  }
}
