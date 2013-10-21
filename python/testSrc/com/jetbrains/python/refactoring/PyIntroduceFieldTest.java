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
package com.jetbrains.python.refactoring;

import com.intellij.testFramework.TestDataPath;
import com.intellij.util.Consumer;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.field.PyIntroduceFieldHandler;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceField/")
public class PyIntroduceFieldTest extends PyIntroduceTestCase {
  public void testMetaClass() {  // PY-1580
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.SAME_METHOD, false));
  }

  public void testInConstructor() {  // PY-1983
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR, false));
  }

  public void testVariableToField() {
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR, false));
  }
  
  public void testUniqueName() {  // PY-4409
    doTestSuggestions(PyExpression.class, "s1");
  }

  public void testPy4453() {
    doTestInplace(null);
  }

  public void testPy4414() {
    doTestInplace(null);
  }

  public void testPy4437() {
    doTestInplace(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR, true));
  }

  @Override
  protected IntroduceHandler createHandler() {
    return new PyIntroduceFieldHandler();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceField/";
  }

  private static class IntroduceOperationCustomizer implements Consumer<IntroduceOperation> {
    private final IntroduceHandler.InitPlace myPlace;
    private final boolean myInplace;

    private IntroduceOperationCustomizer(IntroduceHandler.InitPlace place, boolean inplace) {
      myPlace = place;
      myInplace = inplace;
    }

    @Override
    public void consume(IntroduceOperation operation) {
      if (myInplace) {
        operation.setInplaceInitPlace(myPlace);
      }
      else {
        operation.setInitPlace(myPlace);
      }
    }
  }
}
