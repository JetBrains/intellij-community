package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class VariantsProcessor implements PsiScopeProcessor {
  protected final PsiElement myContext;
  protected String myNotice;
  protected Condition<PsiElement> myNodeFilter;
  protected Condition<String> myNameFilter;

  protected boolean myPlainNamesOnly = false; // if true, add insert handlers to known things like functions
  private List<String> myAllowedNames;
  private final List<String> mySeenNames = new ArrayList<String>();

  public VariantsProcessor(PsiElement context) {
    // empty
    myContext = context;
  }

  public VariantsProcessor(PsiElement context, @Nullable final Condition<PsiElement> nodeFilter, @Nullable final Condition<String> nameFilter) {
    myContext = context;
    myNodeFilter = nodeFilter;
    myNameFilter = nameFilter;
  }

  public void setNotice(@Nullable String notice) {
    myNotice = notice;
  }

  public boolean isPlainNamesOnly() {
    return myPlainNamesOnly;
  }

  public void setPlainNamesOnly(boolean plainNamesOnly) {
    myPlainNamesOnly = plainNamesOnly;
  }


  public boolean execute(PsiElement element, ResolveState substitutor) {
    if (myNodeFilter != null && !myNodeFilter.value(element)) return true; // skip whatever the filter rejects
    // TODO: refactor to look saner; much code duplication
    if (element instanceof PsiNamedElement) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      final String name = psiNamedElement instanceof PyFile
                          ? FileUtil.getNameWithoutExtension(((PyFile)psiNamedElement).getName())
                          : psiNamedElement.getName();
      if (name != null && nameIsAcceptable(name)) {
        addElement(name, psiNamedElement);
      }
    }
    else if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      String referencedName = expr.getReferencedName();
      if (nameIsAcceptable(referencedName)) {
        addElement(referencedName, expr);
      }
    }
    else if (element instanceof NameDefiner) {
      boolean handled_as_imported = false;
      if (element instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement)element;
        PyReferenceExpression ref = importElement.getImportReference();
        if (ref != null && ref.getQualifier() == null) {
          String name = importElement.getAsName() != null ? importElement.getAsName() : ref.getName();
          if (name != null && nameIsAcceptable(name)) {
            PsiElement resolved = ref.getReference().resolve();
            if (resolved instanceof PsiNamedElement) {
              handled_as_imported = true;
              addElement(name, resolved);
            }
          }
        }
      }
      if (! handled_as_imported) {
        final NameDefiner definer = (NameDefiner)element;
        for (PyElement expr : definer.iterateNames()) {
          if (expr != null && expr != myContext) { // NOTE: maybe rather have SingleIterables skip nulls outright?
            String referencedName = expr.getName();
            if (referencedName != null && nameIsAcceptable(referencedName)) {
              addImportedElement(referencedName, definer, expr);
            }
          }
        }
      }
    }

    return true;
  }

  protected void addElement(String name, PsiElement psiNamedElement) {
    mySeenNames.add(name);
  }

  protected void addImportedElement(String referencedName, NameDefiner definer, PyElement expr) {
    addElement(referencedName, expr);
  }

  private boolean nameIsAcceptable(String name) {
    if (name == null) {
      return false;
    }
    if (mySeenNames.contains(name)) {
      return false;
    }
    if (myNameFilter != null && !myNameFilter.value(name)) {
      return false;
    }
    if (myAllowedNames != null && !myAllowedNames.contains(name)) {
      return false;
    }
    return true;
  }

  @Nullable
  public <T> T getHint(Key<T> hintKey) {
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

  public void setAllowedNames(List<String> namesFilter) {
    myAllowedNames = namesFilter;
  }
}
