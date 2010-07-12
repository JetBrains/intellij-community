package com.jetbrains.python;

import com.jetbrains.python.codeInsight.override.PyMethodMember;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyOverrideTest extends PyLightFixtureTestCase {
  private void doTest() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFile file = (PyFile) myFixture.getFile();
    List<PyClass> classes = file.getTopLevelClasses();
    PyFunction toOverride = classes.get(0).getMethods() [0];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), classes.get(1),
                                            Collections.singletonList(new PyMethodMember(toOverride)));
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testSimple() {
    doTest();
  }

  public void testClassmethod() {
    doTest();
  }

  public void testNewStyle() {
    doTest();
  }

  public void testPy3k() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON31);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }
}
