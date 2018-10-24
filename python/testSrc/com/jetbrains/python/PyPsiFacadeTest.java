// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.impl.PyBuiltinCache;

import java.util.Collections;

public class PyPsiFacadeTest extends PyTestCase {
  public void testCreateClassByQNameDoesntDependOnExistingImports() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    runWithSourceRoots(Collections.singletonList(myFixture.findFileInTempDir("src")), () -> {
      myFixture.configureByFile("src/" + getTestName(false) + ".py");
      final PyPsiFacade facade = PyPsiFacade.getInstance(myFixture.getProject());
      assertNotNull(facade.createClassByQName("foo.bar.MyClass", myFixture.getFile()));
    });
  }

  public void testCreateClassByQNameCanResolveUnqualifiedNamesOfBuiltinClasses() {
    final PsiFile file = myFixture.configureByText("a.py", "");
    final PyPsiFacade facade = PyPsiFacade.getInstance(myFixture.getProject());
    final PyClass builtinInt = facade.createClassByQName("int", file);
    assertNotNull(builtinInt);
    assertTrue(PyBuiltinCache.getInstance(file).isBuiltin(builtinInt));
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/psiFacade/";
  }
}
