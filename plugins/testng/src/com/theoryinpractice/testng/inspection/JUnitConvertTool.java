// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Hani Suleiman
 */
public class JUnitConvertTool extends AbstractBaseJavaLocalInspectionTool {

  private static final Logger LOG = Logger.getInstance("TestNG QuickFix");
  private static final String DISPLAY_NAME = "Convert JUnit Tests to TestNG";
  private static final Map<String, String> ANNOTATIONS_MAP;

  public static final String QUICKFIX_NAME = "Convert TestCase to TestNG";

  static {
    ANNOTATIONS_MAP = new HashMap<>();
    ANNOTATIONS_MAP.put("org.junit.Test", "@org.testng.annotations.Test");
    ANNOTATIONS_MAP.put("org.junit.BeforeClass", "@org.testng.annotations.BeforeClass");
    ANNOTATIONS_MAP.put("org.junit.Before", "@org.testng.annotations.BeforeMethod");
    ANNOTATIONS_MAP.put("org.junit.AfterClass", "@org.testng.annotations.AfterClass");
    ANNOTATIONS_MAP.put("org.junit.After", "@org.testng.annotations.AfterMethod");
  }

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
    if (TestNGUtil.inheritsJUnitTestCase(psiClass) || TestNGUtil.containsJunitAnnotations(psiClass)) {
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
    public String getFamilyName() {
      return QUICKFIX_NAME;
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
      if (!TestNGUtil.checkTestNGInClasspath(psiClass)) return;
      try {
        final PsiManager manager = PsiManager.getInstance(project);
        final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final PsiJavaFile javaFile = (PsiJavaFile)psiClass.getContainingFile();

        final List<PsiElement> convertedElements = new SmartList<>();

        for (PsiMethod method : psiClass.getMethods()) {
          final PsiMethodCallExpression[] methodCalls = getTestCaseCalls(method);

          if (method.isConstructor()) {
            convertJUnitConstructor(method);
          }
          else {
            if (!javaFile.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) {
              addMethodJavadoc(factory, method);
            }
            else {
              if (TestNGUtil.containsJunitAnnotations(method)) {
                convertedElements.addAll(convertJunitAnnotations(factory, method));
              } else {
                convertedElements.add(addMethodAnnotations(factory, method));
              }
            }
          }

          for (PsiMethodCallExpression methodCall : methodCalls) {
            PsiMethod assertMethod = methodCall.resolveMethod();
            if (assertMethod == null) {
              continue;
            }
            @NonNls String methodName = assertMethod.getName();
            PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
            final PsiStatement methodCallStatement = PsiTreeUtil.getParentOfType(methodCall, PsiStatement.class);
            LOG.assertTrue(methodCallStatement != null);
            final String qualifierTemplate = methodCall.getMethodExpression().getQualifierExpression() != null ? "$qualifier$." : "";
            final String searchTemplate;
            final String replaceTemplate;
            if ("assertNull".equals(methodName) || "assertNotNull".equals(methodName) || "assertTrue".equals(methodName) || "assertFalse".equals(methodName)) {
              boolean hasMessage = expressions.length == 2;
              searchTemplate = qualifierTemplate + "$method$($object$ " + (hasMessage ? ",$msg$" : "") + ")";
              replaceTemplate = "org.testng.Assert.$method$(" + (hasMessage ? "$msg$," : "") + "$object$)";
            }
            else if ("fail".equals(methodName)) {
              boolean hasMessage = expressions.length == 1;
              searchTemplate = qualifierTemplate + "$method$(" + (hasMessage ? "$msg$" : "") + ")";
              replaceTemplate = "org.testng.Assert.$method$(" + (hasMessage ? "$msg$" : "") + ")";
            }
            else if ("assertThat".equals(methodName)) {
              String paramTemplate = (expressions.length == 3 ? "$msg$," : "") + "$actual$, $matcher$";
              searchTemplate = qualifierTemplate + "assertThat(" + paramTemplate + ")";
              replaceTemplate = "org.hamcrest.MatcherAssert.assertThat(" + paramTemplate +")";
            }
            else {
              boolean hasMessage = hasMessage(methodCall);
              if ((hasMessage && expressions.length == 4) || (!hasMessage && expressions.length == 3)) {
                searchTemplate = qualifierTemplate + "$method$";
                replaceTemplate = "org.testng.AssertJUnit.$method$";
              } else {
                String replaceMethodWildCard = "$method$";
                if (methodName.equals("assertArrayEquals")) {
                  replaceMethodWildCard = "assertEquals";
                }
                searchTemplate = qualifierTemplate + "$method$(" + (hasMessage ? "$msg$, " : "")  + "$expected$, $actual$" + ")";
                replaceTemplate = "org.testng.Assert." + replaceMethodWildCard + "($actual$, $expected$ " + (hasMessage ? ", $msg$" : "") + ")";
              }
            }
            convertedElements.add(TypeConversionDescriptor.replaceExpression(methodCall, searchTemplate, replaceTemplate));
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
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        codeStyleManager.optimizeImports(javaFile);//delete unused imports
        codeStyleManager.shortenClassReferences(javaFile);
      }
      catch (IncorrectOperationException e) {
        LOG.error("Error converting testcase", e);
      }
    }

    private static boolean hasMessage(PsiMethodCallExpression expression) {
      final PsiExpression[] expressions = expression.getArgumentList().getExpressions();
      if (expressions.length == 4) {
        return true;
      }
      final PsiMethod method = expression.resolveMethod();
      LOG.assertTrue(method != null);
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        final PsiType type = parameter.getType();
        if (type instanceof PsiClassType) {
          final PsiClass resolvedClass = ((PsiClassType)type).resolve();
          if (resolvedClass != null && CommonClassNames.JAVA_LANG_STRING.equals(resolvedClass.getQualifiedName())) {
            return true;
          }
        }
      }
      return false;
    }

    private static List<PsiElement> convertJunitAnnotations(final PsiElementFactory factory, final PsiMethod method) throws IncorrectOperationException {
      PsiAnnotation[] annotations = method.getModifierList().getAnnotations();
      return ContainerUtil.mapNotNull(annotations, annotation -> {
        final String testNgAnnotation = ANNOTATIONS_MAP.get(annotation.getQualifiedName());
        if (testNgAnnotation != null) {
          final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@org.testng.annotations.Test", method);
          return annotation.replace(newAnnotation);
        }
        return null;
      });
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
          if (method != null) {
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
              final String qualifiedName = containingClass.getQualifiedName();
              if ("junit.framework.Assert".equals(qualifiedName) ||
                  "org.junit.Assert".equals(qualifiedName) ||
                  "junit.framework.TestCase".equals(qualifiedName)) {
                return true;
              }
            }
          }
          return false;
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
      if (comment instanceof PsiComment) {
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

    private static PsiElement addMethodAnnotations(PsiElementFactory factory, PsiMethod method) throws IncorrectOperationException {
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
        return method.getModifierList().addAfter(annotation, null);
      }
      return null;
    }
  }
}
