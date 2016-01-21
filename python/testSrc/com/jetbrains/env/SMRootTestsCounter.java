/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env;

import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy;
import org.jetbrains.annotations.NotNull;

/**
 * Provides high level API for number of tests calculations in {@link SMTestProxy}
 * @author Ilya.Kazakevich
 */
final class SMRootTestsCounter {
  /**
   * Filter to exclude suites
   */
  static final Filter<SMTestProxy> NOT_SUIT = new Filter<SMTestProxy>() {
    @Override
    public boolean shouldAccept(final SMTestProxy test) {
      return !test.isSuite();
    }
  };

  @NotNull
  private SMTestProxy.SMRootTestProxy myTestProxy;

  /**
   * @param testProxy proxy to wrap
   */
  SMRootTestsCounter(@NotNull final SMRootTestProxy testProxy) {
    myTestProxy = testProxy;
  }

  @NotNull
  SMRootTestProxy getProxy() {
    return myTestProxy;
  }

  /**
   * @return number of failed tests
   */
  int getFailedTestsCount() {
    return myTestProxy.collectChildren(NOT_SUIT.and(Filter.FAILED_OR_INTERRUPTED)).size();
  }

  /**
   * @return number of passed tests
   */
  int getPassedTestsCount() {
    return myTestProxy.collectChildren(NOT_SUIT.and(Filter.PASSED)).size();
  }


  /**
   * @return number of all tests
   */
  int getAllTestsCount() {
    return myTestProxy.collectChildren(NOT_SUIT).size();
  }
}
