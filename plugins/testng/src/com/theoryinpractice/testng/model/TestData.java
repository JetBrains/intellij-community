/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.theoryinpractice.testng.model;

import com.intellij.execution.ExternalizablePath;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;

import java.util.*;

/**
 * @author Hani Suleiman
 */
public class TestData implements Cloneable
{
  public String SUITE_NAME;
  public String PACKAGE_NAME;
  public String MAIN_CLASS_NAME;
  public String METHOD_NAME;
  public String GROUP_NAME;
  public String TEST_OBJECT;
  public String VM_PARAMETERS;
  public String PARAMETERS;
  public String WORKING_DIRECTORY;
  public String OUTPUT_DIRECTORY;

  private Map<String, String> ENVS = new LinkedHashMap<>();
  public boolean PASS_PARENT_ENVS = true;

  public TestSearchScope.Wrapper TEST_SEARCH_SCOPE;
  public Map<String, String> TEST_PROPERTIES = new HashMap<>();
  public List<String> TEST_LISTENERS = new ArrayList<>();
  public boolean USE_DEFAULT_REPORTERS = false;
  public String PROPERTIES_FILE;
  private LinkedHashSet<String> myPatterns = new LinkedHashSet<>();
  private String myChangeList;

  public TestData() {
    TEST_OBJECT = TestType.CLASS.getType();
    TEST_SEARCH_SCOPE = new TestSearchScope.Wrapper();
  }

  public TestSearchScope getScope() {
    return TEST_SEARCH_SCOPE.getScope();
  }

  public void setScope(TestSearchScope testseachscope) {
    TEST_SEARCH_SCOPE.setScope(testseachscope);
  }

  public String getPackageName() {
    return PACKAGE_NAME == null ? "" : PACKAGE_NAME;
  }

  public String getGroupName() {
    return GROUP_NAME == null ? "" : GROUP_NAME;
  }

  public String getMethodName() {
    return METHOD_NAME == null ? "" : METHOD_NAME;
  }

  public String getSuiteName() {
    return SUITE_NAME == null ? "" : SUITE_NAME;
  }

  public String getPropertiesFile() {
    return PROPERTIES_FILE == null ? "" : PROPERTIES_FILE;
  }

  public String getOutputDirectory() {
    return OUTPUT_DIRECTORY == null ? "" : OUTPUT_DIRECTORY;
  }

  public void setVMParameters(String value) {
    VM_PARAMETERS = value;
  }

  public String getVMParameters() {
    return VM_PARAMETERS;
  }

  public void setProgramParameters(String value) {
    PARAMETERS = value;
  }

  public String getProgramParameters() {
    return PARAMETERS;
  }

  public void setWorkingDirectory(String value) {
    WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
  }

  public String getWorkingDirectory() {
    return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TestData)) {
      return false;
    } else {
      TestData data = (TestData) obj;
      return Comparing.equal(TEST_OBJECT, data.TEST_OBJECT)
          && Comparing.equal(getMainClassName(), data.getMainClassName())
          && Comparing.equal(getPackageName(), data.getPackageName())
          && Comparing.equal(getSuiteName(), data.getSuiteName())
          && Comparing.equal(getMethodName(), data.getMethodName())
          && Comparing.equal(WORKING_DIRECTORY, data.WORKING_DIRECTORY)
          && Comparing.equal(OUTPUT_DIRECTORY, data.OUTPUT_DIRECTORY)
          && Comparing.equal(VM_PARAMETERS, data.VM_PARAMETERS)
          && Comparing.equal(PARAMETERS, data.PARAMETERS)
          && Comparing.equal(myPatterns, data.myPatterns)
          && USE_DEFAULT_REPORTERS == data.USE_DEFAULT_REPORTERS;
    }
  }

  @Override
  public int hashCode() {
    return Comparing.hashcode(getMainClassName()) ^
        Comparing.hashcode(getMethodName()) ^
        Comparing.hashcode(getGroupName()) ^
        Comparing.hashcode(getSuiteName()) ^
        Comparing.hashcode(TEST_OBJECT) ^
        Comparing.hashcode(WORKING_DIRECTORY) ^
        Comparing.hashcode(OUTPUT_DIRECTORY) ^
        Comparing.hashcode(VM_PARAMETERS) ^
        Comparing.hashcode(PARAMETERS) ^
        Comparing.hashcode(USE_DEFAULT_REPORTERS) ^
        Comparing.hashcode(myPatterns);
  }

  @Override
  public TestData clone() {
    TestData data;
    try {
      data = (TestData)super.clone();
    }
    catch (CloneNotSupportedException e) {
      //can't happen right?
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      data = new TestData();
    }
    data.TEST_SEARCH_SCOPE = new TestSearchScope.Wrapper();

    data.TEST_PROPERTIES = new HashMap<>();
    data.TEST_PROPERTIES.putAll(TEST_PROPERTIES);

    data.TEST_LISTENERS = new ArrayList<>();
    data.TEST_LISTENERS.addAll(TEST_LISTENERS);

    data.USE_DEFAULT_REPORTERS = USE_DEFAULT_REPORTERS;
    data.ENVS = new LinkedHashMap<>(ENVS);
    data.myPatterns = new LinkedHashSet<>();
    data.myPatterns.addAll(myPatterns);
    data.setScope(getScope());
    return data;
  }

  public String getMainClassName() {
    return MAIN_CLASS_NAME == null ? "" : MAIN_CLASS_NAME;
  }

  public Module setTestMethod(Location<PsiMethod> location) {
    final PsiMethod method = location.getPsiElement();
    METHOD_NAME = method.getName();
    TEST_OBJECT = TestType.METHOD.getType();
    return setMainClass(location instanceof MethodLocation ? ((MethodLocation)location).getContainingClass() : method.getContainingClass());
  }

  public Module setPackage(PsiPackage pkg) {
    PACKAGE_NAME = pkg.getQualifiedName();
    return null;
  }

  public Module setMainClass(PsiClass psiclass) {
    MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(psiclass);
    PsiPackage psipackage = JUnitUtil.getContainingPackage(psiclass);
    PACKAGE_NAME = psipackage == null ? "" : psipackage.getQualifiedName();
    return JavaExecutionUtil.findModule(psiclass);
  }

  public Map<String, String> getEnvs() {
    return ENVS;
  }

  public void setEnvs(final Map<String, String> envs) {
    ENVS = envs;
  }

  public Set<String> getPatterns() {
    return myPatterns;
  }

  public void setPatterns(LinkedHashSet<String> set) {
    myPatterns = set;
  }

  public String getChangeList() {
    return myChangeList;
  }

  public void setChangeList(String changeList) {
    myChangeList = changeList;
  }
}
