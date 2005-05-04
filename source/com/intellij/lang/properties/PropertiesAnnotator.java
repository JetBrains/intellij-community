package com.intellij.lang.properties;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.psi.PsiElement;

import java.util.Collection;

/**
 * @author cdr
 */
class PropertiesAnnotator implements Annotator {
  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (!(element instanceof Property)) return;
    final Property origProperty = (Property)element;
    PropertiesFile propertiesFile = (PropertiesFile)element.getContainingFile();
    Collection<Property> others = propertiesFile.findPropertiesByKey(origProperty.getKey());
    if (others.size() != 1) {
      Annotation annotation = holder.createErrorAnnotation(((PropertyImpl)origProperty).getKeyNode(), "Duplicate property key");
      annotation.registerFix(new RemovePropertyFix(origProperty));
    }
  }
}
