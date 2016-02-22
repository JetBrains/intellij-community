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
package com.jetbrains.python;

import com.jetbrains.python.actions.PyQualifiedNameProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

/**
 * @author Mikhail Golubev
 */
public class PyQualifiedNameProviderTest extends PyTestCase {
  @Test
  public void testTopLevelFunctionReference() {
    myFixture.copyDirectoryToProject(getTestName(true) + "/a", "a");
    myFixture.configureByFile("a/b/c/module.py");
    assertEquals("a.b.c.module.func", getQualifiedNameOfElementUnderCaret());
  }

  @Nullable
  private String getQualifiedNameOfElementUnderCaret() {
    return new PyQualifiedNameProvider().getQualifiedName(myFixture.getElementAtCaret());
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/qualifiedName";
  }
}
