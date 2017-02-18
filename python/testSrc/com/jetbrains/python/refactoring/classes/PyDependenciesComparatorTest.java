package com.jetbrains.python.refactoring.classes;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import org.hamcrest.Matchers;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
public class PyDependenciesComparatorTest extends PyTestCase {

  public void test() {
    myFixture.configureByFile("/refactoring/dependenciesTest.py");
    final PyClass clazz = getClassByName("Foo");

    @SuppressWarnings("ConstantConditions") // Can't be null (class has docstring)
    PsiElement docStringExpression = clazz.getDocStringExpression().getParent();
    PyFunction method = clazz.getMethods()[0];
    PsiElement classField = clazz.getClassAttributes().get(0).getParent();

    final List<PyStatement> elementList = new ArrayList<>();
    elementList.addAll(Arrays.asList(clazz.getStatementList().getStatements()));
    Collections.sort(elementList, PyDependenciesComparator.INSTANCE);

    Assert.assertThat("Members returned in wrong order", elementList, Matchers.contains(
      docStringExpression, classField, method
    ));



  }
}
