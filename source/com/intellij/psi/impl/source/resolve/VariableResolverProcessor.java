package com.intellij.psi.impl.source.resolve;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaVariableConflictResolver;
import com.intellij.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;

/**
 * @author ik, dsl
 */
public class VariableResolverProcessor extends ConflictFilterProcessor implements NameHint, ElementClassHint {
  private static final ClassFilter ourFilter = new ClassFilter(PsiVariable.class);

  private final PsiElement myFromElement;
  private boolean myStaticScopeFlag = false;
  private PsiClass myAccessClass = null;
  private PsiElement myCurrentFileContext = null;

  public VariableResolverProcessor(String name, PsiElement place, PsiClass accessClass){
    super(name, null, ourFilter, new PsiConflictResolver[]{new JavaVariableConflictResolver()}, new ArrayList());
    myFromElement = place;
    myAccessClass = accessClass;
  }

  public VariableResolverProcessor(PsiJavaCodeReferenceElement fromElement) {
    super(fromElement.getText(), null, ourFilter, new PsiConflictResolver[]{new JavaVariableConflictResolver()}, new ArrayList());
    myFromElement = fromElement;

    PsiElement qualifier = fromElement.getQualifier();
    PsiElement referenceName = fromElement.getReferenceNameElement();

    if (referenceName instanceof PsiIdentifier){
      setName(referenceName.getText());
    }
    if (qualifier instanceof PsiExpression){
      final JavaResolveResult accessClass = PsiUtil.getAccessObjectClass((PsiExpression)qualifier);
      final PsiElement element = accessClass.getElement();
      if (element instanceof PsiTypeParameter) {
        final PsiManager manager = element.getManager();
        final PsiClassType type = manager.getElementFactory().createType((PsiTypeParameter) element);
        final PsiType accessType = accessClass.getSubstitutor().substitute(type);
        if(accessType instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(qualifier);
          myAccessClass = manager.getElementFactory().getArrayClass(languageLevel);
        }
        else if(accessType instanceof PsiClassType)
          myAccessClass = ((PsiClassType)accessType).resolve();
      }
      else if (element instanceof PsiClass)
        myAccessClass = (PsiClass) element;
    }
  }

  public final void handleEvent(Event event, Object associated) {
    super.handleEvent(event, associated);
    if(event == Event.START_STATIC){
      myStaticScopeFlag = true;
    }
    else if (Event.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  public void add(PsiElement element, PsiSubstitutor substitutor) {
    final boolean staticProblem = myStaticScopeFlag && !(((PsiVariable)element).hasModifierProperty(PsiModifier.STATIC));
    super.add(new CandidateInfo(element, substitutor, myFromElement, myAccessClass, staticProblem, myCurrentFileContext));
  }

  public boolean shouldProcess(Class elementClass) {
    return PsiVariable.class.isAssignableFrom(elementClass);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (!(element instanceof PsiField)) {
      super.execute(element, substitutor);
      return myResults.size() == 0;
    }

    return super.execute(element, substitutor);
  }
}
