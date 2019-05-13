package com.jetbrains.python.psi.impl;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringTest;
import org.jetbrains.annotations.NotNull;

/**
 * Tests {@link com.jetbrains.python.psi.impl.PyArgumentListImpl#addArgument(com.jetbrains.python.psi.PyExpression)}
 *
 * @author Ilya.Kazakevich
 */
public class PyArgumentListImplTest extends PyClassRefactoringTest {
  private PyElementGeneratorImpl myGenerator;
  private LanguageLevel myLanguagelevel;

  public PyArgumentListImplTest() {
    super("argumentList");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myGenerator = new PyElementGeneratorImpl(myFixture.getProject());
    myLanguagelevel = LanguageLevel.PYTHON34;
    setLanguageLevel(myLanguagelevel);
  }

  /**
   * Ensures new keyword argument is set into appropriate place
   */
  public void testAddKeyArgument() {
    final PyKeywordArgument classKeyword = myGenerator.createKeywordArgument(myLanguagelevel, "metaclass", "ABCMeta");
    final PyKeywordArgument functionKeyword = myGenerator.createKeywordArgument(myLanguagelevel, "new_param", "spam");


    doTest(classKeyword, functionKeyword);
  }

  /**
   * Ensures new param (NOT keyword argument) is set into appropriate place
   */
  public void testAddParam() {
    final PyExpression classParameter = myGenerator.createParameter("SuperClass");
    final PyExpression functionParameter = myGenerator.createParameter("new_param");


    doTest(classParameter, functionParameter);
  }

  /**
   * Adds expressions to the superclass list and to the function calls in file
   *
   * @param superClassExpression expressions to add to the list of superclasses to any class found on file
   * @param functionExpression   expressions to add to any function call found in file
   */
  private void doTest(@NotNull final PyExpression superClassExpression, @NotNull final PyExpression functionExpression) {
    configureMultiFile("addArgumentFile", "stub");
    myFixture.configureByFile(getMultiFileBaseName() + "/addArgumentFile.py");

    //TODO: newly created expressions has no indent info, it leads to errors in postprocessing formatting. Need to investigate.
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).disablePostprocessFormattingInside(
      () -> WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {


        for (final PyClass aClass : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PyClass.class)) {
          final PyArgumentList superClassExpressionList = aClass.getSuperClassExpressionList();
          assert superClassExpressionList != null : "Class has no expression list!";
          superClassExpressionList.addArgument(superClassExpression);
        }

        for (final PyCallExpression expression : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PyCallExpression.class)) {
          final PyArgumentList list = expression.getArgumentList();
          assert list != null : "Callable statement has no argument list?";
          list.addArgument(functionExpression);
        }
      }));
    myFixture.checkResultByFile(getMultiFileBaseName() + "/addArgumentFile.after.py");
  }
}
