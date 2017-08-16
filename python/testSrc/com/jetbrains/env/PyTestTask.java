package com.jetbrains.env;

import com.google.common.collect.Sets;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author traff
 */
public abstract class PyTestTask {
  private String myScriptName;
  private String myScriptParameters;



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
   * To create SDK from path, use {@link PyExecutionFixtureTestTask#createTempSdk(String, sdkTools.SdkCreationType)}
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

  public void doFinally() {
  }

  public void useNormalTimeout() {
  }

  public void useLongTimeout() {
  }

  public String getScriptName() {
    return myScriptName;
  }



  public String getScriptParameters() {
    return myScriptParameters;
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

  /**
   * Provides a way to filter out non-relevant environments
   *
   * @return the set of a tags that interpreter should run on, if an environment doesn't contain one of them, it won't be
   * used to run this test task.
   * null in case filtering shouldn't be used
   */
  @Nullable
  public Set<String> getTagsToCover() {
    return null;
  }
}
