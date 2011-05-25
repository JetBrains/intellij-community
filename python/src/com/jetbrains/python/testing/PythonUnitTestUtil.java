package com.jetbrains.python.testing;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestUtil {
  public static final String TESTCASE_SETUP_NAME = "setUp";
  private static final HashSet<String> PYTHON_TEST_QUALIFIED_CLASSES = Sets.newHashSet("unittest.TestCase", "unittest.case.TestCase");
  private static final Pattern TEST_MATCH_PATTERN = Pattern.compile("(?:^|[\b_\\.%s-])[Tt]est");
  private static final String TESTCASE_METHOD_PREFIX = "test";

  private PythonUnitTestUtil() {
  }

  public static boolean isUnitTestCaseFunction(PyFunction function) {
    final String name = function.getName();
    if (name == null || !name.startsWith(TESTCASE_METHOD_PREFIX)) {
      return false;
    }

    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null || !isUnitTestCaseClass(containingClass, PYTHON_TEST_QUALIFIED_CLASSES)) {
      return false;
    }

    return true;
  }

  public static boolean isUnitTestCaseClass(PyClass cls) {
    return isUnitTestCaseClass(cls, PYTHON_TEST_QUALIFIED_CLASSES);
  }

  public static boolean isUnitTestFile(PyFile file) {
    if (!file.getName().startsWith("test")) return false;
    return true;
  }

  private static boolean isUnitTestCaseClass(PyClass cls, HashSet<String> testQualifiedNames) {
    for (PyClassRef ancestor : cls.iterateAncestors()) {
      if (ancestor == null) continue;

      String qName = ancestor.getQualifiedName();
      if (testQualifiedNames.contains(qName)) {
        return true;
      }
    }
    return false;
  }

  public static List<PyStatement> getTestCaseClassesFromFile(PyFile file) {
    return getTestCaseClassesFromFile(file, PYTHON_TEST_QUALIFIED_CLASSES);
  }

  public static List<PyStatement> getTestCaseClassesFromFile(PyFile file, Set<String> testQualifiedNames) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isTestCaseClass(cls, testQualifiedNames)) {
        result.add(cls);
      }
    }
    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isTestCaseFunction(cls, testQualifiedNames)) {
        result.add(cls);
      }
    }
    return result;
  }

  public static boolean isTestCaseFunction(PyFunction function) {
    return isTestCaseFunction(function, PYTHON_TEST_QUALIFIED_CLASSES);

  }

  public static boolean isTestCaseFunction(PyFunction function, Set<String> testQualifiedNames) {
    final String name = function.getName();
    if (name == null || !TEST_MATCH_PATTERN.matcher(name).find()) {
      return false;
    }
    if (function.getContainingClass() != null) {
      if (isTestCaseClass(function.getContainingClass())) return true;
    }
    boolean hasAssert = hasAssertOrYield(function.getStatementList());
    if (!hasAssert) return false;
    return true;
  }

  private static boolean hasAssertOrYield(PyStatementList list) {
    Stack<PsiElement> stack = new Stack<PsiElement>();
      if (list != null) {
        for (PyStatement st : list.getStatements()) {
          stack.push(st);
          while (!stack.isEmpty()) {
            PsiElement e = stack.pop();
            if (e instanceof PyAssertStatement || e instanceof PyYieldExpression) return true;
            for (PsiElement psiElement : e.getChildren()) {
              stack.push(psiElement);
            }
          }
        }
      }
    return false;
  }

  public static boolean isTestCaseClass(@NotNull PyClass cls) {
    return isTestCaseClass(cls, PYTHON_TEST_QUALIFIED_CLASSES);
  }

  public static boolean isTestCaseClass(@NotNull PyClass cls, Set<String> testQualifiedNames) {
    for (PyClassRef ancestor : cls.iterateAncestors()) {
      String qName = ancestor.getQualifiedName();
      if (qName == null) continue;
      if (testQualifiedNames.contains(qName)) {
        return true;
      }
      String clsName = cls.getQualifiedName();
      String[] names = clsName.split("\\.");
      clsName = names[names.length - 1];

      if (TEST_MATCH_PATTERN.matcher(clsName).find()) {
        return true;
      }
    }

    return false;
  }
}
