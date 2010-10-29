package com.jetbrains.python.testing;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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

  private PythonUnitTestUtil() {
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
    return true;
  }

  public static boolean isTestCaseClass(@NotNull PyClass cls) {
    return isTestCaseClass(cls, PYTHON_TEST_QUALIFIED_CLASSES);
  }

  public static boolean isTestCaseClass(@NotNull PyClass cls, Set<String> testQualifiedNames) {
    for (PyClass ancestor : cls.iterateAncestors()) {
      if (ancestor == null) continue;

      String qName = ancestor.getQualifiedName();
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
