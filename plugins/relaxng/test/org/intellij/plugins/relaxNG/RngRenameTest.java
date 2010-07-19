/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 22.08.2007
 */
public class RngRenameTest extends HighlightingTestBase {
  protected CodeInsightTestFixture createFixture(IdeaTestFixtureFactory factory) {
    return createContentFixture(factory);
  }

  public String getTestDataPath() {
    return "rename/rng";
  }

  public void testRenameRef1() throws Throwable {
    doTestRename("rename-ref-1", "start");
  }

  private void doTestRename(String name, String newName) throws Throwable {
    doTestRename(name, "rng", newName);
  }
}