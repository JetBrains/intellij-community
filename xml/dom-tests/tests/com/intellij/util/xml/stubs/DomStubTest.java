/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.xml.stubs;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.impl.DomManagerImpl;

/**
 * @author Dmitry Avdeev
 *         Date: 8/8/12
 */
public abstract class DomStubTest extends LightPlatformCodeInsightFixtureTestCase {

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public DomStubTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  private static final DomFileDescription<Foo> DOM_FILE_DESCRIPTION = new DomFileDescription<Foo>(Foo.class, "foo") {
    @Override
    public boolean hasStubs() {
      return true;
    }
  };

  @Override
  protected boolean isCommunity() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((DomManagerImpl)DomManager.getDomManager(getProject())).registerFileDescription(DOM_FILE_DESCRIPTION, getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return "/xml/dom-tests/testData/stubs";
  }
}
