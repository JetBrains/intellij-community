/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager implements ApplicationComponent {
  private Map<Class, List<DomElementsAnnotator>> myClass2Annotator = new HashMap<Class, List<DomElementsAnnotator>>();

  private Map<DomFileElement, CachedValue<DomElementsProblemsHolder>> myCache =
    new HashMap<DomFileElement, CachedValue<DomElementsProblemsHolder>>();

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement) {
     return getProblems(domElement, false);
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement, boolean includeXmlProblems) {
    return getProblems(domElement, includeXmlProblems, true);
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,  boolean includeXmlProblems, boolean withChildren) {
    if(domElement == null) return Collections.emptyList();
    
    if (myCache.get(domElement.getRoot()) == null) {
      myCache.put(domElement.getRoot(), getCachedValue(domElement));
    }

    return  myCache.get(domElement.getRoot()).getValue().getProblems(domElement, includeXmlProblems, withChildren);
  }

  private CachedValue<DomElementsProblemsHolder> getCachedValue(final DomElement domElement) {
    final CachedValuesManager cachedValuesManager = PsiManager.getInstance(domElement.getManager().getProject()).getCachedValuesManager();

    return cachedValuesManager.createCachedValue(new CachedValueProvider<DomElementsProblemsHolder>() {
      public Result<DomElementsProblemsHolder> compute() {
        final DomElementsProblemsHolder holder = new DomElementsProblemsHolderImpl();

        final List<DomElementsAnnotator> annotators = getDomElementsAnnotators(domElement);

        for (DomElementsAnnotator annotator : annotators) {
          annotator.annotate((DomElement)domElement.getRoot().getRootElement(), holder);
        }

        return new Result<DomElementsProblemsHolder>(holder, domElement.getRoot().getFile());
      }
    }, false);
  }

  public List<DomElementsAnnotator> getDomElementsAnnotators(DomElement domElement) {
    final Class key = getRootElementClass(domElement);

    return myClass2Annotator.get(key) == null ? new ArrayList<DomElementsAnnotator>() : myClass2Annotator.get(key);
  }


  public void registerDomElementsAnnotator(DomElementsAnnotator annotator, Class aClass) {
    final List<DomElementsAnnotator> annotators =
      myClass2Annotator.get(aClass) == null ? new ArrayList<DomElementsAnnotator>() : myClass2Annotator.get(aClass);

    annotators.add(annotator);

    myClass2Annotator.put(aClass, annotators);
  }

  private static Class getRootElementClass(DomElement domElement) {
    final DomElement rootElement = (DomElement)domElement.getRoot().getRootElement();

    return (Class)rootElement.getDomElementType();
  }

  @NonNls
  public String getComponentName() {
    return "DomElementAnnotationsManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
