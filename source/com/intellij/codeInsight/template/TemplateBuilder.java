package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author mike
 */
public class TemplateBuilder {
  private PsiElement myContainerElement;
  private Map<PsiElement,Expression> myExpressions = new HashMap<PsiElement, Expression>();
  private Map<PsiElement,String> myVariableExpressions = new HashMap<PsiElement, String>();
  private Map<PsiElement, Boolean> myAlwaysStopAtMap = new HashMap<PsiElement, Boolean>();
  private Map<PsiElement, String> myVariableNamesMap = new HashMap<PsiElement, String>();
  private Set<PsiElement> myElements = new TreeSet<PsiElement>(new Comparator<PsiElement>() {
    public int compare(final PsiElement e1, final PsiElement e2) {
      return e1.getTextRange().getStartOffset() - e2.getTextRange().getStartOffset();
    }
  });

  private PsiElement myEndElement;
  private PsiElement mySelection;

  public TemplateBuilder(PsiElement element) {
    myContainerElement = element;
  }

  public void replaceElement(PsiElement element, Expression expression, boolean alwaysStopAt) {
    myAlwaysStopAtMap.put(element, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    replaceElement(element, expression);
  }

  public void replaceElement(PsiElement element, String varName, Expression expression, boolean alwaysStopAt) {
    myAlwaysStopAtMap.put(element, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    myVariableNamesMap.put(element, varName);
    replaceElement(element, expression);
  }

  public void replaceElement (PsiElement element, String varName, String dependantVariableName, boolean alwaysStopAt) {
    myAlwaysStopAtMap.put(element, alwaysStopAt ? Boolean.TRUE : Boolean.FALSE);
    myVariableNamesMap.put(element, varName);
    myVariableExpressions.put(element, dependantVariableName);
    myElements.add(element);
  }

  public void replaceElement(PsiElement element, Expression expression) {
    myExpressions.put(element, expression);
    myElements.add(element);
  }

  /**
   * Adds end variable after the specified element
   */
  public void setEndVariableAfter(PsiElement element) {
    element = element.getNextSibling();
    myEndElement = element;
    myElements.add(element);
  }

  public void setSelection(PsiElement element) {
    mySelection = element;
    myElements.add(element);
  }

  public Template buildInlineTemplate() {
    Template template = buildTemplate();
    template.setInline(true);
    return template;
  }

  public Template buildTemplate() {
    TemplateManager manager = TemplateManager.getInstance(myContainerElement.getProject());
    final Template template = manager.createTemplate("", "");

    String text = myContainerElement.getText();
    final int containerStart = myContainerElement.getTextRange().getStartOffset();
    int start = 0;
    for (Iterator<PsiElement> iterator = myElements.iterator(); iterator.hasNext();) {
      final PsiElement element = iterator.next();
      int offset = element.getTextRange().getStartOffset() - containerStart;
      template.addTextSegment(text.substring(start, offset));

      if (element == mySelection) {
        template.addSelectionStartVariable();
        template.addTextSegment(mySelection.getText());
        template.addSelectionEndVariable();
      } else if (element == myEndElement) {
        template.addEndVariable();
        start = offset;
        continue;
      } else {
        final boolean alwaysStopAt = myAlwaysStopAtMap.get(element) == null ? true : myAlwaysStopAtMap.get(element).booleanValue();
        final Expression expression = myExpressions.get(element);
        final String variableName = myVariableNamesMap.get(element) == null ? String.valueOf(expression.hashCode()) : myVariableNamesMap.get(element);

        if (expression != null) {
          template.addVariable(variableName, expression, expression, alwaysStopAt);
        } else {
          template.addVariableSegment(variableName);
        }
      }

      start = element.getTextRange().getEndOffset() - containerStart;
    }

    template.addTextSegment(text.substring(start));

    for (Iterator<PsiElement> iterator1 = myElements.iterator(); iterator1.hasNext();) {
      PsiElement element = iterator1.next();
      final String dependantVariable = myVariableExpressions.get(element);
      if (dependantVariable != null) {
        final boolean alwaysStopAt = myAlwaysStopAtMap.get(element) == null ? true : myAlwaysStopAtMap.get(element).booleanValue();
        final Expression expression = myExpressions.get(element);
        final String variableName = myVariableNamesMap.get(element) == null
          ? String.valueOf(expression.hashCode())
          : myVariableNamesMap.get(element);
        template.addVariable(variableName, dependantVariable, dependantVariable, alwaysStopAt);
      }
    }

    template.setToIndent(false);
    template.setToReformat(false);

    return template;
  }
}
