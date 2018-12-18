package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PropertyUtil;
import de.plushnikov.intellij.plugin.action.BaseRefactorHandler;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class RefactorSetterHandler extends BaseRefactorHandler {

  public RefactorSetterHandler(Project project, DataContext dataContext) {
    super(dataContext, project);
  }

  protected String getChooserTitle() {
    return "Select Fields to replace Setter-Method With @Getter";
  }

  @Override
  protected String getNothingFoundMessage() {
    return "No field getter have been found to generate @Setter for";
  }

  @Override
  protected String getNothingAcceptedMessage() {
    return "No fields with setter method were found";
  }

  @Override
  protected List<EncapsulatableClassMember> getEncapsulatableClassMembers(PsiClass psiClass) {
    final List<EncapsulatableClassMember> result = new ArrayList<>();
    for (PsiField field : psiClass.getFields()) {
      if (null != PropertyUtil.findPropertySetter(psiClass, field.getName(), false, false)) {
        result.add(new PsiFieldMember(field));
      }
    }
    return result;
  }

  @Override
  protected void process(List<ClassMember> classMembers) {
    for (ClassMember classMember : classMembers) {
      final PsiElementClassMember elementClassMember = (PsiElementClassMember) classMember;

      PsiField psiField = (PsiField) elementClassMember.getPsiElement();
      PsiMethod psiMethod = PropertyUtil.findPropertySetter(psiField.getContainingClass(), psiField.getName(), false, false);
      if (null != psiMethod) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (null != modifierList) {
          PsiAnnotation psiAnnotation = modifierList.addAnnotation(Setter.class.getName());

          psiMethod.delete();
        }
      }
    }
  }
}
