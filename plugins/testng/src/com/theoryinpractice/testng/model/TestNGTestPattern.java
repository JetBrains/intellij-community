/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TestNGTestPattern extends TestNGTestObject {
  private static final Logger LOG = Logger.getInstance(TestNGTestPattern.class);

  public TestNGTestPattern(TestNGConfiguration config) {
    super(config);
  }

  @Override
  public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes)
    throws CantRunException {
    final TestData data = myConfig.getPersistantData();
    final Set<String> patterns = data.getPatterns();
    fillTestObjects(classes, patterns, myConfig.getPersistantData().getScope(), myConfig, getSearchScope());
  }

  public static void fillTestObjects(final Map<PsiClass, Map<PsiMethod, List<String>>> classes,
                                     final Set<String> patterns,
                                     final TestSearchScope testSearchScope,
                                     final ModuleBasedConfiguration config,
                                     final GlobalSearchScope searchScope) throws CantRunException {
    for (final String pattern : patterns) {
      final String className;
      final String methodName;
      if (pattern.contains(",")) {
        methodName = StringUtil.getShortName(pattern, ',');
        className = StringUtil.getPackageName(pattern, ',');
      } else {
        className = pattern;
        methodName = null;
      }

      final PsiClass psiClass = ReadAction.compute(() -> ClassUtil
        .findPsiClass(PsiManager.getInstance(config.getProject()), className.replace('/', '.'), null, true, searchScope));
      if (psiClass != null) {
        final Boolean hasTest = ReadAction.compute(() -> TestNGUtil.hasTest(psiClass));
        if (hasTest) {
          if (StringUtil.isEmpty(methodName)) {
            calculateDependencies(null, classes, searchScope, psiClass);
          }
          else {
            collectTestMethods(classes, psiClass, methodName, searchScope);
          }
        } else {
          throw new CantRunException(TestngBundle.message("dialog.message.no.tests.found.in.class", className));
        }
      }
    }
    if (classes.size() != patterns.size()) {
      final List<Pattern> compilePatterns = new ArrayList<>();
      for (String p : patterns) {
        final Pattern compilePattern;
        try {
          compilePattern = Pattern.compile(p);
        }
        catch (PatternSyntaxException e) {
          continue;
        }
        compilePatterns.add(compilePattern);
      }
      final SourceScope sourceScope = testSearchScope.getSourceScope(config);
      TestClassFilter projectFilter =
        new TestClassFilter(sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.allScope(config.getProject()),
                            config.getProject(), true, true){
          @Override
          public boolean isAccepted(PsiClass psiClass) {
            if (super.isAccepted(psiClass)) {
              final String qualifiedName = ReadAction.compute(psiClass::getQualifiedName);
              LOG.assertTrue(qualifiedName != null);
              for (Pattern pattern : compilePatterns) {
                if (pattern.matcher(qualifiedName).matches()) return true;
              }
            }
            return false;
          }
        };
      calculateDependencies(null, classes, searchScope, TestNGUtil.getAllTestClasses(projectFilter, false));
      if (classes.size() == 0) {
        throw new CantRunException(TestngBundle.message("dialog.message.no.tests.found.in.for.patterns", StringUtil.join(patterns, " || ")));
      }
    }
  }

  @Override
  public String getGeneratedName() {
    final Set<String> patterns = myConfig.getPersistantData().getPatterns();
    final int size = patterns.size();
    if (size == 0) return TestngBundle.message("action.text.temp.suite");
    String firstPattern = ContainerUtil.getFirstItem(patterns);
    if (size == 1) {
      return firstPattern;
    }
    else {
      //noinspection DialogTitleCapitalization
      return TestRunnerBundle.message("test.config.first.pattern.and.few.more", firstPattern, size - 1);
    }
  }

  @Override
  public String getActionName() {
    return getGeneratedName();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final Set<String> patterns = myConfig.getPersistantData().getPatterns();
    if (patterns.isEmpty()) {
      throw new RuntimeConfigurationWarning(TestngBundle.message("testng.dialog.message.no.pattern.selected.warning"));
    }
  }
}
