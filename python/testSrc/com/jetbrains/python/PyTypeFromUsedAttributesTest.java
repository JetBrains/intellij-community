package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeInferenceFromUsedAttributesUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyTypeFromUsedAttributesTest extends PyTestCase {

  public void testCollectAttributeOfParameter() {
    doTestUsedAttributes("def func(x):\n" +
                         "    if x.baz():\n" +
                         "        x.foo = x.bar",
                         "foo", "bar", "baz");
  }

  public void testCollectAttributesInOuterScopes() {
    doTestUsedAttributes("x = undefined()" +
                         "x.quux\n" +
                         "def func2():\n" +
                         "    x.bar\n" +
                         "    def nested():\n" +
                         "        x.baz",
                         "quux", "baz", "bar");
  }

  public void testIgnoreAttributesOfParameterInOtherFunctions() {
    doTestUsedAttributes("def func1(x):\n" +
                         "    x.foo\n" +
                         "    \n" +
                         "def func2(x):\n" +
                         "    x.bar",
                         "bar");
  }


  public void testCollectSpecialMethodNames() {
    doTestUsedAttributes("x = undefined()\n" +
                         "x[0] = x[...]\n" +
                         "x + 42",
                         "__getitem__", "__setitem__", "__add__");
  }

  public void testOnlyBaseClassesRetained() {
    doTestType("class Base(object):\n" +
               "    attr_a = None\n" +
               "    attr_b = None\n" +
               "\n" +
               "class C2(Base):\n" +
               "    pass\n" +
               "\n" +
               "class C3(Base):\n" +
               "    attr_a = None\n" +
               "\n" +
               "class C4(Base):\n" +
               "    attr_a = None\n" +
               "    attr_b = None\n" +
               "\n" +
               "x = undefined()\n" +
               "x.attr_a\n" +
               "x.attr_b",
               "Base | unknown");
  }

  public void testDiamondHierarchyBottom() {
    doTestType("class D(object):\n" +
               "    pass\n" +
               "class B(D):\n" +
               "    pass\n" +
               "class C(D):\n" +
               "    pass\n" +
               "class A(B, C):\n" +
               "    foo = None\n" +
               "    bar = None\n" +
               "\n" +
               "def func(x):\n" +
               "    x.foo\n" +
               "    x.bar",
               "A | unknown");
  }

  public void testDiamondHierarchySiblings() {
    doTestType("class D(object):\n" +
               "    bar = None\n" +
               "class B(D):\n" +
               "    foo = None\n" +
               "class C(D):\n" +
               "    foo = None\n" +
               "    bar = None\n" +
               "class A(B, C):\n" +
               "    foo = None\n" +
               "    bar = None\n" +
               "\n" +
               "def func(x):\n" +
               "    x.foo()\n" +
               "    x.bar()\n",
               "B | C | unknown");
  }

  public void testDiamondHierarchyTop() {
    doTestType("class D(object):\n" +
               "    foo = None\n" +
               "    bar = None\n" +
               "\n" +
               "class B(D):\n" +
               "    foo = None\n" +
               "    bar = None\n" +
               "\n" +
               "class C(D):\n" +
               "    foo = None\n" +
               "\n" +
               "class A(B, C):\n" +
               "    foo = None\n" +
               "    bar = None\n" +
               "\n" +
               "def func(x):\n" +
               "    x.foo()\n" +
               "    x.bar()",
               "D | unknown");
  }

  public void testDiamondHierarchyLeft() {
    doTestType("class D(object):\n" +
               "    foo = None\n" +
               "class B(D):\n" +
               "    bar = None\n" +
               "class C(D):\n" +
               "    pass\n" +
               "class A(B, C):\n" +
               "    foo = None\n" +
               "    bar = None\n" +
               "def func(x):\n" +
               "    x.foo()\n" +
               "    x.bar()",
               "B | unknown");
  }

  public void testBuiltinTypes() {
    doTestType("def func(x):\n" +
               "    x.upper()\n" +
               "    x.decode()",
               "bytearray | str | unicode | unknown");

    doTestType("def func(x):\n" +
               "    x.pop() and x.update()",
               "dict | set | MutableMapping | unknown");
  }

  public void testFunctionType() {
    doTestType("class A:\n" +
               "    def method_a(self):\n" +
               "        pass\n" +
               "class B:\n" +
               "    def method_b(self):\n" +
               "        pass\n" +
               "def func(a, b):\n" +
               "    a.method_a\n" +
               "    b.method_b\n" +
               "    return a\n" +
               "x = func\n" +
               "x" ,
               "(a: A | unknown, b: B | unknown) -> A | unknown");
  }

  public void testFastInferenceForObjectAttributes() {
    doTestType("x = undefined()\n" +
               "x.__init__(1)\n" +
               "x",
               "object | unknown");
  }

  public void testResultsOrdering() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    doTestType("class MySortable(object):\n" +
               "    def sort(self):\n" +
               "        pass\n" +
               "x = undefined()\n" +
               "x.sort()",
               "list | MySortable | OtherClassA | OtherClassB | unknown");
  }

  private void doTestType(@NotNull String text, @NotNull String expectedType) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyReferenceExpression referenceExpression = findLastReferenceByText("x");
    assertNotNull(referenceExpression);
    final TypeEvalContext context = TypeEvalContext.userInitiated(referenceExpression.getContainingFile()).withTracing();
    final PyType actual = context.getType(referenceExpression);
    final String actualType = PythonDocumentationProvider.getTypeName(actual, context);
    assertEquals(expectedType, actualType);
  }

  private void doTestUsedAttributes(@NotNull String text, @NotNull String... attributesExpected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyReferenceExpression referenceExpression = findLastReferenceByText("x");
    assertNotNull(referenceExpression);
    assertSameElements(PyTypeInferenceFromUsedAttributesUtil.collectUsedAttributes(referenceExpression), attributesExpected);
  }

  @Nullable
  private PyReferenceExpression findLastReferenceByText(@NotNull final String text) {
    final PsiElement[] elements = PsiTreeUtil.collectElements(myFixture.getFile(), new PsiElementFilter() {
      @Override
      public boolean isAccepted(PsiElement element) {
        return element instanceof PyReferenceExpression && element.getText().equals(text);
      }
    });
    return (PyReferenceExpression)ArrayUtil.getLastElement(elements);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/typesFromAttributes/";
  }
}
