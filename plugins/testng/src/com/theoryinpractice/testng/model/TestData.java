// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.model;

import com.intellij.execution.ExternalizablePath;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.*;

/**
 * @author Hani Suleiman
 */
public class TestData implements Cloneable
{
  public String SUITE_NAME;
  public String PACKAGE_NAME;
  public @NlsSafe String MAIN_CLASS_NAME;
  public String METHOD_NAME;
  public @NlsSafe String GROUP_NAME;
  public String TEST_OBJECT;
  // should be private, but for now we use DefaultJDOMExternalizer, so, public
  public String VM_PARAMETERS = "-ea";
  public String PARAMETERS;
  public String WORKING_DIRECTORY = PathMacroUtil.MODULE_WORKING_DIR;
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

  public @NlsSafe String getPackageName() {
    return PACKAGE_NAME == null ? "" : PACKAGE_NAME;
  }

  public @NlsSafe String getGroupName() {
    return GROUP_NAME == null ? "" : GROUP_NAME;
  }

  public @NotNull @NlsSafe String getMethodName() {
    return METHOD_NAME == null ? "" : METHOD_NAME;
  }

  public @NotNull @NlsSafe String getSuiteName() {
    return SUITE_NAME == null ? "" : SUITE_NAME;
  }

  public String getPropertiesFile() {
    return PROPERTIES_FILE == null ? "" : PROPERTIES_FILE;
  }

  public String getOutputDirectory() {
    return OUTPUT_DIRECTORY == null ? "" : OUTPUT_DIRECTORY;
  }

  public void setVMParameters(@Nullable String value) {
    VM_PARAMETERS = StringUtil.nullize(value);
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
    WORKING_DIRECTORY = StringUtil.isEmptyOrSpaces(value) ? "" : FileUtilRt.toSystemIndependentName(value.trim());
  }

  public String getWorkingDirectory() {
    return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TestData data)) {
      return false;
    } else {
      return Objects.equals(TEST_OBJECT, data.TEST_OBJECT)
             && Objects.equals(getMainClassName(), data.getMainClassName())
             && Objects.equals(getPackageName(), data.getPackageName())
             && Objects.equals(getSuiteName(), data.getSuiteName())
             && Objects.equals(getMethodName(), data.getMethodName())
             && Objects.equals(WORKING_DIRECTORY, data.WORKING_DIRECTORY)
             && Objects.equals(OUTPUT_DIRECTORY, data.OUTPUT_DIRECTORY)
             && Objects.equals(VM_PARAMETERS, data.VM_PARAMETERS)
             && Objects.equals(PARAMETERS, data.PARAMETERS)
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
    PACKAGE_NAME = StringUtil.getPackageName(Objects.requireNonNull(psiclass.getQualifiedName()));
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
