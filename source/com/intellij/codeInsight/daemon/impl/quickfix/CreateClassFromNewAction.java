package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author mike
 */
public class CreateClassFromNewAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromNewAction");
  private final PsiNewExpression myNewExpression;

  public CreateClassFromNewAction(PsiNewExpression newExpression) {
    myNewExpression = newExpression;
  }

  protected void invokeImpl(PsiClass targetClass) {
    final PsiManager psiManager = myNewExpression.getManager();
    final Project project = psiManager.getProject();
    final PsiElementFactory elementFactory = psiManager.getElementFactory();

    final PsiClass psiClass = CreateFromUsageUtils.createClass(getReferenceElement(myNewExpression), false, null);
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try {
            if (psiClass == null) return;

            setupInheritance(myNewExpression, psiClass);
            setupGenericParameters(myNewExpression, psiClass);

            final Editor editor = positionCursor(project, psiClass.getContainingFile(), psiClass);
            PsiExpressionList argList = myNewExpression.getArgumentList();
            if (argList != null && argList.getExpressions().length > 0) {
              PsiMethod constructor = elementFactory.createConstructor();
              constructor = (PsiMethod) psiClass.add(constructor);

              TemplateBuilder templateBuilder = new TemplateBuilder(psiClass);
              CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, argList, getTargetSubstitutor(myNewExpression));

              setupSuperCall(psiClass, constructor, templateBuilder);

              getReferenceElement(myNewExpression).bindToElement(psiClass);

              final Template template = templateBuilder.buildTemplate();

              final TextRange textRange = psiClass.getTextRange();
              editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());

              startTemplate(editor, template, project);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }

  public static void setupSuperCall(PsiClass targetClass, PsiMethod constructor, TemplateBuilder templateBuilder)
    throws IncorrectOperationException {
    PsiElementFactory elementFactory = targetClass.getManager().getElementFactory();

    PsiClass superClass = targetClass.getSuperClass();
    if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName()) &&
          !"java.lang.Enum".equals(superClass.getQualifiedName())) {
      final PsiMethod[] constructors = superClass.getConstructors();
      boolean hasDefaultConstructor = false;

      for (int i = 0; i < constructors.length; i++) {
        PsiMethod superConstructor = constructors[i];
        if (superConstructor.getParameterList().getParameters().length == 0) {
          hasDefaultConstructor = true;
          break;
        }
      }

      if (!hasDefaultConstructor) {
        PsiExpressionStatement statement =
          (PsiExpressionStatement)elementFactory.createStatementFromText("super();", constructor);
        statement = (PsiExpressionStatement)constructor.getBody().add(statement);

        PsiMethodCallExpression call = (PsiMethodCallExpression)statement.getExpression();
        final PsiExpressionList argumentList = call.getArgumentList();
        templateBuilder.setEndVariableAfter(argumentList.getFirstChild());
      }
    }

    templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
  }

  private void setupGenericParameters(PsiNewExpression expr, PsiClass targetClass) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement ref = expr.getClassReference();
    int numParams = ref.getTypeParameters().length;
    if (numParams == 0) return;
    PsiElementFactory factory = expr.getManager().getElementFactory();
    targetClass.getTypeParameterList().add(factory.createTypeParameterFromText("T", null));
    for (int i = 2; i <= numParams; i++) {
      targetClass.getTypeParameterList().add(factory.createTypeParameterFromText("T" + (i-1), null));
    }
  }

  private void setupInheritance(PsiNewExpression element, PsiClass targetClass) throws IncorrectOperationException {
    if ((element.getParent() instanceof PsiReferenceExpression)) return;

    final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getInstance(myNewExpression.getProject()).getExpectedTypes(element, false);

    for (int i = 0; i < expectedTypes.length; i++) {
      ExpectedTypeInfo expectedType = expectedTypes[i];
      final PsiType type = expectedType.getType();
      PsiClass aClass = PsiUtil.resolveClassInType(type);
      if (aClass == null) continue;
      if (aClass.equals(targetClass) || aClass.hasModifierProperty(PsiModifier.FINAL)) continue;
      PsiElementFactory factory = aClass.getManager().getElementFactory();

      if (aClass.isInterface()) {
        final PsiReferenceList implementsList = targetClass.getImplementsList();
        implementsList.add(factory.createClassReferenceElement(aClass));
      }
      else {
        final PsiReferenceList extendsList = targetClass.getExtendsList();
        if (extendsList.getReferencedTypes().length > 0) continue;
        extendsList.add(factory.createClassReferenceElement(aClass));
      }
    }
  }


  private PsiFile getTargetFile(PsiElement element) {
    final PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)element);

    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)referenceElement.getQualifier();
      final PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)psiElement;
        return psiClass.getContainingFile();
      }
    }

    return null;
  }

  protected PsiElement getElement() {
    if (!myNewExpression.isValid() || !myNewExpression.getManager().isInProject(myNewExpression)) return null;
    final PsiJavaCodeReferenceElement referenceElement = getReferenceElement(myNewExpression);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return myNewExpression;

    return null;
  }

  protected boolean isValidElement(PsiElement element) {
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(element, PsiJavaCodeReferenceElement.class);
    if (ref == null) return false;

    return ref.resolve() != null;
  }

  protected boolean isAvailableImpl(int offset) {
    final PsiElement nameElement = getNameElement(myNewExpression);

    final PsiFile targetFile = getTargetFile(myNewExpression);
    if (targetFile != null && !targetFile.isWritable()) {
      return false;
    }

    if (shouldShowTag(offset, nameElement, myNewExpression)) {
      setText("Create Class '" + nameElement.getText() + "'");
      return true;
    }

    return false;
  }

  private PsiJavaCodeReferenceElement getReferenceElement(PsiNewExpression expression) {
    return expression.getClassReference();
  }

  private PsiElement getNameElement(PsiNewExpression targetElement) {
    final PsiJavaCodeReferenceElement referenceElement = getReferenceElement(targetElement);
    if (referenceElement == null) return null;
    return referenceElement.getReferenceNameElement();
  }

  public String getFamilyName() {
    return "Create Class from New";
  }
}
