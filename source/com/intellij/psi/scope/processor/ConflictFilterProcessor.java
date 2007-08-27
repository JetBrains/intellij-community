package com.intellij.psi.scope.processor;

import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.03.2003
 * Time: 14:46:31
 * To change this template use Options | File Templates.
 */
public class ConflictFilterProcessor extends FilterScopeProcessor<CandidateInfo> implements NameHint{
  protected final PsiConflictResolver[] myResolvers;
  private JavaResolveResult[] myCachedResult = null;
  protected String myName;
  protected final PsiElement myPlace;

  public ConflictFilterProcessor(String name, ElementFilter filter, PsiConflictResolver[] resolvers, List<CandidateInfo> container,
                                 final PsiElement place){
    super(filter, container);
    myResolvers = resolvers;
    myName = name;
    myPlace = place;
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor){
    if(myCachedResult != null && myCachedResult.length == 1 && myCachedResult[0].isAccessible()) {
      return false;
    }

    if(myName == null || PsiUtil.checkName(element, myName, myPlace)){
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

  public JavaResolveResult[] getResult(){
    if(myCachedResult == null){
      final List<CandidateInfo> conflicts = new SmartList<CandidateInfo>(super.getResults());
      for (PsiConflictResolver resolver : myResolvers) {
        CandidateInfo candidate = resolver.resolveConflict(conflicts);
        if (candidate != null) {
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
