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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.propertyBased.*;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.Test;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Ilya.Kazakevich
 */
@SkipSlowTestLocally
public class PythonCodeInsightSanityTest extends PyEnvTestCase {

  /**
   * When this test fail please record its rechecking seed and create stable test providing it to {@link #runActivity(Pair)}
   */
  @Test
  public void testRandomActivity() {
    runActivity(null);
  }

  /**
   * To be fixed by {@link com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention} author @traff
   * Caused by: java.lang.StringIndexOutOfBoundsException: String index out of range: 36
   * at java.lang.String.substring(String.java:1963)
   * at com.intellij.codeInsight.template.TemplateBuilderImpl.buildTemplate(TemplateBuilderImpl.java:221)
   * at com.intellij.codeInsight.template.TemplateBuilderImpl.buildInlineTemplate(TemplateBuilderImpl.java:186)
   * at com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention.startTemplate(PyAnnotateTypesIntention.java:159)
   * at com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention.generateTypeCommentAnnotations(PyAnnotateTypesIntention.java:153)
   * at com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention.annotateTypes(PyAnnotateTypesIntention.java:84)
   */
  @Test
  public void testStringIndexOutOfRange() {
    runActivity(Pair.create(3683007015279203452L, 36));
  }

  @Test
  public void testReparse() {
    runSanityTest(pathAndFixture -> {
      final CodeInsightTestFixture fixture = pathAndFixture.second;
      PropertyChecker.checkScenarios(actionsOnPyFiles(MadTestingUtil::randomEditsWithReparseChecks, fixture,
                                              new File(fixture.getTestDataPath(), "sanity").getPath()));
    });
  }

  @NotNull
  private static Supplier<MadTestingAction> actionsOnPyFiles(@NotNull final Function<PsiFile, Generator<? extends MadTestingAction>> fileActions,
                                                              @NotNull final CodeInsightTestFixture fixture,
                                                              @NotNull final String testDataPath) {

    return MadTestingUtil.actionsOnFileContents(fixture, testDataPath, f -> f.getName().endsWith(".py"), fileActions);
  }

  private void runActivity(@Nullable final Pair<Long, Integer> seedToRepeat) {
    runSanityTest(pathAndFixture -> {
      final CodeInsightTestFixture fixture = pathAndFixture.second;
      MadTestingUtil.enableAllInspections(fixture.getProject(), fixture.getProject());
      Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
        file -> Generator.sampledFrom(new InvokeIntention(file, new IntentionPolicy()),
                                      new InvokeCompletion(file, new CompletionPolicy()),
                                      new StripTestDataMarkup(file),
                                      new DeleteRange(file));

      PropertyChecker.Parameters checker = PropertyChecker.customized();
      if (seedToRepeat != null) {
        checker = checker.recheckingIteration(seedToRepeat.first, seedToRepeat.second);
      }
      checker.checkScenarios(actionsOnPyFiles(fileActions, fixture, pathAndFixture.first));
    });
  }

  private void runSanityTest(@NotNull final Consumer<Pair<String, CodeInsightTestFixture>> test) {

    final String subfolder = "sanity";
    runPythonTest(new PyExecutionFixtureTestTask(subfolder) {
      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          final Pair<String, CodeInsightTestFixture> pathAndFixture =
            Pair.create(new File(myFixture.getTestDataPath(), subfolder).getPath(), myFixture);
          test.accept(pathAndFixture);
        });
      }
    });
  }
}
