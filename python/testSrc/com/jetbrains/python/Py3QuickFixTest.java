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
package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyMissingConstructorInspection;
import com.jetbrains.python.inspections.PyStatementEffectInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class Py3QuickFixTest extends PyTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PyTestCase.ourPy3Descriptor;
  }

  // PY-13685
  public void testReplacePrintEnd() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyStatementEffectInspection.class, PyBundle.message("QFIX.statement.effect"), true, true);
      }
    });
  }

  // PY-13685
  public void testReplacePrintComment() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyStatementEffectInspection.class, PyBundle.message("QFIX.statement.effect"), true, true);
      }
    });
  }

  // PY-13685
  public void testReplaceExecComment() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyStatementEffectInspection.class, PyBundle.message("QFIX.statement.effect"), true, true);
      }
    });
  }

  // PY-11561
  public void testAddCallSuperAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/";
  }

  private void doInspectionTest(@NotNull Class inspectionClass,
                                @NotNull String quickFixName,
                                boolean applyFix,
                                boolean available) {
    doInspectionTest(getTestName(false) + ".py", inspectionClass, quickFixName, applyFix, available);
  }

  protected void doInspectionTest(@TestDataFile @NonNls @NotNull String testFileName,
                                  @NotNull Class inspectionClass,
                                  @NonNls @NotNull String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    doInspectionTest(new String[]{testFileName}, inspectionClass, quickFixName, applyFix, available);
  }

  /**
   * Runs daemon passes and looks for given fix within infos.
   *
   * @param testFiles       names of files to participate; first is used for inspection and then for check by "_after".
   * @param inspectionClass what inspection to run
   * @param quickFixName    how the resulting fix should be named (the human-readable name users see)
   * @param applyFix        true if the fix needs to be applied
   * @param available       true if the fix should be available, false if it should be explicitly not available.
   * @throws Exception
   */
  protected void doInspectionTest(@NonNls @NotNull String[] testFiles,
                                  @NotNull Class inspectionClass,
                                  @NonNls @NotNull String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(quickFixName);
    if (available) {
      assertNotNull(intentionAction);
      if (applyFix) {
        myFixture.launchAction(intentionAction);

        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
      }
    }
    else {
      assertNull(intentionAction);
    }
  }

  // Turns "name.ext" to "name_insertion.ext"

  @NonNls
  private static String graftBeforeExt(String name, String insertion) {
    int dotpos = name.indexOf('.');
    if (dotpos < 0) dotpos = name.length();
    return name.substring(0, dotpos) + insertion + name.substring(dotpos, name.length());
  }
}
