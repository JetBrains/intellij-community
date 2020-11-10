package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.action.BaseRefactorHandler;

import java.util.ArrayList;
import java.util.List;

public class RefactorGetterHandler extends BaseRefactorHandler {

  public RefactorGetterHandler(Project project, DataContext dataContext) {
    super(dataContext, project);
  }

  @Override
  protected @NlsContexts.DialogTitle String getChooserTitle() {
    return LombokBundle.message("dialog.title.select.fields.to.replace.getter.method.with.getter");
  }

  @Override
  protected @NlsContexts.HintText String getNothingFoundMessage() {
    return LombokBundle.message("hint.text.no.field.getter.have.been.found.to.generate.getters.for");
  }

  @Override
  protected @NlsContexts.HintText String getNothingAcceptedMessage() {
    return LombokBundle.message("hint.text.no.fields.with.getter.method.were.found");
  }

  @Override
  protected List<EncapsulatableClassMember> getEncapsulatableClassMembers(PsiClass psiClass) {
    final List<EncapsulatableClassMember> result = new ArrayList<>();
    for (PsiField field : psiClass.getFields()) {
      if (null != PropertyUtilBase.findPropertyGetter(psiClass, field.getName(), false, false)) {
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
      PsiMethod psiMethod = PropertyUtilBase.findPropertyGetter(psiField.getContainingClass(), psiField.getName(), false, false);

      if (null != psiMethod) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (null != modifierList) {
          PsiAnnotation psiAnnotation = modifierList.addAnnotation(LombokClassNames.GETTER);
//          psiAnnotation.setDeclaredAttributeValue("value", )

          psiMethod.delete();
        }
      }
    }
  }
}
