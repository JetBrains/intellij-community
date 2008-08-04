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
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.annotations.NonNls;

public class RenameFieldTest extends CodeInsightTestCase {
  private LanguageLevel myPreviousLanguageLevel;

  protected void setUp() throws Exception {
    super.setUp();
    myPreviousLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myPreviousLanguageLevel);
    super.tearDown();
  }

  protected void doTest(@NonNls String newName, @NonNls String ext) throws Exception {
    String suffix = getTestName(false);
    configureByFile("/refactoring/renameField/before" + suffix + "." + ext);
    perform(newName);
    checkResultByFile("/refactoring/renameField/after" + suffix + "." + ext);
  }

  public void testSimpleFieldRenaming() throws Exception {
    doTest("myNewField", "java");
  }

  public void testCollisionsInMethod() throws Exception {
    doTest("newFieldName", "java");
  }

  public void testCollisionsInMethodOfSubClass() throws Exception {
    doTest("newFieldName", "java");
  }

  public void testCollisionsRenamingFieldWithSetter() throws Exception {
    doTest("utm", "java");
  }

  public void testHidesOuter() throws Exception {
    doTest("x", "java");
  }

  public void testEnumConstantWithConstructor() throws Exception {
    doTest("newName", "java");
  }

  public void testEnumConstantWithInitializer() throws Exception {  // IDEADEV-28840
    doTest("AAA", "java");
  }

  protected void perform(String newName) {
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase
      .ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);

    new RenameProcessor(myProject, element, newName, false, false).run();
  }
}
