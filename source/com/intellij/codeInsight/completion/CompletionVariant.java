package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.CompletionProcessor;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.01.2003
 * Time: 17:38:14
 * To change this template use Options | File Templates.
 */

public class CompletionVariant
implements JDOMExternalizable{
  protected static short DEFAULT_TAIL_TYPE = TailType.SPACE;

  private final Set myScopeClasses = new HashSet();
  private ElementFilter myPosition;
  private final List myCompletionsList = new ArrayList();
  private final Set myScopeClassExceptions = new HashSet();
  private InsertHandler myInsertHandler = null;
  private final Map myItemProperties = new com.intellij.util.containers.HashMap();
  private boolean caseInsensitive;

  public CompletionVariant(){}

  public CompletionVariant(Class scopeClass, ElementFilter position){
    includeScopeClass(scopeClass);
    myPosition = position;
  }

  public CompletionVariant(ElementFilter position){
    myPosition = position;
  }

  public boolean isScopeAcceptable(PsiElement scope){
    return isScopeClassAcceptable(scope.getClass());
  }

  public boolean isScopeFinal(PsiElement scope){
    return isScopeClassFinal(scope.getClass());
  }

  public InsertHandler getInsertHandler(){
    return myInsertHandler;
  }

  public void setInsertHandler(InsertHandler handler){
    myInsertHandler = handler;
  }

  public void setItemProperty(Object id, Object value){
    myItemProperties.put(id, value);
  }

  public boolean isScopeClassFinal(Class scopeClass){
    Iterator iter = myScopeClasses.iterator();
    while(iter.hasNext()){
      Scope scope = (Scope) iter.next();
      if(scope.myClass.isAssignableFrom(scopeClass) && scope.myFinalFlag){
        return true;
      }
    }
    return false;
  }

  public boolean isScopeClassAcceptable(Class scopeClass){
    boolean ret = false;

    Iterator iter = myScopeClasses.iterator();
    while(iter.hasNext()){
      final Class aClass = ((Scope) iter.next()).myClass;
      if(aClass.isAssignableFrom(scopeClass)){
        ret = true;
        break;
      }
    }

    if(ret){
      iter = myScopeClassExceptions.iterator();
      while(iter.hasNext()){
        final Class aClass = (Class) iter.next();
        if(aClass.isAssignableFrom(scopeClass)){
          ret = false;
          break;
        }
      }
    }
    return ret;
  }

  public void excludeScopeClass(Class aClass){
    myScopeClassExceptions.add(aClass);
  }

  public void includeScopeClass(Class aClass){
    myScopeClasses.add(new Scope(aClass, false));
  }

  public void includeScopeClass(Class aClass, boolean flag){
    myScopeClasses.add(new Scope(aClass, flag));
  }

  public void addCompletionFilterOnElement(ElementFilter filter){
    addCompletionFilterOnElement(filter, TailType.NONE);
  }

  public void addCompletionFilterOnElement(ElementFilter filter, int tailType){
    addCompletion((Object)new ElementExtractorFilter(filter), tailType);
  }

  public void addCompletionFilter(ElementFilter filter, int tailType){
    addCompletion((Object)filter, tailType);
  }

  public void addCompletionFilter(ElementFilter filter){
    addCompletionFilter(filter, TailType.NONE);
  }

  public void addCompletion(String keyword){
    addCompletion(keyword, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(String keyword, int tailType){
    addCompletion((Object)keyword, tailType);
  }

  public void addCompletion(KeywordChooser chooser){
    addCompletion(chooser, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(KeywordChooser chooser, int tailType){
    addCompletion((Object)chooser, tailType);
  }

  public void addCompletion(ContextGetter chooser){
    addCompletion(chooser, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(ContextGetter chooser, int tailType){
    addCompletion((Object)chooser, tailType);
  }

  private void addCompletion(Object completion, int tail){
    myCompletionsList.add(new CompletionVariantItem(completion, tail));
  }

  public void addCompletion(String[] keywordList){
    addCompletion(keywordList, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(String[] keywordList, int tailType){
    for(int i = 0; i < keywordList.length; i++){
      addCompletion(keywordList[i], tailType);
    }
  }

  public boolean isVariantApplicable(PsiElement position, PsiElement scope){
    if(isScopeAcceptable(scope)){
      return myPosition.isAcceptable(position, scope);
    }
    return false;
  }

  public void addReferenceCompletions(PsiReference reference, PsiElement position, LinkedHashSet set, String prefix){
    final Iterator<CompletionVariantItem> iter = myCompletionsList.iterator();

    while(iter.hasNext()){
      final CompletionVariantItem ce = (CompletionVariantItem)iter.next();
      addReferenceCompletions(reference, position, set, prefix, ce);
    }
  }

  private LookupItem addLookupItem(LinkedHashSet set, CompletionVariantItem element, Object completion, String prefix){
    LookupItem ret = LookupItemUtil.objectToLookupItem(completion);
    if(ret == null) return null;

    if(getInsertHandler() != null){
      ret.setAttribute(LookupItem.INSERT_HANDLER_ATTR, getInsertHandler());
      ret.setTailType(TailType.UNKNOWN);
    }
    else {
      ret.setTailType(element.myTailType);
    }

    final Iterator iter = myItemProperties.keySet().iterator();
    while(iter.hasNext()){
      final Object key = iter.next();
      if(key == LookupItem.FORCE_SHOW_FQN_ATTR && ret.getObject() instanceof PsiClass){
        String packageName = ((PsiClass)(ret.getObject())).getQualifiedName();
        if(packageName != null && packageName.lastIndexOf('.') > 0)
          packageName = packageName.substring(0, packageName.lastIndexOf('.'));
        else packageName = "";
        if (packageName.length() == 0){
          packageName = "default package";
        }

        ret.setAttribute(LookupItem.TAIL_TEXT_ATTR, " (" + packageName + ")");
        ret.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
      }
      else{
        if(completion instanceof PsiNamedElement && key == LookupItem.FORCE_QUALIFY){
          final PsiNamedElement completionElement = (PsiNamedElement) completion;
          final PsiElement parent = completionElement.getParent();
          if(parent instanceof PsiClass){
            final String className = ((PsiClass) parent).getName();
            ret.setLookupString(className + "." + ret.getLookupString());
            ret.setAttribute(key, myItemProperties.get(key));
          }
        }
        ret.setAttribute(key, myItemProperties.get(key));
      }
    }
    final String lookupString = ret.getLookupString();
    if(CompletionUtil.checkName(lookupString, prefix, caseInsensitive)){
      set.add(ret);
      return ret;
    }
    return null;
  }

  public void addKeywords(PsiElementFactory factory, LinkedHashSet set, CompletionContext context, PsiElement position){
    final Iterator iter = myCompletionsList.iterator();
    while(iter.hasNext()){
      final CompletionVariantItem ce = (CompletionVariantItem)iter.next();
      final Object comp = ce.myCompletion;
      if(comp instanceof OffsetDependant){
        ((OffsetDependant)comp).setOffset(context.startOffset);
      }

      if(comp instanceof String){
        addKeyword(factory, set, ce, comp, context);
      }
      else if (comp instanceof ContextGetter){
        final Object[] elements = ((ContextGetter)comp).get(position, context);
        for(int i = 0; i < elements.length; i++){
          addLookupItem(set, ce, elements[i], context.prefix);
        }
      }
      // TODO: KeywordChooser -> ContextGetter
      else if(comp instanceof KeywordChooser){
        final String[] keywords = ((KeywordChooser)comp).getKeywords(context, position);
        for(int i = 0; i < keywords.length; i++){
          addKeyword(factory, set, ce, keywords[i], context);
        }
      }
    }
  }

  private void addKeyword(PsiElementFactory factory, LinkedHashSet set, final CompletionVariantItem ce, final Object comp, CompletionContext context){
    final Iterator iter = set.iterator();
    while(iter.hasNext()){
      final LookupItem item = (LookupItem)iter.next();
      if((item).getObject().toString().equals(comp.toString())){
        return;
      }
    }
    if(factory == null){
      addLookupItem(set, ce, comp, context.prefix);
    }
    else{
      try{
        final PsiKeyword keyword = factory.createKeyword((String)comp);
        addLookupItem(set, ce, keyword, context.prefix);
      }
      catch(IncorrectOperationException e){
        addLookupItem(set, ce, comp, context.prefix);
      }
    }
  }

  public boolean hasReferenceFilter(){
    final Iterator iter = myCompletionsList.iterator();
    while(iter.hasNext()){
      if(((CompletionVariantItem)iter.next()).myCompletion instanceof ElementFilter){
        return true;
      }
    }
    return false;
  }

  public void readExternal(Element variantElement) throws InvalidDataException{
    final Element filterElement = variantElement.getChild("position", CompletionData.COMPLETION_NS);
    final String scopeString = variantElement.getAttribute("scope").getValue().trim();
    final String excludeString = variantElement.getAttribute("exclude").getValue().trim();

    for(StringTokenizer tokenizer = new StringTokenizer(scopeString, "|");
        tokenizer.hasMoreTokens();){
      final String s = tokenizer.nextToken();
      myScopeClasses.add(FilterUtil.getClassByName(s));
    }

    for(StringTokenizer tokenizer = new StringTokenizer(excludeString, "|");
        tokenizer.hasMoreTokens();){
      final String s = tokenizer.nextToken();
      myScopeClassExceptions.add(FilterUtil.getClassByName(s));
    }

    final Element completionsElement = variantElement.getChild("completions", CompletionData.COMPLETION_NS);

    myPosition = (ElementFilter)FilterUtil.readFilterGroup(filterElement).get(0);

    final Iterator keywordsIterator = completionsElement.getChildren("keyword-set", CompletionData.COMPLETION_NS).iterator();
    while(keywordsIterator.hasNext()){
      final Element keywordElement = (Element) keywordsIterator.next();
      final StringTokenizer tok = new StringTokenizer(keywordElement.getTextTrim());
      while(tok.hasMoreTokens()){
        myCompletionsList.add(tok.nextToken().trim());
      }
    }
    final Iterator filtersIterator = completionsElement.getChildren("filter", CompletionData.COMPLETION_NS).iterator();
    while(filtersIterator.hasNext()){
      myCompletionsList.addAll(FilterUtil.readFilterGroup((Element)filtersIterator.next()));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException{
    throw new WriteExternalException("Can't write completion data!!!");
  }

  protected void addReferenceCompletions(PsiReference reference, PsiElement position, LinkedHashSet set,
                                         String prefix, CompletionVariantItem item){
    if(item.myCompletion instanceof ElementFilter){
      final CompletionProcessor processor = new CompletionProcessor(prefix, position, (ElementFilter)item.myCompletion);
      if(reference instanceof PsiJavaReference){
        ((PsiJavaReference)reference).processVariants(processor);
      }
      else{
        final Object[] completions = reference.getVariants();

        if(completions == null){
          return;
        }
        for(int j = 0; j < completions.length; j++){
          if(completions[j] instanceof PsiElement){
            processor.execute((PsiElement)completions[j], PsiSubstitutor.EMPTY);
          }
          else if(completions[j] instanceof CandidateInfo){
            final CandidateInfo info = ((CandidateInfo)completions[j]);
            if(info.isValidResult())
              processor.execute(info.getElement(), PsiSubstitutor.EMPTY);
          }
          else{
            addLookupItem(set, item, completions[j], prefix);
          }
        }
      }

      final Iterator resultIter = processor.getResults().iterator();
      while(resultIter != null && resultIter.hasNext()){
        final CompletionElement comElement = (CompletionElement) resultIter.next();
        final LookupItem lookupItem = addLookupItem(set, item, comElement.getElement(), prefix);
        lookupItem.setAttribute(LookupItem.SUBSTITUTOR, comElement.getSubstitutor());
        if (comElement.getQualifier() != null){
          CompletionUtil.setQualifierType(lookupItem, comElement.getQualifier());
        }
      }
    }
  }

  private static class Scope{
    Class myClass;
    boolean myFinalFlag;

    Scope(Class aClass, boolean flag){
      myClass = aClass;
      myFinalFlag = flag;
    }
  }

  protected static class CompletionVariantItem{
    public Object myCompletion;
    public int myTailType;

    CompletionVariantItem(Object completion, int tailtype){
      myCompletion = completion;
      myTailType = tailtype;
    }

    public String toString(){
      return myCompletion.toString();
    }
  }

  public String toString(){
    return "completion variant at " + myPosition.toString() + " completions: " + myCompletionsList;
  }

  public boolean isCaseInsensitive() {
    return caseInsensitive;
  }

  public void setCaseInsensitive(boolean caseInsensitive) {
    this.caseInsensitive = caseInsensitive;
  }
}
