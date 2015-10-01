package com.jetbrains.env;

import com.google.common.collect.Sets;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author traff
 */
public abstract class PyTestTask {
  private String myWorkingFolder;
  private String myScriptName;
  private String myScriptParameters;

  public void setWorkingFolder(String workingFolder) {
    myWorkingFolder = workingFolder;
  }

  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  public void setScriptParameters(String scriptParameters) {
    myScriptParameters = scriptParameters;
  }

  public void setUp(String testName) throws Exception {
  }

  public void tearDown() throws Exception {
  }

  /**
   * Run test on certain SDK path.
   * To create SDK from path, use {@link PyExecutionFixtureTestTask#createTempSdk(String, com.jetbrains.python.sdkTools.SdkCreationType)}
   *
   * @param sdkHome sdk path
   */
  public abstract void runTestOn(String sdkHome) throws Exception;

  public void before() throws Exception {
  }

  public void testing() throws Exception {
  }

  public void after() throws Exception {
  }

  public void useNormalTimeout() {
  }

  public void useLongTimeout() {
  }

  public String getScriptName() {
    return myScriptName;
  }

  public String getScriptPath() {
    return getFilePath(myScriptName);
  }

  public String getFilePath(String scriptName) {
    return FileUtil
      .toSystemDependentName(myWorkingFolder.endsWith("/") ? myWorkingFolder + scriptName : myWorkingFolder + "/" + scriptName);
  }

  public String getScriptParameters() {
    return myScriptParameters;
  }

  public String getWorkingFolder() {
    return myWorkingFolder;
  }

  /**
   * @return tags this task needs to exist on interpreter to run
   */
  @NotNull
  public Set<String> getTags() {
    return Sets.newHashSet();
  }

  /**
   * Checks if task supports this language level
   * @param level level to check
   * @return true if supports
   */
  public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
    return true;
  }
}
