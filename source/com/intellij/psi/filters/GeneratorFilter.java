package com.intellij.psi.filters;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;

import java.lang.ref.SoftReference;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 19:55:15
 * To change this template use Options | File Templates.
 */
public class GeneratorFilter implements ElementFilter{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.filters.GeneratorFilter");
  private ContextGetter myGetter;
  private Class myFilterClass;

  public GeneratorFilter(Class filterClass, ContextGetter getter){
    myFilterClass = filterClass;
    myGetter = getter;
  }

  public boolean isClassAcceptable(Class hintClass){
    final ElementFilter filter = getFilter();
    if(filter != null){
      return filter.isClassAcceptable(hintClass);
    }
    return true;
  }


  private SoftReference myCachedElement = new SoftReference(null);
  private SoftReference myCachedFilter = new SoftReference(null);

  private ElementFilter getFilter(){
    return (ElementFilter) myCachedFilter.get();
  }

  protected ElementFilter getFilter(PsiElement context){
    ElementFilter filter = (ElementFilter)myCachedFilter.get();
    if(myCachedElement.get() != context || filter == null){
      filter = generateFilter(context);
      myCachedFilter = new SoftReference(filter);
      myCachedElement = new SoftReference(context);
    }
    return filter;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element == null) return false;
    final ElementFilter filter = getFilter(context);
    if(filter != null){
      return filter.isAcceptable(element, context);
    }
    return false;
  }

  protected ElementFilter generateFilter(PsiElement context){
    try{
      final ElementFilter elementFilter = (ElementFilter) myFilterClass.newInstance();
      final Object[] initArgument = myGetter.get(context, null);
      if(InitializableFilter.class.isAssignableFrom(myFilterClass) && initArgument != null){
        ((InitializableFilter)elementFilter).init(initArgument);
        return elementFilter;
      }
      else{
        LOG.error("Filter initialization failed!");
      }
    }
    catch(InstantiationException e){
      LOG.error(e);
    }
    catch(IllegalAccessException e){
      LOG.error(e);
    }
    return null;
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    throw new InvalidDataException("Not implemented yet!");
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }
}
