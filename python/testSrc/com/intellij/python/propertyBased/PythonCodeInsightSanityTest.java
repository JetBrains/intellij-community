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
package com.intellij.python.propertyBased;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.propertyBased.*;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import jetCheck.Generator;
import jetCheck.PropertyChecker;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Ilya.Kazakevich
 */
@SkipSlowTestLocally
public class PythonCodeInsightSanityTest extends PyEnvTestCase {

  @Test
  public void testRandomActivity() {
    runSanityTest(pathAndFixture -> {
      final CodeInsightTestFixture fixture = pathAndFixture.second;
      MadTestingUtil.enableAllInspections(fixture.getProject(), fixture.getProject());
      Function<PsiFile, Generator<? extends MadTestingAction>> fileActions = file ->
        Generator.anyOf(InvokeIntention.randomIntentions(file, new IntentionPolicy()),
                        InvokeCompletion.completions(file, new CompletionPolicy()),
                        Generator.constant(new StripTestDataMarkup(file)),
                        DeleteRange.psiRangeDeletions(file));

      PropertyChecker.forAll(actionsOnPyFiles(fileActions, fixture, pathAndFixture.first))
        .shouldHold(FileWithActions::runActions);
    });
  }

  @Test
  public void testReparse() {
    runSanityTest(pathAndFixture -> {
      final CodeInsightTestFixture fixture = pathAndFixture.second;
      PropertyChecker.forAll(actionsOnPyFiles(MadTestingUtil::randomEditsWithReparseChecks, fixture,
                                              new File(fixture.getTestDataPath(), "sanity").getPath()))
        .shouldHold(FileWithActions::runActions);
    });
  }

  @NotNull
  private static Generator<FileWithActions> actionsOnPyFiles(@NotNull final Function<PsiFile, Generator<? extends MadTestingAction>> fileActions,
                                                             @NotNull final CodeInsightTestFixture fixture,
                                                             @NotNull final String testDataPath) {

    return MadTestingUtil.actionsOnFileContents(fixture, testDataPath, f -> f.getName().endsWith(".py"), fileActions);
  }

  private void runSanityTest(@NotNull final Consumer<Pair<String, CodeInsightTestFixture>> test) {

    final String subfolder = "sanity";
    runTest(new PyExecutionFixtureTestTask(subfolder) {
      @Override
      public void runTestOn(String sdkHome) throws Exception {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          final Pair<String, CodeInsightTestFixture> pathAndFixture =
            Pair.create(new File(myFixture.getTestDataPath(), subfolder).getPath(), myFixture);
          test.accept(pathAndFixture);
        });
      }
    }, getTestName(true));
  }
}
