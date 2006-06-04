/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.04.2002
 * Time: 12:58:09
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.makeStatic.MakeMethodStaticProcessor;
import com.intellij.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.refactoring.makeStatic.Settings;
import com.intellij.refactoring.util.ParameterTablePanel;

import java.util.ArrayList;

public class MakeMethodStaticTest extends CodeInsightTestCase {
  public void testEmptyMethod() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before1.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after1.java");
  }

  public void testUseStatic() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before2.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after2.java");
  }

  public void testUseField() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before3.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after3.java");
  }

  public void testIDEADEV2556() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before21.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/after21.java");
  }

  public void testUseFieldWithThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before4.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after4.java");
  }

  public void testUseFieldWithSuperEmptyExtends() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before5.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after5.java");
  }

  public void testUseFieldWithSuper() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before6.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after6.java");
  }

  public void testUseMethod() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before7.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after7.java");
  }

  public void testThisInsideAnonymous() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before8.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after8.java");
  }

  public void testUsageInSubclass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before9.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after9.java");
  }

  public void testGeneralUsageNoParam() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before10.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/after10-np.java");
  }

  public void testGeneralUsage() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before10.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after10.java");
  }

  public void testUsageInSubclassWithSuper() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before11.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after11.java");
  }

  public void testSuperUsageWithComplexSuperClass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before12.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after12.java");
  }

  public void testExplicitThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before13.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after13.java");
  }

  public void testQualifiedThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before14.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after14.java");
  }

  public void testSCR8043() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before15.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after15.java");
  }

  public void testJavadoc1() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before16.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after16.java");
  }

  public void testJavadoc2() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before17.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after17.java");
  }

  public void testGenericClass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before18.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after18.java");
  }

  public void testFieldWriting() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before19.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after19.java");
  }

  public void testQualifiedInnerClassCreation() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before20.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after20.java");
  }

  public void testQualifiedThisAdded() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before22.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after22.java");
  }

  private void perform(boolean addClassParameter) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;

    new MakeMethodStaticProcessor(
            myProject,
            method,
            new Settings(true, addClassParameter ? "anObject" : null, null)).run();
  }

  private void performWithFields() {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;
    final ArrayList<ParameterTablePanel.VariableData> parametersForFields = new ArrayList<ParameterTablePanel.VariableData>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(method, parametersForFields);

    new MakeMethodStaticProcessor(
            myProject,
            method,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new ParameterTablePanel.VariableData[parametersForFields.size()]))).run();
  }
}
