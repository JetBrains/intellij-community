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

import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class RncParsingTest extends AbstractParsingTest {
  public RncParsingTest() {
    super("complex");
  }

  @Test
  public void testExternal() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testMain() {
   parsingFixture.get().doTest(true);
  }

  @Test
  public void testElements() {
   parsingFixture.get().doTest(true);
  }

  @Test
  public void testProperties() {
   parsingFixture.get().doTest(true);
  }

  @Test
  public void testDatatype() {
   parsingFixture.get().doTest(true);
  }

  @Test
  public void testRelaxNG() {
   parsingFixture.get().doTest(true);
  }

  @Test
  public void testDocbook() {
    parsingFixture.get().doTest(true);
  }
}