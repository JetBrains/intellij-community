/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class JUnitConvertTool extends BaseJavaLocalInspectionTool {

  private static final Logger LOG = Logger.getInstance("TestNG QuickFix");
  private static final String DISPLAY_NAME = "Convert JUnit Tests to TestNG";
  public static final String QUICKFIX_NAME = "Convert TestCase to TestNG";

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "TestNG";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "JUnitTestNG";
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (TestNGUtil.inheritsJUnitTestCase(psiClass) || TestNGUtil.containsJunitAnnotions(psiClass)) {
      final PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      ProblemDescriptor descriptor = manager.createProblemDescriptor(nameIdentifier != null ? nameIdentifier : psiClass, "TestCase can be converted to TestNG",
                                                                     new JUnitConverterQuickFix(),
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
      return new ProblemDescriptor[]{descriptor};
    }
    return null;
  }

  public static class JUnitConverterQuickFix implements LocalQuickFix {

    @NotNull
    public String getName() {
      return QUICKFIX_NAME;
    }

     @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (!CodeInsightUtilBase.preparePsiElementForWrite(descriptor.getPsiElement())) return;
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
      if (!TestNGUtil.checkTestNGInClasspath(psiClass)) return;
      try {
        final PsiManager manager = PsiManager.getInstance(project);
        final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final PsiJavaFile javaFile = (PsiJavaFile)psiClass.getContainingFile();

        for (PsiMethod method : psiClass.getMethods()) {
          if (method.isConstructor()) {
            convertJUnitConstructor(method);
          }
          else {
            if (!javaFile.getLanguageLevel().hasEnumKeywordAndAutoboxing()) {
              addMethodJavadoc(factory, method);
            }
            else {
              if (TestNGUtil.containsJunitAnnotions(method)) {
                convertJunitAnnotions(factory, method);
              }
              addMethodAnnotations(factory, method);
            }
          }

          final PsiMethodCallExpression[] methodCalls = getTestCaseCalls(method);
          for (PsiMethodCallExpression methodCall : methodCalls) {
            PsiMethod assertMethod = methodCall.resolveMethod();
            if (assertMethod == null) {
              continue;
            }
            PsiAssertStatement assertStatement = null;
            @NonNls String methodName = assertMethod.getName();
            PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
            final PsiStatement methodCallStatement = PsiTreeUtil.getParentOfType(methodCall, PsiStatement.class);
            LOG.assertTrue(methodCallStatement != null);
            if ("assertTrue".equals(methodName) || "assertFalse".equals(methodName)) {
              if (expressions.length == 1) {
                assertStatement = createAssert(factory, null, methodCall);
                final PsiExpression assertCondition = assertStatement.getAssertCondition();
                LOG.assertTrue(assertCondition != null);
                assertCondition.replace(expressions[0]);
              }
              else if (expressions.length == 2) {
                assertStatement = createAssert(factory, expressions[0], methodCall);
                final PsiExpression assertCondition = assertStatement.getAssertCondition();
                LOG.assertTrue(assertCondition != null);
                assertCondition.replace(expressions[1]);
              }

              if ("assertFalse".equals(methodName) && assertStatement != null) {
                PsiExpression assertCondition = assertStatement.getAssertCondition();
                LOG.assertTrue(assertCondition != null);
                assertCondition.replace(factory.createExpressionFromText("!(" + assertCondition.getText() + ')',
                                                                         PsiTreeUtil.getParentOfType(assertCondition,
                                                                                                     PsiMethodCallExpression.class)));
              }
            }
            else if ("assertNull".equals(methodName) || "assertNotNull".equals(methodName)) {
              String operator = "assertNull".equals(methodName) ? "==" : "!=";
              if (expressions.length == 1) {
                assertStatement = createAssert(factory, null, methodCall);
                PsiExpression expression =
                  factory.createExpressionFromText(expressions[0].getText() + ' ' + operator + " null", assertStatement);
                final PsiExpression assertCondition = assertStatement.getAssertCondition();
                LOG.assertTrue(assertCondition != null);
                assertCondition.replace(expression);
              }
              else if (expressions.length == 2) {
                assertStatement = createAssert(factory, expressions[0], methodCall.getParent());
                PsiExpression expression =
                  factory.createExpressionFromText(expressions[1].getText() + ' ' + operator + " null", assertStatement);
                final PsiExpression assertCondition = assertStatement.getAssertCondition();
                LOG.assertTrue(assertCondition != null);
                assertCondition.replace(expression);
              }
            }
            else if ("fail".equals(methodName)) {
              if (expressions.length == 0) {
                assertStatement = createAssert(factory, null, methodCall);
              }
              else if (expressions.length == 1) {
                assertStatement = createAssert(factory, expressions[0], methodCall);
              }
            }
            else {
              //if it's a 3 arg, the error message goes at the end
              PsiElement inserted = null;
              if (expressions.length == 2) {
                inserted = methodCallStatement
                  .replace(factory.createStatementFromText("org.testng.Assert." + methodCall.getText() + ";", methodCall.getParent()));
              }
              else if (expressions.length == 3) {
                @NonNls String call = "org.testng.Assert." + methodName + '(' + expressions[2].getText() + ", " + expressions[1].getText() +
                                      ", " + expressions[0].getText() + ");";
                inserted = methodCallStatement.replace(factory.createStatementFromText(call, methodCall.getParent()));
              }
              if (inserted != null) {
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
              }
            }
            if (assertStatement != null) {
              methodCallStatement.replace(assertStatement);
            }
          }
        }
        final PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null && "junit.framework.TestCase".equals(superClass.getQualifiedName())) {
          final PsiReferenceList extendsList = psiClass.getExtendsList();
          LOG.assertTrue(extendsList != null);
          for (PsiJavaCodeReferenceElement element : extendsList.getReferenceElements()) {
            element.delete();
          }
        }
        JavaCodeStyleManager.getInstance(project).optimizeImports(javaFile);//delete unused imports
      }
      catch (IncorrectOperationException e) {
        LOG.error("Error converting testcase", e);
      }
    }



    private static void convertJunitAnnotions(PsiElementFactory factory, PsiMethod method) throws IncorrectOperationException {
      PsiAnnotation[] annotations = method.getModifierList().getAnnotations();
      for (PsiAnnotation annotation : annotations) {
        PsiAnnotation newAnnotation = null;
        if ("org.junit.Test".equals(annotation.getQualifiedName())) {
          newAnnotation = factory.createAnnotationFromText("@org.testng.annotations.Test", method);
        }
        else if ("org.junit.BeforeClass".equals(annotation.getQualifiedName())) {
          newAnnotation = factory.createAnnotationFromText("@org.testng.annotations.BeforeClass", method);
        }
        else if ("org.junit.Before".equals(annotation.getQualifiedName())) {
          newAnnotation = factory.createAnnotationFromText("@org.testng.annotations.BeforeMethod", method);
        }
        else if ("org.junit.AfterClass".equals(annotation.getQualifiedName())) {
          newAnnotation = factory.createAnnotationFromText("@org.testng.annotations.AfterClass", method);
        }
        else if ("org.junit.After".equals(annotation.getQualifiedName())) {
          newAnnotation = factory.createAnnotationFromText("@org.testng.annotations.AfterMethod", method);
        }
        if (newAnnotation != null) {
          JavaCodeStyleManager.getInstance(annotation.getProject()).shortenClassReferences(annotation.replace(newAnnotation));
        }
      }
    }

    private static void convertJUnitConstructor(PsiMethod method) {
      method.accept(new JavaRecursiveElementWalkingVisitor() {

        @Override
        public void visitExpressionStatement(PsiExpressionStatement statement) {
          PsiExpression expression = statement.getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
            if (methodCall.getArgumentList().getExpressions().length == 1) {
              PsiMethod resolved = methodCall.resolveMethod();
              if (resolved != null && "junit.framework.TestCase".equals(resolved.getContainingClass().getQualifiedName()) &&
                  "TestCase".equals(resolved.getName())) {
                try {
                  statement.delete();
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            }
          }
        }
      });
    }

    private static PsiMethodCallExpression[] getTestCaseCalls(PsiMethod method) {
      PsiElement[] methodCalls = PsiTreeUtil.collectElements(method, new PsiElementFilter() {
        public boolean isAccepted(PsiElement element) {
          if (!(element instanceof PsiMethodCallExpression)) return false;
          final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
          final PsiMethod method = methodCall.resolveMethod();
          return method != null && "junit.framework.Assert".equals(method.getContainingClass().getQualifiedName());
        }
      });
      PsiMethodCallExpression[] expressions = new PsiMethodCallExpression[methodCalls.length];
      System.arraycopy(methodCalls, 0, expressions, 0, methodCalls.length);
      return expressions;
    }

    private static void addMethodJavadoc(PsiElementFactory factory, PsiMethod method) throws IncorrectOperationException {
      if (method.getName().startsWith("test")) {
        addMethodJavadocLine(factory, method, " * @testng.test");
      }
      else if ("setUp".equals(method.getName()) && method.getParameterList().getParameters().length == 0) {
        addMethodJavadocLine(factory, method, " * @testng.before-test");
      }
      else if ("tearDown".equals(method.getName()) && method.getParameterList().getParameters().length == 0) {
        addMethodJavadocLine(factory, method, " * @testng.after-test");
      }
    }

    private static void addMethodJavadocLine(PsiElementFactory factory, PsiMethod method, @NonNls String javaDocLine)
      throws IncorrectOperationException {
      PsiComment newComment;
      PsiElement comment = method.getFirstChild();
      if (comment != null && comment instanceof PsiComment) {
        String[] commentLines = comment.getText().split("\n");
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < commentLines.length; i++) {
          String commentLine = commentLines[i];
          // last line, append our new comment entry
          if (i == commentLines.length - 1) {
            buf.append(javaDocLine);
            buf.append(commentLine);
          }
          else {
            buf.append(commentLine);
            buf.append('\n');
          }
        }
        String commentString = buf.toString();

        newComment = factory.createCommentFromText(commentString, null);
        comment.replace(newComment);

      }
      else {
        String commentString;

        StringBuffer commentBuffer = new StringBuffer();
        commentBuffer.append("/**\n");
        commentBuffer.append(javaDocLine);
        commentBuffer.append('\n');
        commentBuffer.append(" */");

        commentString = commentBuffer.toString();
        newComment = factory.createCommentFromText(commentString, null);

        method.addBefore(newComment, comment);
      }
    }

    private static void addMethodAnnotations(PsiElementFactory factory, PsiMethod method) throws IncorrectOperationException {
      PsiAnnotation annotation = null;
      if (method.getName().startsWith("test")) {
        annotation = factory.createAnnotationFromText("@org.testng.annotations.Test", method);
      }
      else if ("setUp".equals(method.getName()) && method.getParameterList().getParameters().length == 0) {
        annotation = factory.createAnnotationFromText("@org.testng.annotations.BeforeMethod", method);
      }
      else if ("tearDown".equals(method.getName()) && method.getParameterList().getParameters().length == 0) {
        annotation = factory.createAnnotationFromText("@org.testng.annotations.AfterMethod", method);
      }
      if (annotation != null) {
        JavaCodeStyleManager.getInstance(annotation.getProject()).shortenClassReferences(method.getModifierList().addAfter(annotation, null));
      }
    }

    private static PsiAssertStatement createAssert(PsiElementFactory factory, PsiExpression description, PsiElement context)
      throws IncorrectOperationException {
      PsiAssertStatement assertStatement;
      if (description == null) {
        assertStatement = (PsiAssertStatement)factory.createStatementFromText("assert false;", context.getParent());
        return assertStatement;
      }
      else {
        assertStatement = (PsiAssertStatement)factory.createStatementFromText("assert false : \"x\";", context.getParent());
        final PsiExpression assertDescription = assertStatement.getAssertDescription();
        assert assertDescription != null;
        assertDescription.replace(description);
      }
      return assertStatement;
    }
  }
}
