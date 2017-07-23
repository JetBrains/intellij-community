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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates configuration for each file provided to it
 *
 * @author Ilya.Kazakevich
 */
class CreateConfigurationByFileTask<T extends AbstractPythonTestRunConfiguration<?>> extends CreateConfigurationTestTask<T> {
  @NotNull
  private final String[] myFileNames;

  /**
   * @see CreateConfigurationTestTask#CreateConfigurationTestTask(String, Class)
   */
  CreateConfigurationByFileTask(@Nullable final String testRunnerName,
                                @NotNull final Class<T> expectedConfigurationType,
                                @NotNull final String...fileNames) {
    super(testRunnerName, expectedConfigurationType);
    myFileNames = fileNames.clone();
  }

  /**
   * @see CreateConfigurationTestTask#CreateConfigurationTestTask(String, Class)
   */
  CreateConfigurationByFileTask(@Nullable final String testRunnerName,
                                @NotNull final Class<T> expectedConfigurationType) {
    this(testRunnerName, expectedConfigurationType, "test_file.py", "test_class.py", "folder_with_word_tests_in_name");
  }

  @NotNull
  @Override
  protected final List<PsiElement> getPsiElementsToRightClickOn() {
    return Arrays.stream(myFileNames).map(this::getElementToRightClickOnByFile).collect(Collectors.toList());
  }

  /**
   * @param fileName file or folder name provided by class instantiator
   * @return element to right click on to generate test
   */
  @NotNull
  protected PsiElement getElementToRightClickOnByFile(@NotNull final String fileName) {
    final VirtualFile virtualFile = myFixture.getTempDirFixture().getFile(fileName);
    assert virtualFile != null : "Can't find " + fileName;

    // Configure context by folder in case of folder, or by element if file
    final PsiElement elementToRightClickOn;
    if (virtualFile.isDirectory()) {
      elementToRightClickOn = PsiDirectoryFactory.getInstance(getProject()).createDirectory(virtualFile);
    }
    else {
      myFixture.configureByFile(fileName);
      elementToRightClickOn = myFixture.getElementAtCaret();
    }
    return elementToRightClickOn;
  }




  static class CreateConfigurationTestAndRenameClassTask<T extends PyAbstractTestConfiguration> extends CreateConfigurationByFileTask<T> {
    CreateConfigurationTestAndRenameClassTask(@NotNull final String testRunnerName,
                                              @NotNull final Class<T> expectedConfigurationType) {
      super(testRunnerName, expectedConfigurationType, "test_class.py");
    }

    @Override
    protected void checkConfiguration(@NotNull T configuration, @NotNull PsiElement elementToRightClickOn) {
      super.checkConfiguration(configuration, elementToRightClickOn);
      Assert.assertThat("Wrong name generated", configuration.getName(), Matchers.containsString("TheTest"));
      Assert.assertThat("Bad target generated", configuration.getTarget().getTarget(), Matchers.endsWith("TheTest"));
      myFixture.renameElementAtCaret("FooTest");
      Assert.assertThat("Name not renamed", configuration.getName(), Matchers.containsString("FooTest"));
    }
  }

  static class CreateConfigurationTestAndRenameFolderTask<T extends PyAbstractTestConfiguration>
    extends CreateConfigurationByFileTask<T> {
    CreateConfigurationTestAndRenameFolderTask(@Nullable final String testRunnerName,
                                               @NotNull final Class<T> expectedConfigurationType) {
      super(testRunnerName, expectedConfigurationType, "folderWithTests");
    }

    @Override
    protected void checkConfiguration(@NotNull final T configuration, @NotNull PsiElement elementToRightClickOn) {
      super.checkConfiguration(configuration, elementToRightClickOn);
      Assert.assertThat("Wrong name generated", configuration.getName(), Matchers.containsString("folderWithTests"));
      Assert.assertThat("Bad target generated", configuration.getTarget().getTarget(), Matchers.containsString("folderWithTests"));

      final VirtualFile virtualFolder = myFixture.getTempDirFixture().getFile("folderWithTests");
      assert virtualFolder != null : "Can't find folder";
      final PsiDirectory psiFolder = PsiManager.getInstance(getProject()).findDirectory(virtualFolder);
      assert psiFolder != null : "No psi for folder found";
      myFixture.renameElement(psiFolder, "newFolder");
      Assert.assertThat("Name not renamed", configuration.getName(), Matchers.containsString("newFolder"));
      Assert.assertThat("Target not renamed", configuration.getTarget().getTarget(), Matchers.containsString("newFolder"));
    }
  }
}
