/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;

/**
 * User : ktisha
 */
public class PyConvertMethodToPropertyIntentionTest extends PyIntentionTestCase {

  public void testParamList() throws Exception {
    doNegativeTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testSimple() throws Exception {
    doIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testProperty() throws Exception {
    doNegativeTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testEmptyReturn() throws Exception {
    doNegativeTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testYield() throws Exception {
    doIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testNoReturn() throws Exception {
    doNegativeTest(PyBundle.message("INTN.convert.method.to.property"));
  }

}