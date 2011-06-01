package com.jetbrains.python.psi.resolve;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
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

/**
 * @author yole
 */
public class CompletionVariantsProcessor extends VariantsProcessor {
  private final Map<String, LookupElement> myVariants = new HashMap<String, LookupElement>();

  public CompletionVariantsProcessor(PsiElement context) {
    super(context);
  }

  public CompletionVariantsProcessor(PsiElement context,
                                     @Nullable Condition<PsiElement> nodeFilter,
                                     @Nullable Condition<String> nameFilter) {
    super(context, nodeFilter, nameFilter);
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

  @Override
  protected void addElement(String name, PsiElement element) {
    myVariants.put(name, setupItem(LookupElementBuilder.create(element, name).setIcon(element.getIcon(0))));
  }

  protected void addImportedElement(String referencedName, NameDefiner definer, PyElement expr) {
    Icon icon = expr.getIcon(0);
    // things like PyTargetExpression cannot have a general icon, but here we only have variables
    if (icon == null) icon = Icons.VARIABLE_ICON;
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
