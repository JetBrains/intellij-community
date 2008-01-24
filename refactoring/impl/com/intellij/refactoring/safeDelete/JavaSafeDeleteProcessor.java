package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.usageInfo.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaSafeDeleteProcessor implements SafeDeleteProcessorDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor");

  public boolean handlesElement(final PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod ||
           element instanceof PsiField || element instanceof PsiParameter;
  }

  @Nullable
  public Condition<PsiElement> findUsages(final PsiElement element, final PsiElement[] allElementsToDelete, final List<UsageInfo> usages) {
    if (element instanceof PsiClass) {
      findClassUsages((PsiClass) element, allElementsToDelete, usages);
      if (element instanceof PsiTypeParameter) {
        findTypeParameterExternalUsages((PsiTypeParameter)element, usages);
      }
      return getUsageInsideDeletedFilter(allElementsToDelete);
    }
    else if (element instanceof PsiMethod) {
      return findMethodUsages((PsiMethod) element, allElementsToDelete, usages);
    }
    else if (element instanceof PsiField) {
      return findFieldUsages((PsiField)element, usages, allElementsToDelete);
    }
    else if (element instanceof PsiParameter) {
      LOG.assertTrue(((PsiParameter) element).getDeclarationScope() instanceof PsiMethod);
      findParameterUsages((PsiParameter)element, usages);
      return getUsageInsideDeletedFilter(allElementsToDelete);
    }
    assert false: "findUsages() got element for which handlesElement() returned false";
    return null;
  }

  public static Condition<PsiElement> getUsageInsideDeletedFilter(final PsiElement[] allElementsToDelete) {
    return new Condition<PsiElement>() {
      public boolean value(final PsiElement usage) {
        return !(usage instanceof PsiFile) && isInside(usage, allElementsToDelete);
      }
    };
  }

  private static void findClassUsages(final PsiClass psiClass, final PsiElement[] allElementsToDelete, final List<UsageInfo> usages) {
    final boolean justPrivates = containsOnlyPrivates(psiClass);

    ReferencesSearch.search(psiClass).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        final PsiElement element = reference.getElement();

        if (!isInside(element, allElementsToDelete)) {
          PsiElement parent = element.getParent();
          if (parent instanceof PsiReferenceList) {
            final PsiElement pparent = parent.getParent();
            if (pparent instanceof PsiClass) {
              final PsiClass inheritor = (PsiClass) pparent;
              //If psiClass contains only private members, then it is safe to remove it and change inheritor's extends/implements accordingly
              if (justPrivates) {
                if (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList())) {
                  usages.add(new SafeDeleteExtendsClassUsageInfo((PsiJavaCodeReferenceElement)element, psiClass, inheritor));
                  return true;
                }
              }
            }
          }
          LOG.assertTrue(element.getTextRange() != null);
          usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(element, psiClass, parent instanceof PsiImportStatement));
        }
        return true;
      }
    });
  }

  private static boolean containsOnlyPrivates(final PsiClass aClass) {
    final PsiField[] fields = aClass.getFields();
    for (PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    }

    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    }

    final PsiClass[] inners = aClass.getInnerClasses();
    for (PsiClass inner : inners) {
      if (!inner.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    }

    return true;
  }

  private static void findTypeParameterExternalUsages(final PsiTypeParameter typeParameter, final Collection<UsageInfo> usages) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    final int index = owner.getTypeParameterList().getTypeParameterIndex(typeParameter);

    ReferencesSearch.search(owner).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        if (reference instanceof PsiJavaCodeReferenceElement) {
          PsiTypeElement[] typeArgs = ((PsiJavaCodeReferenceElement)reference).getParameterList().getTypeParameterElements();
          if (typeArgs.length > index) {
            usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(typeArgs[index], typeParameter, true));
          }
        }
        return true;
      }
    });
  }

  @Nullable
  private static Condition<PsiElement> findMethodUsages(PsiMethod psiMethod, final PsiElement[] allElementsToDelete, List<UsageInfo> usages) {
    final Collection<PsiReference> references = ReferencesSearch.search(psiMethod).findAll();

    if(psiMethod.isConstructor()) {
      return findConstructorUsages(psiMethod, references, usages, allElementsToDelete);
    }
    final PsiMethod[] overridingMethods =
            removeDeletedMethods(OverridingMethodsSearch.search(psiMethod, psiMethod.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY),
                                 allElementsToDelete);

    boolean anyRefs = false;
    for (PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods)) {
        usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(element, psiMethod, false));
        anyRefs = true;
      }
    }

    final Condition<PsiElement> usageInsideDeleted;
    if (!anyRefs) {
      HashMap<PsiMethod, Collection<PsiReference>> methodToReferences = new HashMap<PsiMethod, Collection<PsiReference>>();
      for (PsiMethod overridingMethod : overridingMethods) {
        final Collection<PsiReference> overridingReferences = ReferencesSearch.search(overridingMethod).findAll();
        methodToReferences.put(overridingMethod, overridingReferences);
      }
      final Set<PsiMethod> validOverriding =
              validateOverridingMethods(psiMethod, references, Arrays.asList(overridingMethods), methodToReferences, usages,
                                        allElementsToDelete);
      usageInsideDeleted = new Condition<PsiElement>() {
        public boolean value(PsiElement usage) {
          if(usage instanceof PsiFile) return false;
          return isInside(usage, allElementsToDelete) || isInside(usage,  validOverriding);
        }
      };
    }
    else {
      usageInsideDeleted = getUsageInsideDeletedFilter(allElementsToDelete);
    }

    return usageInsideDeleted;
  }

  private static PsiMethod[] removeDeletedMethods(PsiMethod[] methods, final PsiElement[] allElementsToDelete) {
    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    for (PsiMethod method : methods) {
      boolean isDeleted = false;
      for (PsiElement element : allElementsToDelete) {
        if (element == method) {
          isDeleted = true;
          break;
        }
      }
      if (!isDeleted) {
        list.add(method);
      }
    }
    return list.toArray(new PsiMethod[list.size()]);
  }

  @Nullable
  private static Condition<PsiElement> findConstructorUsages(PsiMethod constructor, Collection<PsiReference> originalReferences, List<UsageInfo> usages,
                                                             final PsiElement[] allElementsToDelete) {
    HashMap<PsiMethod, Collection<PsiReference>> constructorsToRefs = new HashMap<PsiMethod, Collection<PsiReference>>();
    HashSet<PsiMethod> newConstructors = new HashSet<PsiMethod>();
    if (isTheOnlyEmptyDefaultConstructor(constructor)) return null;

    newConstructors.add(constructor);
    constructorsToRefs.put(constructor, originalReferences);
    HashSet<PsiMethod> passConstructors = new HashSet<PsiMethod>();
    do {
      passConstructors.clear();
      for (PsiMethod method : newConstructors) {
        final Collection<PsiReference> references = constructorsToRefs.get(method);
        for (PsiReference reference : references) {
          PsiMethod overridingConstructor = getOverridingConstructorOfSuperCall(reference.getElement());
          if (overridingConstructor != null && !constructorsToRefs.containsKey(overridingConstructor)) {
            Collection<PsiReference> overridingConstructorReferences = ReferencesSearch.search(overridingConstructor).findAll();
            constructorsToRefs.put(overridingConstructor, overridingConstructorReferences);
            passConstructors.add(overridingConstructor);
          }
        }
      }
      newConstructors.clear();
      newConstructors.addAll(passConstructors);
    }
    while(!newConstructors.isEmpty());

    final Set<PsiMethod> validOverriding =
            validateOverridingMethods(constructor, originalReferences, constructorsToRefs.keySet(), constructorsToRefs, usages,
                                      allElementsToDelete);

    return new Condition<PsiElement>() {
      public boolean value(PsiElement usage) {
        if(usage instanceof PsiFile) return false;
        return isInside(usage, allElementsToDelete) || isInside(usage, validOverriding);
      }
    };
  }

  private static boolean isTheOnlyEmptyDefaultConstructor(final PsiMethod constructor) {
    if (constructor.getParameterList().getParameters().length > 0) return false;
    final PsiCodeBlock body = constructor.getBody();
    if (body != null && body.getStatements().length > 0) return false;
    return constructor.getContainingClass().getConstructors().length == 1;
  }

  private static Set<PsiMethod> validateOverridingMethods(PsiMethod originalMethod, final Collection<PsiReference> originalReferences,
                                                   Collection<PsiMethod> overridingMethods, HashMap<PsiMethod, Collection<PsiReference>> methodToReferences,
                                                   List<UsageInfo> usages,
                                                   final PsiElement[] allElementsToDelete) {
    Set<PsiMethod> validOverriding = new LinkedHashSet<PsiMethod>(overridingMethods);
    Set<PsiMethod> multipleInterfaceImplementations = new HashSet<PsiMethod>();
    boolean anyNewBadRefs;
    do {
      anyNewBadRefs = false;
      for (PsiMethod overridingMethod : overridingMethods) {
        if (validOverriding.contains(overridingMethod)) {
          final Collection<PsiReference> overridingReferences = methodToReferences.get(overridingMethod);
          boolean anyOverridingRefs = false;
          for (final PsiReference overridingReference : overridingReferences) {
            final PsiElement element = overridingReference.getElement();
            if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
              anyOverridingRefs = true;
              break;
            }
          }
          if (!anyOverridingRefs && isMultipleInterfacesImplementation(overridingMethod, allElementsToDelete)) {
            anyOverridingRefs = true;
            multipleInterfaceImplementations.add(overridingMethod);
          }

          if (anyOverridingRefs) {
            validOverriding.remove(overridingMethod);
            anyNewBadRefs = true;

            for (PsiReference reference : originalReferences) {
              final PsiElement element = reference.getElement();
              if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods)) {
                usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(element, originalMethod, false));
                validOverriding.clear();
              }
            }
          }
        }
      }
    }
    while(anyNewBadRefs && !validOverriding.isEmpty());

    for (PsiMethod method : validOverriding) {
      if (method != originalMethod) {

        usages.add(new SafeDeleteOverridingMethodUsageInfo(method, originalMethod));
      }
    }

    for (PsiMethod method : overridingMethods) {
      if (!validOverriding.contains(method) && !multipleInterfaceImplementations.contains(method)) {
        final boolean methodCanBePrivate =
          canBePrivate(method, methodToReferences.get(method), validOverriding, allElementsToDelete);
        if (methodCanBePrivate) {
          usages.add(new SafeDeletePrivatizeMethod(method, originalMethod));
        }
      }
    }
    return validOverriding;
  }

  private static boolean isMultipleInterfacesImplementation(final PsiMethod method, final PsiElement[] allElementsToDelete) {
    final PsiMethod[] methods = method.findSuperMethods();
    for(PsiMethod superMethod: methods) {
      if (ArrayUtil.find(allElementsToDelete, superMethod) < 0) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiMethod getOverridingConstructorOfSuperCall(final PsiElement element) {
    PsiMethod overridingConstructor = null;
    if(element instanceof PsiReferenceExpression && "super".equals(element.getText())) {
      PsiElement parent = element.getParent();
      if(parent instanceof PsiMethodCallExpression) {
        parent = parent.getParent();
        if(parent instanceof PsiExpressionStatement) {
          parent = parent.getParent();
          if(parent instanceof PsiCodeBlock) {
            parent = parent.getParent();
            if(parent instanceof PsiMethod && ((PsiMethod) parent).isConstructor()) {
              overridingConstructor = (PsiMethod) parent;
            }
          }
        }
      }
    }
    return overridingConstructor;
  }

  private static boolean canBePrivate(PsiMethod method, Collection<PsiReference> references, Collection<? extends PsiElement> deleted,
                               final PsiElement[] allElementsToDelete) {
    final PsiClass containingClass = method.getContainingClass();
    if(containingClass == null) {
      return false;
    }

    PsiManager manager = method.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiElementFactory factory = facade.getElementFactory();
    final PsiModifierList privateModifierList;
    try {
      final PsiMethod newMethod = factory.createMethod("x3", PsiType.VOID);
      privateModifierList = newMethod.getModifierList();
      privateModifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    } catch (IncorrectOperationException e) {
      LOG.assertTrue(false);
      return false;
    }
    for (PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      if (!isInside(element, allElementsToDelete) && !isInside(element, deleted)
          && !facade.getResolveHelper().isAccessible(method, privateModifierList, element, null, null)) {
        return false;
      }
    }
    return true;
  }

  private static Condition<PsiElement> findFieldUsages(final PsiField psiField, final List<UsageInfo> usages, final PsiElement[] allElementsToDelete) {
    final Condition<PsiElement> isInsideDeleted = getUsageInsideDeletedFilter(allElementsToDelete);
    ReferencesSearch.search(psiField).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        if (!isInsideDeleted.value(reference.getElement())) {
          final PsiElement element = reference.getElement();
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiAssignmentExpression && element == ((PsiAssignmentExpression)parent).getLExpression()) {
            usages.add(new SafeDeleteFieldWriteReference((PsiAssignmentExpression)parent, psiField));
          }
          else {
            TextRange range = reference.getRangeInElement();
            usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(reference.getElement(), psiField, range.getStartOffset(),
                                                                    range.getEndOffset(), false, false));
          }
        }

        return true;
      }
    });

    return isInsideDeleted;
  }


  private static void findParameterUsages(final PsiParameter parameter, final List<UsageInfo> usages) {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    final int index = method.getParameterList().getParameterIndex(parameter);
    //search for refs to current method only, do not search for refs to overriding methods, they'll be searched separately
    ReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (element.getParent() instanceof PsiCall) {
          final PsiExpressionList argList = ((PsiCall)element.getParent()).getArgumentList();
          if (argList != null) {
            final PsiExpression[] args = argList.getExpressions();
            if (index < args.length) {
              if (!parameter.isVarArgs()) {
                usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(args[index], parameter, true));
              }
              else {
                for (int i = index; i < args.length; i++) {
                  usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(args[i], parameter, true));
                }
              }
            }
          }
        }
        return true;
      }
    });

    ReferencesSearch.search(parameter).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        PsiElement element = reference.getElement();
        final PsiDocTag docTag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
        if (docTag != null) {
          usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(docTag, parameter, true));
          return true;
        }

        boolean isSafeDelete = false;
        if (element.getParent().getParent() instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)element.getParent().getParent();
          PsiReferenceExpression methodExpression = call.getMethodExpression();
          if (methodExpression.getText().equals(PsiKeyword.SUPER) || methodExpression.getQualifierExpression() instanceof PsiSuperExpression) {
            final PsiMethod superMethod = call.resolveMethod();
            if (superMethod != null && MethodSignatureUtil.isSuperMethod(superMethod, method)) {
              isSafeDelete = true;
            }
          }
        }

        usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(element, parameter, isSafeDelete));
        return true;
      }
    });
  }

  private static boolean isInside(PsiElement place, PsiElement[] ancestors) {
    return isInside(place, Arrays.asList(ancestors));
  }

  private static boolean isInside(PsiElement place, Collection<? extends PsiElement> ancestors) {
    for (PsiElement element : ancestors) {
      if (isInside(place, element)) return true;
    }
    return false;
  }

  public static boolean isInside (PsiElement place, PsiElement ancestor) {
    if (SafeDeleteProcessor.isInside(place, ancestor)) return true;
    if (place instanceof PsiComment && ancestor instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)ancestor;
      if (aClass.getParent() instanceof PsiJavaFile) {
        final PsiJavaFile file = (PsiJavaFile)aClass.getParent();
        if (PsiTreeUtil.isAncestor(file, place, false)) {
          if (file.getClasses().length == 1) { // file will be deleted on class deletion
            return true;
          }
        }
      }
    }

    return false;
  }
}
