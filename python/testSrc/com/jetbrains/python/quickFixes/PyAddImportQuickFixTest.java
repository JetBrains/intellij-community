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
package com.jetbrains.python.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyAddImportQuickFixTest extends PyQuickFixTestCase {

  // PY-19773
  public void testReexportedName() throws Exception {
    doMultiFileAutoImportTest("Import 'flask.request'");
  }

  public void testOsPathFunctions() throws Exception {
    doMultiFileAutoImportTest("Import", fix -> {
      final List<ImportCandidateHolder> candidates = fix.getCandidates();
      final List<String> names = ContainerUtil.map(candidates, c -> c.getPresentableText("join"));
      assertSameElements(names, "os.path.join()");
      return true;
    });
  }

  // PY-19975
  public void testCanonicalNamesFromHigherLevelPackage() {
    doMultiFileAutoImportTest("Import", fix -> {
      final List<ImportCandidateHolder> candidates = fix.getCandidates();
      final List<String> names = ContainerUtil.map(candidates, c -> c.getPresentableText("MyClass"));
      assertOrderedEquals(names, "bar.MyClass", "foo.MyClass");
      return true;
    });
  }
  
  // PY-22422
  public void testAddParenthesesAndTrailingCommaToUpdatedFromImport() {
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doMultiFileAutoImportTest("Import");
  }

  // PY-21563
  public void testCombineFromImportsForReferencesInTypeComment() {
    doMultiFileAutoImportTest("Import");
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix) {
    doMultiFileAutoImportTest(hintPrefix, null);
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix, @Nullable Processor<AutoImportQuickFix> checkQuickfix) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    final String entryPoint = "main";
    myFixture.configureByFile(entryPoint + ".py");
    myFixture.checkHighlighting(true, false, false);
    final List<IntentionAction> intentions = myFixture.filterAvailableIntentions(hintPrefix);
    final IntentionAction intention = ContainerUtil.find(intentions, action -> {
      return action instanceof QuickFixWrapper && ((QuickFixWrapper)action).getFix() instanceof AutoImportQuickFix;
    });
    assertNotNull("Auto import quick fix starting with '" + hintPrefix + "' wasn't found", intention);
    final AutoImportQuickFix quickfix = (AutoImportQuickFix)((QuickFixWrapper)intention).getFix();
    boolean applyFix = true;
    if (checkQuickfix != null) {
      applyFix = checkQuickfix.process(quickfix);
    }
    if (applyFix) {
      myFixture.launchAction(intention);
      myFixture.checkResultByFile(getTestName(true) + "/" + entryPoint + "_after.py", true);
    }
  }
}
