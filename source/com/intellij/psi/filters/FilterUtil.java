package com.intellij.psi.filters;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspToken;
import com.intellij.psi.filters.classes.AnyInnerFilter;
import com.intellij.psi.filters.classes.InheritorFilter;
import com.intellij.psi.filters.classes.InterfaceFilter;
import com.intellij.psi.filters.classes.ThisOrAnyInnerFilter;
import com.intellij.psi.filters.element.IsAccessibleFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.element.PackageEqualsFilter;
import com.intellij.psi.filters.position.PreviousElementFilter;
import com.intellij.psi.filters.position.StartElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jdom.Element;
import org.jdom.Namespace;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 17:45:45
 * To change this template use Options | File Templates.
 */
public class FilterUtil{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionData");
  public static final Namespace FILTER_NS = Namespace.getNamespace("http://www.intellij.net/data/filter");

  private static final Map<String,Class> ourRegisteredFilters = new HashMap<String, Class>();
  static{
    registerFilter("and", AndFilter.class);
    registerFilter("or", OrFilter.class);
    registerFilter("any-inner", AnyInnerFilter.class);
    registerFilter("assignable", InheritorFilter.class);
    registerFilter("class", ClassFilter.class);
    registerFilter("constructor", AndFilter.class);
    registerFilter("interface", InterfaceFilter.class);
    registerFilter("accessible-class", IsAccessibleFilter.class);
    registerFilter("modifiers", ModifierFilter.class);
    registerFilter("not", NotFilter.class);
    registerFilter("same-package", PackageEqualsFilter.class);
    registerFilter("previous", PreviousElementFilter.class);
    registerFilter("parent", ScopeFilter.class);
    registerFilter("text", TextFilter.class);
    registerFilter("first-classes", StartElementFilter.class);
    registerFilter("this-or-any-inner", ThisOrAnyInnerFilter.class);
  }

  static void registerFilter(String name, Class filterClass){
    ourRegisteredFilters.put(name, filterClass);
  }

  public static ElementFilter readFilter(Element element)
  throws UnknownFilterException{
    final String filterName = element.getName();

      if(ourRegisteredFilters.containsKey(filterName)){

        final ElementFilter filter;
        try{
          filter = (ElementFilter)(ourRegisteredFilters.get(filterName)).newInstance();
          //filter.readExternal(element);
        }
        catch(InstantiationException e){
          throw new UnknownFilterException(filterName, e);
        }
        catch(IllegalAccessException e){
          throw new UnknownFilterException(filterName, e);
        }

        return filter;
      }
      else{
        throw new UnknownFilterException(filterName);
      }
  }

  public static List<ElementFilter> readFilterGroup(Element element){
    final List<ElementFilter> list = new ArrayList<ElementFilter>();
    final Iterator filtersIterator = element.getChildren().iterator();
    while(filtersIterator.hasNext()){
      final Element current = (Element)filtersIterator.next();
      if(current.getNamespace().equals(FILTER_NS)){
        try{
          list.add(FilterUtil.readFilter(current));
        }
        catch(UnknownFilterException ufe){
          LOG.error(ufe);
        }
      }
    }
    return list;
  }

  public static final Class getClassByName(String shortName){
    final String[] packs = {
      "",
      "com.intellij.psi.",
      "com.intellij.psi.jsp.",
      "com.intellij.psi.xml.",
      "com.intellij.aspects.psi."
    };

    for(int i = 0; i < packs.length; i++){
      final Class aClass = tryClass(packs[i] + shortName);
      if(aClass != null){
        return aClass;
      }
    }
    return null;
  }

  private static Class tryClass(String name){
    try{
      return Class.forName(name);
    }
    catch(Exception e){
      return null;
    }
  }

  public static PsiType getTypeByElement(PsiElement element, PsiElement context){
    //if(!element.isValid()) return null;
    if(element instanceof PsiType){
      return (PsiType)element;
    }
    if(element instanceof PsiClass){
      return element.getManager().getElementFactory().createType((PsiClass)element);
    }
    else if(element instanceof PsiMethod){
      return ((PsiMethod)element).getReturnType();
    }
    else if(element instanceof PsiVariable){
      return ((PsiVariable)element).getType();
    }
    else if(element instanceof PsiKeyword){
      if("class".equals(element.getText())){
        return PsiType.getJavaLangClass(element.getManager(), element.getResolveScope());
      }
      else if("true".equals(element.getText()) || "false".equals(element.getText())){
        return PsiType.BOOLEAN;
      }
      else if("this".equals(element.getText())){
        PsiElement previousElement = getPreviousElement(context, false);
        if(".".equals(previousElement.getText())){
          previousElement = getPreviousElement(previousElement, false);
          final String className = previousElement.getText();
          PsiElement walker = context;
          while(walker != null){
            if(walker instanceof PsiClass && !(walker instanceof PsiAnonymousClass)){
              if(className.equals(((PsiClass)walker).getName()))
                return getTypeByElement(walker, context);
            }
            walker = walker.getContext();
          }
        }
        else{
          final PsiClass owner = PsiTreeUtil.getContextOfType(context, PsiClass.class, true);
          return getTypeByElement(owner, context);
        }
      }
    }
    else if(element instanceof PsiExpression){
      return ((PsiExpression)element).getType();
    }

    return null;
  }

  public static PsiElement searchNonSpaceNonCommentBack(PsiElement element) {
    if(element == null) return null;
    while (true) {
      int offset = element.getTextRange().getStartOffset() - 1;
      if (offset < 0) return null;
      element = element.getContainingFile().findElementAt(offset);
      if (element == null) return null;
      if(element instanceof JspToken && ((JspToken)element).getTokenType() == JspToken.JSP_DIRECTIVE_WHITE_SPACE) continue;
      if (!(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)) return element;
    }
  }

  public static final PsiElement getPreviousElement(final PsiElement element, boolean skipReference){
    PsiElement prev = element;
    if(element != null){
      if(skipReference){
        prev = searchNonSpaceNonCommentBack(element);
        while(prev != null && prev.getParent() instanceof PsiJavaCodeReferenceElement){
          prev = searchNonSpaceNonCommentBack(prev.getParent());
        }
      }
      else{
        prev = searchNonSpaceNonCommentBack(prev);
      }
    }
    return prev;
  }
}
