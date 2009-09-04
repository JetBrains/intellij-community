package com.jetbrains.python.psi.resolve;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VariantsProcessor implements PsiScopeProcessor {
  private final Map<String, LookupElement> myVariants = new HashMap<String, LookupElement>();

  protected String my_notice;

  public VariantsProcessor() {
    // empty
  }

  public VariantsProcessor(final PyResolveUtil.Filter filter) {
    my_filter = filter;
  }

  protected PyResolveUtil.Filter my_filter;

  public void setNotice(@Nullable String notice) {
    my_notice = notice;
  }

  protected LookupElementBuilder setupItem(LookupElementBuilder item) {
    if (my_notice != null) {
      return setItemNotice(item, my_notice);
    }
    return item;
  }

  protected static LookupElementBuilder setItemNotice(final LookupElementBuilder item, String notice) {
    return item.setTailText(notice);
  }

  public LookupElement[] getResult() {
    final Collection<LookupElement> variants = myVariants.values();
    return variants.toArray(new LookupElement[variants.size()]);
  }

  public List<LookupElement> getResultList() {
    return new ArrayList<LookupElement>(myVariants.values());
  }

  public boolean execute(PsiElement element, ResolveState substitutor) {
    if (my_filter != null && !my_filter.accept(element)) return true; // skip whatever the filter rejects
    // TODO: refactor to look saner; much code duplication
    if (element instanceof PsiNamedElement) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      final String name = psiNamedElement.getName();
      if (!myVariants.containsKey(name)) {
        myVariants.put(name, setupItem(LookupElementBuilder.create(psiNamedElement)));
      }
    }
    else if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      String referencedName = expr.getReferencedName();
      if (referencedName != null && !myVariants.containsKey(referencedName)) {
        myVariants.put(referencedName, setupItem(LookupElementBuilder.create(referencedName)));
      }
    }
    else if (element instanceof NameDefiner) {
      final NameDefiner definer = (NameDefiner)element;
      for (PyElement expr : definer.iterateNames()) {
        if (expr != null) { // NOTE: maybe rather have SingleIterables skip nulls outright?
          String referencedName = expr.getName();
          if (referencedName != null && !myVariants.containsKey(referencedName)) {
            LookupElementBuilder lookup_item = setupItem(LookupElementBuilder.create(referencedName));
            if (definer instanceof PyImportElement) { // set notice to imported module name if needed
              PsiElement maybe_from_import = definer.getParent();
              if (maybe_from_import instanceof PyFromImportStatement) {
                final PyFromImportStatement from_import = (PyFromImportStatement)maybe_from_import;
                PyReferenceExpression src = from_import.getImportSource();
                if (src != null) {
                  lookup_item = setItemNotice(lookup_item, " | " + src.getName());
                }
              }
            }
            myVariants.put(referencedName, lookup_item);
          }
        }
      }
    }

    return true;
  }

  @Nullable
  public <T> T getHint(Key<T> hintKey) {
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

}
