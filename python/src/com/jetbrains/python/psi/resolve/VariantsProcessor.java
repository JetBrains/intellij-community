package com.jetbrains.python.psi.resolve;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.jetbrains.python.codeInsight.PyClassInsertHandler;
import com.jetbrains.python.codeInsight.PyFunctionInsertHandler;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class VariantsProcessor implements PsiScopeProcessor {
  private final Map<String, LookupElement> myVariants = new HashMap<String, LookupElement>();

  protected final PsiElement myContext;
  protected String myNotice;
  protected Condition<PsiElement> myNodeFilter;
  protected Condition<String> myNameFilter;

  protected boolean myPlainNamesOnly = false; // if true, add insert handlers to known things like functions
  private List<String> myAllowedNames;

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


  protected LookupElementBuilder setupItem(LookupElementBuilder item) {
    if (!myPlainNamesOnly) {
      if (item.getObject() instanceof PyFunction && ((PyFunction) item.getObject()).getProperty() == null &&
          !isSingleArgDecoratorCall(myContext, (PyFunction)item.getObject())) {
        item = item.setInsertHandler(PyFunctionInsertHandler.INSTANCE);
        final PyParameterList parameterList = ((PyFunction)item.getObject()).getParameterList();
        final String params = StringUtil.join(parameterList.getParameters(), new Function<PyParameter, String>() {
            @Override
            public String fun(PyParameter pyParameter) {
              return pyParameter.getText();
            }
          }, ", ");
        item = item.setTailText("(" + params + ")");
      }
      else if (item.getObject() instanceof PyClass) {
        item = item.setInsertHandler(PyClassInsertHandler.INSTANCE);
      }
    }
    if (myNotice != null) {
      return setItemNotice(item, myNotice);
    }
    return item;
  }

  private static boolean isSingleArgDecoratorCall(PsiElement elementInCall, PyFunction callee) {
    if (callee.getParameterList().getParameters().length > 1) {
      return false;
    }
    PyDecorator decorator = PsiTreeUtil.getParentOfType(elementInCall, PyDecorator.class);
    if (decorator == null) {
      return false;
    }
    return PsiTreeUtil.isAncestor(decorator.getCallee(), elementInCall, false);
  }

  protected static LookupElementBuilder setItemNotice(final LookupElementBuilder item, String notice) {
    return item.setTypeText(notice);
  }

  public LookupElement[] getResult() {
    final Collection<LookupElement> variants = myVariants.values();
    return variants.toArray(new LookupElement[variants.size()]);
  }

  public List<LookupElement> getResultList() {
    return new ArrayList<LookupElement>(myVariants.values());
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
        myVariants.put(name, setupItem(LookupElementBuilder.create(psiNamedElement, name).setIcon(element.getIcon(0))));
      }
    }
    else if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      String referencedName = expr.getReferencedName();
      if (nameIsAcceptable(referencedName)) {
        myVariants.put(referencedName, setupItem(LookupElementBuilder.create(expr, referencedName)));
      }
    }
    else if (element instanceof NameDefiner) {
      boolean handled_as_imported = false;
      if (element instanceof PyImportElement) {
        PyReferenceExpression ref = ((PyImportElement)element).getImportReference();
        if (ref != null && ref.getQualifier() == null) {
          String name = ref.getName();
          if (name != null && nameIsAcceptable(name)) {
            PsiElement resolved = ref.getReference().resolve();
            if (resolved instanceof PsiNamedElement) {
              handled_as_imported = true;
              myVariants.put(name, setupItem(LookupElementBuilder.create(resolved, name).setIcon(resolved.getIcon(0))));
            }
          }
        }
      }
      if (! handled_as_imported) {
        final NameDefiner definer = (NameDefiner)element;
        for (PyElement expr : definer.iterateNames()) {
          if (expr != null && expr != myContext) { // NOTE: maybe rather have SingleIterables skip nulls outright?
            String referencedName = expr.getName();
            Icon icon = expr.getIcon(0);
            // things like PyTargetExpression cannot have a general icon, but here we only have variables
            if (icon == null) icon = Icons.VARIABLE_ICON;
            if (referencedName != null && nameIsAcceptable(referencedName)) {
              LookupElementBuilder lookupItem = setupItem(LookupElementBuilder.create(expr, referencedName).setIcon(icon));
              if (definer instanceof PyImportElement) { // set notice to imported module name if needed
                PsiElement maybeFromImport = definer.getParent();
                if (maybeFromImport instanceof PyFromImportStatement) {
                  final PyFromImportStatement fromImport = (PyFromImportStatement)maybeFromImport;
                  PyReferenceExpression src = fromImport.getImportSource();
                  if (src != null) {
                    lookupItem = setItemNotice(lookupItem, src.getName());
                  }
                }
              }
              if (definer instanceof PyAssignmentStatement) {
                PyExpression value = ((PyAssignmentStatement)definer).getAssignedValue();
                if (value != null) {
                  PyType type = value.getType(TypeEvalContext.fast());
                  if (type != null) {
                    lookupItem = lookupItem.setTypeText(type.getName());
                  }
                }
              }
              myVariants.put(referencedName, lookupItem);
            }
          }
        }
      }
    }

    return true;
  }

  private boolean nameIsAcceptable(String name) {
    if (name == null) {
      return false;
    }
    if (myVariants.containsKey(name)) {
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
