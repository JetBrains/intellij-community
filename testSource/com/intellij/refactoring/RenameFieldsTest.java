/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 04.06.2002
 * Time: 20:01:43
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;

public class RenameFieldsTest extends CodeInsightTestCase {
  public void testSimpleFieldRenaming() throws Exception {
    configureByFile("/refactoring/renameField/before01.java");
    perform("myNewField");
    checkResultByFile("/refactoring/renameField/after01.java");
  }

  public void testCollisionsInMethod() throws Exception {
    configureByFile("/refactoring/renameField/before02.java");
    perform("newFieldName");
    checkResultByFile("/refactoring/renameField/after02.java");
  }

  public void testCollisionsInMethodOfSubClass() throws Exception {
    configureByFile("/refactoring/renameField/before03.java");
    perform("newFieldName");
    checkResultByFile("/refactoring/renameField/after03.java");
  }

  protected void perform(String newName) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
                                                             TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    new RenameProcessor(myProject, element, newName, false, false, false).testRun();
  }
}
