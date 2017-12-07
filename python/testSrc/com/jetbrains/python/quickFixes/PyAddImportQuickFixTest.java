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
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyAddImportQuickFixTest extends PyQuickFixTestCase {

  // PY-19773
  public void testReexportedName() {
    doMultiFileAutoImportTest("Import 'flask.request'");
  }

  public void testOsPathFunctions() {
    doMultiFileAutoImportTest("Import 'os.path.join()'");
  }

  // PY-19975
  public void testCanonicalNamesFromHigherLevelPackage() {
    doMultiFileAutoImportTest("Import this name");
  }
  
  // PY-22422
  public void testAddParenthesesAndTrailingCommaToUpdatedFromImport() {
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doMultiFileAutoImportTest("Import 'bar from module'");
  }

  // PY-21563
  public void testCombineFromImportsForReferencesInTypeComment() {
    doMultiFileAutoImportTest("Import this name");
  }

  // PY-25234
  public void testBinarySkeletonStdlibModule() {
    doMultiFileAutoImportTest("Import 'sys'");
  }

  // PY-25234
  public void testUserSkeletonStdlibModule() {
    doMultiFileAutoImportTest("Import 'alembic'");
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByFile("main.py");
    myFixture.checkHighlighting(true, false, false);

    final IntentionAction intention = myFixture.findSingleIntention(hintPrefix);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(true) + "/main_after.py", true);
  }
}
