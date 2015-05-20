/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.spellchecker.inspection

import com.intellij.testFramework.PlatformTestUtil
/**
 * @author peter
 */
public class SpellcheckerPerformanceTest extends SpellcheckerInspectionTestCase {
  @Override
  protected void setUp() throws Exception {
    def start = System.currentTimeMillis()
    super.setUp()
    println("setUp took " + (System.currentTimeMillis() - start))
  }

  public void "test large text file with many typos"() {
    int typoCount = 50000
    String text = "aaaaaaaaa " * typoCount // about 0.5M

    def start = System.currentTimeMillis()
    def file = myFixture.addFileToProject("foo.txt", text).virtualFile
    println("creation took " + (System.currentTimeMillis() - start))

    start = System.currentTimeMillis()
    myFixture.configureFromExistingVirtualFile(file)
    println("configure took " + (System.currentTimeMillis() - start))

    myFixture.enableInspections(inspectionTools)

    start = System.currentTimeMillis()
    assertSize(typoCount, myFixture.doHighlighting())
    println("warmup took " + (System.currentTimeMillis() - start))

    PlatformTestUtil.assertTiming("highlighting too long", 1000) { assertSize(typoCount, myFixture.doHighlighting()) }
  }
  
}
