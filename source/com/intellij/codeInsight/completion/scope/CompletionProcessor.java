package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.util.PsiUtil;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:13:27
 * To change this template use Options | File Templates.
 */
public class CompletionProcessor extends BaseScopeProcessor
 implements ElementClassHint{
  private final String myPrefix;
  private boolean myStatic = false;
  private final Set<Object> myResultNames = new HashSet<Object>();
  private final List<CompletionElement> myResults;
  private final PsiElement myElement;
  private PsiElement myScope;
  private CodeInsightSettings mySettings = null;
  private final ElementFilter myFilter;
  private boolean myMembersFlag = false;
  private PsiType myQualifierType = null;
  private PsiClass myQualifierClass = null;

  private CompletionProcessor(String prefix, PsiElement element, List<CompletionElement> container, ElementFilter filter){
    mySettings = CodeInsightSettings.getInstance();
    myPrefix = prefix;
    myResults = container;
    myElement = element;
    myFilter = filter;
    myScope = element;
    if (ResolveUtil.findParentContextOfClass(myElement, PsiDocComment.class, false) != null)
      myMembersFlag = true;
    while(myScope != null && !(myScope instanceof PsiFile || myScope instanceof PsiClass)){
      myScope = myScope.getContext();
    }

    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        myQualifierClass = ResolveUtil.getContextClass(myElement);
        myQualifierType = myElement.getManager().getElementFactory().createType(myQualifierClass);
      }
      else if (qualifier != null) {
          myQualifierType = qualifier.getType();
          myQualifierClass = PsiUtil.resolveClassInType(myQualifierType);
        }
      }
  }

  public CompletionProcessor(String prefix, PsiElement element, ElementFilter filter){
    this(prefix, element, new ArrayList<CompletionElement>(), filter);
  }


  public void handleEvent(Event event, Object associated){
    if(event == Event.START_STATIC){
      myStatic = true;
    }
    if(event == Event.CHANGE_LEVEL){
      myMembersFlag = true;
    }
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor){
    if(!(element instanceof PsiClass) && element instanceof PsiModifierListOwner){
      PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
      if(myStatic){
        if(!modifierListOwner.hasModifierProperty(PsiModifier.STATIC)){
          // we don't need non static method in static context.
          return true;
        }
      }
      else{
        if(!mySettings.SHOW_STATIC_AFTER_INSTANCE
          && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)
          && !myMembersFlag){
          // according settings we don't need to process such fields/methods
          return true;
        }
      }
    }
    final PsiElement elementParent = myElement.getParent();
    if(element instanceof PsiPackage){
      if(!mySettings.LIST_PACKAGES_IN_CODE && myScope instanceof PsiClass){
        if(!(elementParent instanceof PsiJavaCodeReferenceElement
             && ((PsiJavaCodeReferenceElement)elementParent).isQualified())) {
          return true;
        }
      }
    }

    PsiResolveHelper resolveHelper = myElement.getManager().getResolveHelper();
    if(!(element instanceof PsiMember) || resolveHelper.isAccessible(((PsiMember)element), myElement, myQualifierClass)){
      final String name = PsiUtil.getName(element);
      if(CompletionUtil.checkName(name, myPrefix) && myFilter.isClassAcceptable(element.getClass())
        && myFilter.isAcceptable(new CandidateInfo(element, substitutor), myElement))
        add(new CompletionElement(myQualifierType, element, substitutor));
    }
    return true;
  }

  private void add(CompletionElement element){
    if(!myResultNames.contains(element.getUniqueId())){
      myResultNames.add(element.getUniqueId());
      myResults.add(element);
    }
  }

  public Collection getResults(){
    return myResults;
  }

  public boolean shouldProcess(Class elementClass){
    return myFilter.isClassAcceptable(elementClass);
  }
}
