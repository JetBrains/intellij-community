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
package org.jetbrains.yaml.scalarConversion;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.yaml.YAMLParserDefinition;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class YAMLScalarConversionTest extends LightPlatformCodeInsightFixtureTestCase {
  
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/scalarConversion/data/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new YAMLParserDefinition();
  }

  public void testSimple() {
    doTest(YAMLPlainTextImpl.class);
  }

  public void testSimpleTwoLines() {
    doTest(YAMLPlainTextImpl.class);
  }

  private void doTest(Class<? extends YAMLScalar> ...unsupportedClasses) {
    final PsiFile file = myFixture.configureByFile("sampleDocument.yml");

    Collection<YAMLScalar> scalars = PsiTreeUtil.collectElementsOfType(file, YAMLScalar.class);
    assertEquals(5, scalars.size());

    final String text;
    try {
      text = FileUtil.loadFile(new File(getTestDataPath() + getTestName(true) + ".txt"));
    }
    catch (IOException e) {
      fail(e.toString());
      return;
    }

    for (YAMLScalar scalar : scalars) {
      boolean isUnsupported = ((Computable<Boolean>)() -> {
        for (Class<? extends YAMLScalar> aClass : unsupportedClasses) {
          if (aClass == scalar.getClass()) {
            return true;
          }
        }
        return false;
      }).compute();
      
      final ElementManipulator<YAMLScalar> manipulator = ElementManipulators.getManipulator(scalar);
      assertNotNull(manipulator);

      final YAMLScalar newElement = manipulator.handleContentChange(scalar, text);
      assertEquals(isUnsupported + ";" + newElement.getClass() + ";" + scalar.getClass(),
                   isUnsupported, newElement.getClass() != scalar.getClass());
      assertEquals("Failed at " + scalar.getClass() + ": ", text, newElement.getTextValue());
    }
  }
}
