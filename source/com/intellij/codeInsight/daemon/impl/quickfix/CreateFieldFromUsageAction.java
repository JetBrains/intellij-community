package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateFieldFromUsageAction extends CreateVarFromUsageAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageAction");

  public CreateFieldFromUsageAction(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected String getText(String varName) {
    return "Create Field '" + varName + "'";
  }

  protected boolean createConstantField() {
    return false;
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression)) {
      return;
    }

    final PsiManager psiManager = myReferenceExpression.getManager();
    final Project project = psiManager.getProject();
    final PsiElementFactory factory = psiManager.getElementFactory();


    PsiMember enclosingContext = null;
    PsiClass parentClass;
    do {
      enclosingContext = (PsiMember)PsiTreeUtil.getParentOfType(
        enclosingContext == null ? myReferenceExpression : (PsiElement)enclosingContext,
        new Class[]{PsiMethod.class, PsiField.class, PsiClassInitializer.class});
      parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
    }
    while (parentClass instanceof PsiAnonymousClass);

    PsiFile targetFile = targetClass.getContainingFile();

    try {
      final ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);

      String fieldName = myReferenceExpression.getReferenceName();
      PsiField field;
      if (!createConstantField()) {
        field = factory.createField(fieldName, PsiType.INT);
      } else {
        PsiClass aClass = factory.createClassFromText("int i = 0;", null);
        field = aClass.getFields()[0];
        field.setName(fieldName);
      }
      if (enclosingContext != null && enclosingContext.getParent() == parentClass && targetClass == parentClass 
          && (enclosingContext instanceof PsiClassInitializer || enclosingContext instanceof PsiField)) {
        field = (PsiField)targetClass.addBefore(field, enclosingContext);
      }
      else {
        field = (PsiField)targetClass.add(field);
      }

      final Editor newEditor = positionCursor(project, targetFile, field);

      setupVisibility(parentClass, targetClass, field.getModifierList());

      if (shouldCreateStaticMember(myReferenceExpression, enclosingContext, targetClass)) {
        field.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      }

      if (createConstantField()) {
        field.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
        field.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      }

      TemplateBuilder builder = new TemplateBuilder(field);
      PsiElement context = PsiTreeUtil.getParentOfType(myReferenceExpression, new Class[] {PsiClass.class, PsiMethod.class});
      new GuessTypeParameters(factory).setupTypeElement(field.getTypeElement(), expectedTypes, getTargetSubstitutor(myReferenceExpression), builder, context, targetClass);

      if (createConstantField()) {
        builder.replaceElement(field.getInitializer(), new EmptyExpression());
      }

      builder.setEndVariableAfter(field.getNameIdentifier());

      final Template template = builder.buildTemplate();

      TextRange range = field.getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      startTemplate(newEditor, template, project);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public String getFamilyName() {
    return "Create Field from Usage";
  }
}
