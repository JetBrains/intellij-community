// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Producer;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

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

  /**
   * Method called on main thread.
   * Each inheritor may do anything on edt, but should call parent *after all* on main thread
   */
  public void tearDown() throws Exception {

  }

  /**
   * Run test on certain SDK path.
   * To create SDK from path, use {@link PyExecutionFixtureTestTask#createTempSdk(String, sdkTools.SdkCreationType)}
   *
   * @param sdkHome     sdk path
   * @param existingSdk If sdk exists already you are encouraged to reuse it. Create one using sdkHome otherwise.
   */
  public abstract void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception;

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


 public static<T> T getUnderEdt(@NotNull Producer<T> producer) {
    final Ref<T> ref = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ref.set(producer.produce());
    });
    return ref.get();
  }

  /**
   * @return tags this task needs to exist on interpreter to run
   */
  @NotNull
  public Set<String> getTags() {
    return new HashSet<String>();
  }

  /**
   * Checks if task supports this language level
   *
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
