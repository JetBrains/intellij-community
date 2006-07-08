package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.03.2003
 * Time: 14:46:31
 * To change this template use Options | File Templates.
 */
public class ConflictFilterProcessor extends FilterScopeProcessor
 implements NameHint{
  protected final PsiConflictResolver[] myResolvers;
  protected JavaResolveResult[] myCachedResult = null;
  protected String myName;

  public ConflictFilterProcessor(String name, PsiElement element, ElementFilter filter, PsiConflictResolver[] resolvers, List container){
    super(filter, element, container);
    myResolvers = resolvers;
    myName = name;
  }

  public PsiConflictResolver[] getResolvers(){
    return myResolvers;
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor){
    if(myCachedResult != null && myCachedResult.length == 1 && myCachedResult[0].isAccessible()) {
      return false;
    }

    if(myName == null || PsiUtil.checkName(element, myName)){
      return super.execute(element, substitutor);
    }
    return true;
  }

  protected void add(PsiElement element, PsiSubstitutor substitutor){
    add(new CandidateInfo(element, substitutor));
  }

  protected void add(CandidateInfo info){
    myCachedResult = null;
    myResults.add(info);
  }


  public void handleEvent(Event event, Object associated){
    if(event == Event.CHANGE_LEVEL && myName != null){
      myCachedResult = getResult();
    }
    for (PsiConflictResolver myResolver : myResolvers) {
      myResolver.handleProcessorEvent(event, associated);
    }
  }

  public void forceResult(JavaResolveResult[] result){
    myCachedResult = result;
  }


  public JavaResolveResult[] getResult(){
    if(myCachedResult == null){
      CandidateInfo candidate;

      final List<CandidateInfo> conflicts = new ArrayList<CandidateInfo>((List<CandidateInfo>)super.getResults());
      for (PsiConflictResolver myResolver : myResolvers) {
        if ((candidate = myResolver.resolveConflict(conflicts)) != null) {
          conflicts.clear();
          conflicts.add(candidate);
          break;
        }
      }
      myCachedResult = conflicts.toArray(new JavaResolveResult[conflicts.size()]);
    }

    return myCachedResult;
  }

  public String getName(){
    return myName;
  }

  public void setName(String name){
    myName = name;
  }

  public <T> T getHint(Class<T> hintClass) {
    if (hintClass.equals(NameHint.class)){
      if(myName == null){
        return null;
      }
    }
    return super.getHint(hintClass);
  }
}
