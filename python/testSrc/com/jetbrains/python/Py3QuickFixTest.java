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
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  // PY-15867
  public void testAddCallSuperKeywordOnlyParamInSuperInit() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }

  // PY-15867
  public void testAddCallSuperKeywordOnlyParamInInit() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }

  // PY-15867
  public void testAddCallSuperSingleStarParamInSuperInit() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }

  // PY-15867
  public void testAddCallSuperSingleStarParamInSuperInitAndVarargInInit() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }

  // PY-11561
  public void testAddCallSuperTypeAnnotationsPreserved() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }

  // PY-16036, PY-11561
  public void testAddCallSuperSelfNameAndAnnotationPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperNoRequiredKeywordOnlyParamAfterSingleStarInSuperInit() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }
  
  // PY-16421
  public void testAddCallSuperSingleStarParamPreserved() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }
  
    // PY-15867
  public void testAddCallSuperRequiredKeywordOnlyParamAfterSingleStarInSuperInitIsMerged() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
      }
    });
  }
  
  // PY-16428 
  public void testAddParameterNotAvailableInsideAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      public void run() {
        doInspectionTest(PyUnresolvedReferencesInspection.class, 
                         PyBundle.message("QFIX.unresolved.reference.add.param.$0", "unresolved"), false, false);
      }
    });
  }

  // PY-8991
  public void testRemoveUnicodePrefixFromGluedStringNodesWithSlash() {
    runWithLanguageLevel(LanguageLevel.PYTHON32, new Runnable() {
      public void run() {
        myFixture.configureByFile(getTestName(false) + ".py");
        myFixture.checkHighlighting(true, false, false);
        final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("INTN.remove.leading.$0", "U"));
        assertNotNull(intentionAction);
        myFixture.launchAction(intentionAction);
        myFixture.checkResultByFile(getTestName(false) + "_after.py");
      }
    });
  }

  // PY-8990
  public void testRemoveUnicodePrefixFromGluedStringNodesInParenthesis() {
    runWithLanguageLevel(LanguageLevel.PYTHON32, new Runnable() {
      public void run() {
        myFixture.configureByFile(getTestName(false) + ".py");
        myFixture.checkHighlighting(true, false, false);
        final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("INTN.remove.leading.$0", "U"));
        assertNotNull(intentionAction);
        myFixture.launchAction(intentionAction);
        myFixture.checkResultByFile(getTestName(false) + "_after.py");
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
  @SuppressWarnings("Duplicates")
  protected void doInspectionTest(@NonNls @NotNull String[] testFiles,
                                  @NotNull Class inspectionClass,
                                  @NonNls @NotNull String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, false);
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(quickFixName);
    if (available) {
      if (intentionActions.isEmpty()) {
        throw new AssertionError("Quickfix \"" + quickFixName + "\" is not available");
      }
      if (intentionActions.size() > 1) {
        throw new AssertionError("There are more than one quickfix with the name \"" + quickFixName + "\"");
      }
      if (applyFix) {
        myFixture.launchAction(intentionActions.get(0));
        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
      }
    }
    else {
      assertEmpty("Quick fix \"" + quickFixName + "\" should not be available", intentionActions);
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
