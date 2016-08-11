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
package com.intellij.tasks.impl.gson;

import com.google.gson.Gson;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

public class MandatoryTest extends TestCase {
  public void testSuccess1() {
    String json = "{ \"mandatory\" : \"text\" }";
    doTest(json, Class1.class, true);
  }

  public void testSuccess2() {
    String json = "{ \"simple\" : \"text2\", \"mandatory\" : \"text\" }";
    doTest(json, Class1.class, true);
  }

  public void testSuccess3() {
    String json = "{ \"unknown\" : \"text2\", \"mandatory\" : \"text\" }";
    doTest(json, Class1.class, true);
  }

  public void testSuccess4() {
    String json = "null";
    doTest(json, Class1.class, true);
  }

  public void testFailure1() {
    String json = "{ \"simple\" : \"text2\" }";
    doTest(json, Class1.class, false);
  }

  public void testFailure2() {
    String json = "{ \"unknown\" : \"text2\" }";
    doTest(json, Class1.class, false);
  }

  public void testFailure3() {
    String json = "{ \"mandatory\" : null }";
    doTest(json, Class1.class, false);
  }

  public void testSuccessEnclosed1() {
    String json = "{ \"mandatory\" : { \"mandatory\" : \"text\" } }";
    doTest(json, Class2.class, true);
  }

  public void testSuccessEnclosed2() {
    String json = "{ \"mandatory\" : { \"mandatory\" : \"text\" }, \"simple\" : { \"mandatory\" : \"text\" } }";
    doTest(json, Class2.class, true);
  }

  public void testSuccessEnclosed3() {
    String json = "{ \"mandatory\" : { \"mandatory\" : \"text\" }, \"simple\" : null }";
    doTest(json, Class2.class, true);
  }

  public void testFailureEnclosed1() {
    String json = "{ \"simple\" : { \"mandatory\" : \"text\" } }";
    doTest(json, Class2.class, false);
  }

  public void testFailureEnclosed2() {
    String json = "{ \"mandatory\" : null }";
    doTest(json, Class2.class, false);
  }

  public void testFailureEnclosed3() {
    String json = "{ \"mandatory\" : { \"simple\" : \"text\" } }";
    doTest(json, Class2.class, false);
  }

  public void testFailureEnclosed4() {
    String json = "{ \"mandatory\" : { \"mandatory\" : null, \"simple\" : \"text\" } }";
    doTest(json, Class2.class, false);
  }

  public void testFailureEnclosed5() {
    String json = "{ \"mandatory\" : { \"mandatory\" : \"text\" }, \"simple\" : { \"simple\" : \"text\" } }";
    doTest(json, Class2.class, false);
  }

  private static void doTest(@NotNull String json, @NotNull Class<?> clazz, boolean expectedSuccess) {
    Gson gson = TaskGsonUtil.createDefaultBuilder().create();

    boolean success;
    try {
      Object result = gson.fromJson(json, clazz);
      success = true;
    }
    catch (JsonMandatoryException e) {
      success = false;
    }

    assertEquals(expectedSuccess, success);
  }

  @RestModel
  private static class Class1 {
    String simple;
    @Mandatory String mandatory;
  }

  @RestModel
  private static class Class2 {
    Class1 simple;
    @Mandatory Class1 mandatory;
  }
}
