package com.intellij.psi.filters.position;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.*;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NonNls;

public class NamespaceFilter implements ElementFilter {
  private final String[] myNamespaces;

  public NamespaceFilter(@NonNls String... namespaces){
    myNamespaces = namespaces;
  }

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(XmlTag.class, hintClass) || ReflectionCache.isAssignable(XmlDocument.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof XmlTag){
      final XmlTag psiElement = (XmlTag)element;
      if (!psiElement.isValid()) return false;
      String ns = psiElement.getNamespace();

      if(ns.length() > 0){
        for (String aMyValue : myNamespaces) {
          if (aMyValue.equals(ns)) return true;
        }
      }

      final PsiFile psiFile = psiElement.getContainingFile();
      if (psiFile instanceof XmlFile) {
        // We use file references for as dtd namespace
        // But we should also check PUBLIC ID for namespace
        final XmlProlog prolog = ((XmlFile)psiFile).getDocument().getProlog();

        if (prolog != null) {
          final XmlDoctype doctype = prolog.getDoctype();
          if (doctype != null) {
            final String publicId = doctype.getPublicId();

            if (publicId != null) {
              for (String aMyValue : myNamespaces) {
                if (aMyValue.equals(publicId)) {
                  return true;
                }
              }
            }
          }
        }
      }
    }
    else if(element instanceof XmlDocument){
      return isAcceptable(((XmlDocument) element).getRootTag(), context);
    }
    return false;
  }
}
