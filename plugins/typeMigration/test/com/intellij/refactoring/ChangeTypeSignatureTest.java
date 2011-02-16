/*
 * User: anna
 * Date: 18-Mar-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class ChangeTypeSignatureTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/typeMigration/testData";
  }

  private void doTest(boolean success, String migrationTypeText) throws Exception {
    String dataPath = "/refactoring/changeTypeSignature/";
    configureByFile(dataPath + getTestName(false) + ".java");
    final PsiFile file = getFile();
    final PsiElement element = file.findElementAt(getEditor().getCaretModel().getOffset());
    final PsiReferenceParameterList parameterList = PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class);
    assert parameterList != null;
    final PsiClass superClass = (PsiClass)((PsiJavaCodeReferenceElement)parameterList.getParent()).resolve();
    assert superClass != null;

    PsiType migrationType = getJavaFacade().getElementFactory().createTypeFromText(migrationTypeText, null);

    try {
      final TypeMigrationRules rules = new TypeMigrationRules(TypeMigrationLabeler.getElementType(parameterList));
      rules.setMigrationRootType(PsiSubstitutor.EMPTY.put(superClass.getTypeParameters()[0], migrationType).substitute(new PsiImmediateClassType(superClass, PsiSubstitutor.EMPTY)));
      rules.setBoundScope(GlobalSearchScope.projectScope(getProject()));
      new TypeMigrationProcessor(getProject(), parameterList, rules).run();
      if (success) {
        checkResultByFile(dataPath + getTestName(false) + ".java.after");
      } else {
        fail("Conflicts should be detected");
      }
    }
    catch (RuntimeException e) {
      if (success) {
        e.printStackTrace();
        fail("Conflicts should not appear");
      }
    }
  }

  private void doTest(boolean success) throws Exception {
    doTest(success, "java.lang.Object");
  }

  public void testListTypeArguments() throws Exception {
    doTest(true);
  }

  public void testFieldUsage() throws Exception {
    doTest(true);
  }

  public void testFieldUsage1() throws Exception {
    doTest(true);
  }

  public void testReturnType() throws Exception {
    doTest(true);
  }

  public void testReturnType1() throws Exception {
    doTest(true);
  }

  public void testReturnType2() throws Exception {
    doTest(true);
  }

  public void testPassedParameter() throws Exception {
    doTest(true);
  }

  public void testPassedParameter1() throws Exception {
    doTest(true, "java.lang.Integer");
  }

  public void testPassedParameter2() throws Exception {
    doTest(true);
  }

  public void testUsedInSuper() throws Exception {
    doTest(true);
  }

  public void testCompositeReturnType() throws Exception {
    doTest(true);
  }

  public void testTypeHierarchy() throws Exception {
    doTest(true);
  }

  public void testTypeHierarchy1() throws Exception {
    doTest(true);
  }

  public void testTypeHierarchy2() throws Exception {
    doTest(true);
  }

  public void testTypeHierarchyFieldUsage() throws Exception {
    doTest(true);
  }

  public void testTypeHierarchyFieldUsageConflict() throws Exception {
    doTest(true);
  }

  public void testParameterMigration() throws Exception {
    doTest(true);
  }

  public void testParameterMigration1() throws Exception {
    doTest(true, "java.lang.Integer");
  }

  public void testParameterMigration2() throws Exception {
    doTest(true, "java.lang.Integer");
  }

  public void testFieldTypeMigration() throws Exception {
    doTest(true, "java.lang.String");
  }

  public void testMethodReturnTypeMigration() throws Exception {
    doTest(true, "java.lang.Integer");
  }

}