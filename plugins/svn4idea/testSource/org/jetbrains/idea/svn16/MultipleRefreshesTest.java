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
package org.jetbrains.idea.svn16;

import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 12/6/11
 * Time: 3:27 PM
 * To change this template use File | Settings | File Templates.
 */
@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class MultipleRefreshesTest {

  private SvnChangesCorrectlyRefreshedTest myTest;

  @Before
  public void setUp() throws Exception {
    myTest.setUp();
  }

  @After
  public void tearDown() throws Exception {
    myTest.tearDown();
  }

  @Parameterized.Parameters
  public static List<Object[]> data() {
    final ArrayList<Object[]> result = new ArrayList<>(10);
    for (int i = 0; i < 100; i++) {
      result.add(new Object[]{i});
    }
    return result;
  }

  public MultipleRefreshesTest(int cnt) {
    ChangeListManagerImpl.LOG.info("TEST " + cnt);
    myTest = new SvnChangesCorrectlyRefreshedTest();
  }

  @org.junit.Test
  public void testName() throws Exception {
    myTest.testModificationAndAfterRevert();
  }
}
