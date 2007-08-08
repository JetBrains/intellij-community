/**
 * @author ven
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.makeStatic.MakeClassStaticProcessor;
import com.intellij.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.refactoring.makeStatic.Settings;
import com.intellij.refactoring.util.ParameterTablePanel;

import java.util.ArrayList;

public class MakeClassStaticTest extends CodeInsightTestCase {
  private static final String TEST_ROOT = "/refactoring/makeClassStatic/";

  public void testSimple() throws Exception { perform(); }

  public void testSimpleWithFields() throws Exception { performWithFields(); }

  public void testQualifiedThisInSibling() throws Exception { perform(); }

  public void testIDEADEV3247() throws Exception { perform(); }

  public void testIDEADEV11595() throws Exception { perform(); }

  public void testIDEADEV12762() throws Exception { perform(); }

  public void testRegularReference() throws Exception {
    perform();
  }

  private void perform() throws Exception {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;

    boolean addClassParameter = MakeStaticUtil.isParameterNeeded(aClass);

    new MakeClassStaticProcessor(
            myProject,
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null, null)).run();
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }

  private void performWithFields() throws Exception {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;
    final ArrayList<ParameterTablePanel.VariableData> parametersForFields = new ArrayList<ParameterTablePanel.VariableData>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(aClass, parametersForFields);

    new MakeClassStaticProcessor(
            myProject,
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new ParameterTablePanel.VariableData[parametersForFields.size()]))).run();
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }
}
