/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.env.python.testing;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.ConfigurationTarget;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import com.jetbrains.python.testing.TestTargetType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Creates configurations for many different cases: packages, classes, files and folders. It checks then that configuration is ok.
 * @author Ilya.Kazakevich
 */
class CreateConfigurationMultipleCasesTask<T extends PyAbstractTestConfiguration> extends CreateConfigurationTestTask<T> {

  CreateConfigurationMultipleCasesTask(@NotNull final String testRunnerName,
                                       @NotNull final Class<T> expectedConfigurationType) {
    super(testRunnerName, expectedConfigurationType);
  }


  @NotNull
  @Override
  protected List<PsiElement> getPsiElementsToRightClickOn() {
    final List<PsiElement> result = new ArrayList<>();


    result.add(getDir("tests_package/package_test"));
    result.add(getFile("tests_package/package_test", "test_in_package.py"));
    result.add(getFile("tests_package/package_test", "test_in_package.py").findTopLevelClass("TestLogic"));

    result.add(getDir("tests_folder"));
    result.add(getFile("tests_folder", "test_lonely.py"));
    result.add(getFile("tests_folder", "test_lonely.py").findTopLevelClass("TestLonely"));
    result.add(getFile("tests_folder", "test_functions.py").findTopLevelFunction("test_test"));
    result.add(getFile("tests_folder", "test_functions.py").findTopLevelFunction("foo"));

    result.add(getFile("tests_folder/test-test", "test-foo.py").findTopLevelClass("TestDash"));


    return result;
  }

  @Override
  protected void checkConfiguration(@NotNull final T configuration, @NotNull final PsiElement element) {
    // When namespace packages are supported we can run tests in for of foo.spam.Bar even if foo is plain dir
    // otherwise we need to set "foo" as working dir and run spam.Bar.


    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    final ConfigurationTarget target = configuration.getTarget();
    final VirtualFile workingDirectory = fileSystem.refreshAndFindFileByPath(configuration.getWorkingDirectorySafe());
    Assert.assertNotNull("Working directory hasn't been set", workingDirectory);
    final VirtualFile projectRoot = myFixture.getTempDirFixture().getFile(".");
    assert projectRoot != null;

    assert element instanceof PsiNamedElement : "Unknown element " + element;
    final String elementName = ((PsiNamedElement)element).getName();
    assert elementName != null;

    if (element instanceof PyClass && elementName.endsWith("TestDash")) {
      Assert.assertThat("Bad target", configuration.getTarget().getTarget(), Matchers.endsWith("test-foo.TestDash"));
      Assert.assertThat("Bad directory", configuration.getWorkingDirectorySafe(), Matchers.endsWith("test-test"));
    }
    else if (element instanceof PsiDirectory && elementName.endsWith("package_test")) {
      Assert.assertEquals("Working directory for folder should be same as folder", target.asVirtualFile(), workingDirectory);
      Assert.assertThat("Bad target", configuration.getTarget().getTarget(), Matchers.endsWith("package_test"));
    }
    else if (element instanceof PyFile && elementName.endsWith("test_in_package.py")) {
      final VirtualFile targetFile = target.asVirtualFile();
      assert targetFile != null : "Failed to create virtual file for " + target;
      Assert.assertEquals("Working directory for file should be same as file's parent", targetFile.getParent(), workingDirectory);
      Assert.assertThat("Bad target", configuration.getTarget().getTarget(), Matchers.endsWith("test_in_package.py"));
    }
    else if (element instanceof PyClass && elementName.endsWith("TestLogic")) {
      final PsiFile targetFile = element.getContainingFile();
      Assert.assertEquals("Bad working dir for class", targetFile.getVirtualFile().getParent(), workingDirectory);
      Assert.assertEquals("Bad configuration for class", "test_in_package.TestLogic", target.getTarget());
    }
    else if (element instanceof PsiDirectory && elementName.endsWith("tests_folder")) {
      Assert.assertEquals("Bad configuration for no package folder", workingDirectory, target.asVirtualFile());
      Assert.assertThat("Bad target", configuration.getTarget().getTarget(), Matchers.endsWith("tests_folder"));
    }
    else if (element instanceof PyFile && elementName.endsWith("test_lonely.py")) {
      final VirtualFile targetFile = target.asVirtualFile();
      assert targetFile != null : "Failed to create virtual file for " + target;
      Assert.assertEquals("Bad configuration for no package file", targetFile.getParent(), workingDirectory);
      Assert.assertThat("Bad target", configuration.getTarget().getTarget(), Matchers.endsWith("test_lonely.py"));
    }
    else if (element instanceof PyClass && elementName.endsWith("TestLonely")) {
      Assert.assertEquals("Bad working directory for no package class", element.getContainingFile().getVirtualFile().getParent(),
                          workingDirectory);
      Assert.assertEquals("Bad configuration for class no package", "test_lonely.TestLonely", target.getTarget());
    }
    else if (element instanceof PyFunction && elementName.endsWith("test_test")) {
      Assert.assertEquals("Bad configuration target", "test_functions.test_test", target.getTarget());
    }
    else if (element instanceof PyFunction && elementName.endsWith("foo")) {
      Assert.assertEquals("non-test function should lead to level-based test", TestTargetType.PATH, target.getTargetType());
    }
    else {
      throw new AssertionError("Unexpected configuration " + configuration);
    }
  }

  @NotNull
  private PsiDirectory getDir(@NotNull final String dirName) {
    final PsiManager psiManager = PsiManager.getInstance(myFixture.getProject());
    final VirtualFile vfsDir = myFixture.getTempDirFixture().getFile(dirName);
    assert vfsDir != null : "No vfs " + dirName;
    final PsiDirectory psiDirectory = psiManager.findDirectory(vfsDir);
    assert psiDirectory != null : "No psi " + dirName;
    return psiDirectory;
  }

  @NotNull
  private PyFile getFile(@NotNull final String dirName, @NotNull final String fileName) {
    final PsiFile psiFile = getDir(dirName).findFile(fileName);
    assert psiFile instanceof PyFile : "Bad file " + psiFile;
    return (PyFile)psiFile;
  }
}
