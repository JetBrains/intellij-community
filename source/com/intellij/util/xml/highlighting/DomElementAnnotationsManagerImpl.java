/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.containers.WeakValueHashMap;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.PsiManager;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager implements ApplicationComponent {
  private Map<Class, List<DomElementsAnnotator>> myClass2Annotator = new HashMap<Class, List<DomElementsAnnotator>>();

  private Map<DomFileElement, CachedValue<DomElementsProblemsHolder>> myCache =
    new WeakValueHashMap<DomFileElement, CachedValue<DomElementsProblemsHolder>>();

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement) {
    return getProblems(domElement, false);
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement, boolean includeXmlProblems) {
    return getProblems(domElement, includeXmlProblems, true, HighlightSeverity.ERROR);
  }

  /**
   * Result is: Errors and Warnings
   * @param domElement
   * @param includeXmlProblems
   * @param withChildren
   * @return
   */
  public List<DomElementProblemDescriptor> getProblems(DomElement domElement, boolean includeXmlProblems, boolean withChildren) {
    if (domElement == null || !domElement.isValid()) return Collections.emptyList();

    final DomElementsProblemsHolder holder = getDomElementsProblemsHolder(domElement.getRoot());

    return holder.getProblems(domElement, includeXmlProblems, withChildren);
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       boolean includeXmlProblems,
                                                       boolean withChildren,
                                                       HighlightSeverity minSeverity) {
    if (domElement == null || !domElement.isValid()) return Collections.emptyList();

    final DomFileElement<?> fileElement = domElement.getRoot();
    final DomElementsProblemsHolder holder = getDomElementsProblemsHolder(fileElement);

    return holder.getProblems(domElement, includeXmlProblems, withChildren, minSeverity);
  }

  public List<DomElementProblemDescriptor> getAllProblems(final DomFileElement<?> fileElement, HighlightSeverity minSeverity) {
    return getDomElementsProblemsHolder(fileElement).getAllProblems();
  }

  public DomElementsProblemsHolder getDomElementsProblemsHolder(final DomFileElement<?> fileElement) {
    if (myCache.get(fileElement) == null) {
      myCache.put(fileElement, getCachedValue(fileElement));
    }

    return myCache.get(fileElement).getValue();
  }

  private CachedValue<DomElementsProblemsHolder> getCachedValue(final DomFileElement fileElement) {
    final CachedValuesManager cachedValuesManager = PsiManager.getInstance(fileElement.getManager().getProject()).getCachedValuesManager();

    return cachedValuesManager.createCachedValue(new CachedValueProvider<DomElementsProblemsHolder>() {
      public Result<DomElementsProblemsHolder> compute() {
        final DomElementsProblemsHolder holder = new DomElementsProblemsHolderImpl();
        final DomElement rootElement = fileElement.getRootElement();
        final Class<?> type = DomUtil.getRawType(rootElement.getDomElementType());
        for (DomElementsAnnotator annotator : getOrCreateAnnotators(type)) {
          annotator.annotate(rootElement, holder);
        }
        return new Result<DomElementsProblemsHolder>(holder, fileElement.getFile());
      }
    }, false);
  }


  public void registerDomElementsAnnotator(DomElementsAnnotator annotator, Class aClass) {
    final List<DomElementsAnnotator> annotators = getOrCreateAnnotators(aClass);

    annotators.add(annotator);

    myClass2Annotator.put(aClass, annotators);
  }

  private List<DomElementsAnnotator> getOrCreateAnnotators(final Class aClass) {
    return myClass2Annotator.get(aClass) == null ? new ArrayList<DomElementsAnnotator>() : myClass2Annotator.get(aClass);
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
