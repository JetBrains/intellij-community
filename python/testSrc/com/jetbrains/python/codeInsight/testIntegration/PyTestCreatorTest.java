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
import org.easymock.IMocksControl;

import java.util.Arrays;

import static org.easymock.EasyMock.createNiceControl;
import static org.easymock.EasyMock.expect;

/**
 * Checks how test classes are created
 *
 * @author Ilya.Kazakevich
 */
public final class PyTestCreatorTest extends PyTestCase {
  public void testCreateTest() {

    myFixture.configureByFile("/create_tests/create_tst.py");

    final VirtualFile[] roots = ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots();
    assert roots.length > 0 : "Empty roots for module " + myFixture.getModule();
    final VirtualFile root = roots[0];

    final IMocksControl mockControl = createNiceControl();
    final CreateTestDialog dialog = mockControl.createMock(CreateTestDialog.class);
    expect(dialog.getFileName()).andReturn("tests.py").anyTimes();
    expect(dialog.getClassName()).andReturn("Spam").anyTimes();
    // Target dir is first module source
    expect(dialog.getTargetDir()).andReturn(root.getCanonicalPath()).anyTimes();
    expect(dialog.getMethods()).andReturn(Arrays.asList("eggs", "eggs_and_ham")).anyTimes();
    mockControl.replay();


    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      final PsiFile file = PyTestCreator.generateTest(myFixture.getProject(), dialog).getContainingFile();
      myFixture.configureByText(file.getFileType(), file.getText());
      myFixture.checkResultByFile("/create_tests/create_tst.expected.py");
    });
  }
}
