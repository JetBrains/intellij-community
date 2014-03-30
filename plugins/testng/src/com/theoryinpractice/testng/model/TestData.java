/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.theoryinpractice.testng.model;

import com.intellij.execution.ExternalizablePath;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

/**
 * @author Hani Suleiman Date: Jul 20, 2005 Time: 1:11:01 PM
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
  public String ANNOTATION_TYPE;

  public String ENV_VARIABLES;
  private Map<String, String> ENVS = new LinkedHashMap<String, String>();
  public boolean PASS_PARENT_ENVS = true;

  public TestSearchScope.Wrapper TEST_SEARCH_SCOPE;
  public Map<String, String> TEST_PROPERTIES = new HashMap<String, String>();
  public List<String> TEST_LISTENERS = new ArrayList<String>();
  public boolean USE_DEFAULT_REPORTERS = false;
  public String PROPERTIES_FILE;
  private Set<String> myPatterns = new HashSet<String>();

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

  public String getWorkingDirectory(Project project) {
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
  public Object clone() throws CloneNotSupportedException {
    TestData data = (TestData) super.clone();
    data.TEST_SEARCH_SCOPE = new TestSearchScope.Wrapper();

    data.TEST_PROPERTIES = new HashMap<String, String>();
    data.TEST_PROPERTIES.putAll(TEST_PROPERTIES);

    data.TEST_LISTENERS = new ArrayList<String>();
    data.TEST_LISTENERS.addAll(TEST_LISTENERS);

    data.USE_DEFAULT_REPORTERS = USE_DEFAULT_REPORTERS;
    data.ENVS = new LinkedHashMap<String, String>(ENVS);
    data.myPatterns = new HashSet<String>();
    data.myPatterns.addAll(myPatterns);
    data.setScope(getScope());
    return data;
  }

  public String getGeneratedName(JavaRunConfigurationModule runconfigurationmodule) {
    if (TestType.PACKAGE.getType().equals(TEST_OBJECT)) if (getPackageName().length() == 0) return "<default>";
    else return getPackageName();
    String name = JavaExecutionUtil.getPresentableClassName(getMainClassName());
    if (TestType.METHOD.getType().equals(TEST_OBJECT)) {
      return name + '.' + getMethodName();
    }
    else if (TestType.SUITE.getType().equals(TEST_OBJECT)) {
      return getSuiteName();
    }
    else {
      if (TestType.PATTERN.getType().equals(TEST_OBJECT)) {
        final int size = myPatterns.size();
        if (size == 0) return "Temp suite";
        return StringUtil.getShortName(myPatterns.iterator().next()) + (size > 1 ? " and " + (size - 1) + " more" : "");
      }
      return name;
    }
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

  public boolean isConfiguredByElement(PsiElement element) {
    if (TEST_OBJECT.equals(TestType.PACKAGE.getType())) {
      if (element instanceof PsiPackage) {
        return Comparing.strEqual(PACKAGE_NAME, ((PsiPackage) element).getQualifiedName());
      } else if (element instanceof PsiDirectory) {
        final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
        return psiPackage != null && Comparing.strEqual(PACKAGE_NAME, psiPackage.getQualifiedName());
      }
    }

    element = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
    if (element instanceof PsiMethod && TEST_OBJECT.equals(TestType.METHOD.getType())) {
      final PsiClass aClass = ((PsiMethod) element).getContainingClass();
      return aClass != null &&
             Comparing.strEqual(MAIN_CLASS_NAME, JavaExecutionUtil.getRuntimeQualifiedName(aClass)) &&
          Comparing.strEqual(METHOD_NAME, ((PsiMethod) element).getName());
    } else if (element instanceof PsiClass && TEST_OBJECT.equals(TestType.CLASS.getType())) {
      return Comparing.strEqual(MAIN_CLASS_NAME, JavaExecutionUtil.getRuntimeQualifiedName((PsiClass) element));
    }
    return false;
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

  public void setPatterns(Set<String> set) {
    myPatterns = set;
  }
}
