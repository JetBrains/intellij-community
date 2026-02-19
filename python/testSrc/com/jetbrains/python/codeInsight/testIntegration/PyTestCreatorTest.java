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
package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.testing.PyTestFactory;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.TestRunnerService;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Checks how test classes are created
 *
 * @author Ilya.Kazakevich
 */
public final class PyTestCreatorTest extends PyTestCase {

  public void testCreateUnitTest() {
    final PyTestCreationModel model = prepareAndCreateModel();
    TestRunnerService testRunnerService = TestRunnerService.getInstance(myFixture.getModule());
    testRunnerService.setProjectConfiguration(PythonTestConfigurationType.getInstance().getUnitTestFactory().getName());
    checkResult(model, "create_tst_class.expected_unittest.py");
  }

  public void testCreatePyTest() {
    final PyTestCreationModel model = prepareAndCreateModel();
    boolean p2k = LanguageLevel.forElement(myFixture.getFile()).isPython2();
    TestRunnerService testRunnerService = TestRunnerService.getInstance(myFixture.getModule());
    testRunnerService.setProjectConfiguration(PyTestFactory.id);

    checkResult(model, (p2k ? "create_tst_class.expected_pytest_2k.py" : "create_tst_class.expected_pytest_3k.py"));

    model.setClassName("");
    model.setFileName("tests_no_class.py");

    checkResult(model, "create_tst.expected.py");
  }

  @NotNull
  private PyTestCreationModel prepareAndCreateModel() {
    myFixture.configureByFile("/create_tests/create_tst.py");

    final VirtualFile[] roots = ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots();
    assert roots.length > 0 : "Empty roots for module " + myFixture.getModule();
    final VirtualFile root = roots[0];

    return new PyTestCreationModel("tests.py", root.getCanonicalPath(), "Spam", Arrays.asList("eggs", "eggs_and_ham"));
  }

  private void checkResult(@NotNull final PyTestCreationModel model, @NotNull final String fileName) {
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      final PsiFile file = PyTestCreator.generateTest(myFixture.getFile(), model).getContainingFile();
      myFixture.configureByText(file.getFileType(), file.getText());
      myFixture.checkResultByFile("/create_tests/" + fileName);
    });
  }
}
