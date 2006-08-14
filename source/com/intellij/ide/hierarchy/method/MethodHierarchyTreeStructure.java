package com.intellij.ide.hierarchy.method;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public final class MethodHierarchyTreeStructure extends HierarchyTreeStructure {
  public static final String TYPE = IdeBundle.message("title.hierarchy.method");
  private final SmartPsiElementPointer myMethod;

  /**
   * Should be called in read action
   */
  public MethodHierarchyTreeStructure(final Project project, final PsiMethod method) {
    super(project, null);
    myBaseDescriptor = buildHierarchyElement(project, method);
    ((MethodHierarchyNodeDescriptor)myBaseDescriptor).setTreeStructure(this);
    myMethod = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(method);
    setBaseElement(myBaseDescriptor); //to set myRoot
  }

  private HierarchyNodeDescriptor buildHierarchyElement(final Project project, final PsiMethod method) {
    final PsiClass suitableBaseClass = findSuitableBaseClass(method);

    HierarchyNodeDescriptor descriptor = null;
    final ArrayList<PsiClass> superClasses = createSuperClasses(suitableBaseClass);

    if (!suitableBaseClass.equals(method.getContainingClass())) {
      superClasses.add(0, suitableBaseClass);
    }

    // remove from the top of the branch the classes that contain no 'method'
    for(int i = superClasses.size() - 1; i >= 0; i--){
      final PsiClass psiClass = superClasses.get(i);

      if (MethodHierarchyUtil.findBaseMethodInClass(method, psiClass, false) == null) {
        superClasses.remove(i);
      }
      else {
        break;
      }
    }

    for(int i = superClasses.size() - 1; i >= 0; i--){
      final PsiClass superClass = superClasses.get(i);
      final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, superClass, false, MethodHierarchyTreeStructure.this);
      if (descriptor != null){
        descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, method.getContainingClass(), true, MethodHierarchyTreeStructure.this);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
    }
    return newDescriptor;
  }

  private static ArrayList<PsiClass> createSuperClasses(PsiClass aClass) {
    if (!aClass.isValid()) {
      return new ArrayList<PsiClass>();
    }

    final ArrayList<PsiClass> superClasses = new ArrayList<PsiClass>();
    while (!isJavaLangObject(aClass)) {
      final PsiClass aClass1 = aClass;
      final PsiClass[] superTypes = aClass1.getSupers();
      PsiClass superType = null;
      // find class first
      for (final PsiClass type : superTypes) {
        if (!type.isInterface() && !isJavaLangObject(type)) {
          superType = type;
          break;
        }
      }
      // if we haven't found a class, try to find an interface
      if (superType == null) {
        for (final PsiClass type : superTypes) {
          if (!isJavaLangObject(type)) {
            superType = type;
            break;
          }
        }
      }
      if (superType == null) break;
      if (superClasses.contains(superType)) break;
      superClasses.add(superType);
      aClass = superType;
    }

    return superClasses;
  }

  private static boolean isJavaLangObject(final PsiClass aClass) {
    return "java.lang.Object".equals(aClass.getQualifiedName());
  }

  private static PsiClass findSuitableBaseClass(final PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();

    if (containingClass instanceof PsiAnonymousClass) {
      return containingClass;
    }

    final PsiClass superClass = containingClass.getSuperClass();
    if (superClass == null) {
      return containingClass;
    }

    if (MethodHierarchyUtil.findBaseMethodInClass(method, superClass, true) == null) {
      for (final PsiClass anInterface : containingClass.getInterfaces()) {
        if (MethodHierarchyUtil.findBaseMethodInClass(method, anInterface, true) != null) {
          return anInterface;
        }
      }
    }

    return containingClass;
  }

  @Nullable
  public final PsiMethod getBaseMethod() {
    final PsiElement element = myMethod.getElement();
    return element instanceof PsiMethod ? (PsiMethod)element : null;
  }


  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiClass psiClass = ((MethodHierarchyNodeDescriptor)descriptor).getPsiClass();

    final PsiClass[] subclasses = getSubclasses(psiClass);

    final ArrayList<HierarchyNodeDescriptor> descriptors = new ArrayList<HierarchyNodeDescriptor>(subclasses.length);
    for (final PsiClass aClass : subclasses) {
      if (HierarchyBrowserManager.getInstance(myProject).HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED) {
        if (shouldHideClass(aClass)) {
          continue;
        }
      }

      final MethodHierarchyNodeDescriptor d = new MethodHierarchyNodeDescriptor(myProject, descriptor, aClass, false, this);
      descriptors.add(d);
    }
    return descriptors.toArray(new HierarchyNodeDescriptor[descriptors.size()]);
  }

  private PsiClass[] getSubclasses(final PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass || psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      return PsiClass.EMPTY_ARRAY;
    }

    return PsiManager.getInstance(myProject).getSearchHelper().findInheritors(psiClass, psiClass.getUseScope(), false);
  }

  private boolean shouldHideClass(final PsiClass psiClass) {
    if (getMethod(psiClass, false) != null || isSuperClassForBaseClass(psiClass)) {
      return false;
    }

    if (hasBaseClassMethod(psiClass) || isAbstract(psiClass)) {
      for (final PsiClass subclass : getSubclasses(psiClass)) {
        if (!shouldHideClass(subclass)) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  private boolean isAbstract(final PsiModifierListOwner owner) {
    return owner.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  private boolean hasBaseClassMethod(final PsiClass psiClass) {
    final PsiMethod baseClassMethod = getMethod(psiClass, true);
    return baseClassMethod != null && !isAbstract(baseClassMethod);
  }

  private PsiMethod getMethod(final PsiClass aClass, final boolean checkBases) {
    return MethodHierarchyUtil.findBaseMethodInClass(getBaseMethod(), aClass, checkBases);
  }

  boolean isSuperClassForBaseClass(final PsiClass aClass) {
    final PsiMethod baseMethod = getBaseMethod();
    if (baseMethod == null) {
      return false;
    }
    final PsiClass baseClass = baseMethod.getContainingClass();
    if (baseClass == null) {
      return false;
    }
    // NB: parameters here are at CORRECT places!!!
    return baseClass.isInheritor(aClass, true);
  }
}
