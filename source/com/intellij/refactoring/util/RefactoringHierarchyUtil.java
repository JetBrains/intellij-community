/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 14:28:03
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.containers.HashSet;

import java.util.*;

public class RefactoringHierarchyUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.RefactoringHierarchyUtil");
  private static final List<PsiType> PRIMITIVE_TYPES = Arrays.asList(
      new PsiType[]{PsiType.BYTE, PsiType.CHAR, PsiType.SHORT, PsiType.INT, PsiType.LONG, PsiType.FLOAT, PsiType.DOUBLE, }
  );

  public static boolean willBeInTargetClass(PsiElement place,
                                            Set<PsiMember> membersToMove,
                                            PsiClass targetClass,
                                            boolean includeSubclasses) {
    PsiElement parent = place;
    while (parent != null) {
      if (membersToMove.contains(parent)) return true;
      if (parent instanceof PsiClass) {
        if (targetClass.equals(parent)) return true;
        if (includeSubclasses && ((PsiClass) parent).isInheritor(targetClass, true)) return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public static PsiClass getDeepestNonObjectBase(PsiClass aClass) {
    PsiClass current = aClass;
    while (current != null) {
      PsiClassType[] supers = current.getExtendsList().getReferencedTypes();
      if (supers.length == 0) {
        return current;
      }
      PsiClass base = supers[0].resolve();
      if (base != null) {
        current = base;
      }
      else {
        return current;
      }
    }
    return null;
  }

  public static PsiClass getNearestBaseClass(PsiClass subClass, boolean includeNonProject) {
    PsiClassType[] superTypes = subClass.getSuperTypes();

    if (superTypes != null && superTypes.length > 0) {
      PsiClass resolved = superTypes[0].resolve();
      if (resolved != null) {
        if (!includeNonProject) {
          if (resolved.getManager().isInProject(resolved)) {
            return resolved;
          }
        }
        else {
          return resolved;
        }
      }
    }
    return null;
  }

  /**
   *
   * @param subClass
   * @param includeNonProject
   * @param sortAlphabetically if false, sorted in DFS order
   * @return
   */
  public static ArrayList createBasesList(PsiClass subClass, boolean includeNonProject, boolean sortAlphabetically) {
    LinkedHashSet<PsiClass> bases = new LinkedHashSet<PsiClass>();
    getSuperClasses(subClass, bases, includeNonProject);

    if (!subClass.isInterface()) {
      final PsiManager manager = subClass.getManager();
      PsiClass javaLangObject = manager.findClass("java.lang.Object", subClass.getResolveScope());
      if (includeNonProject && javaLangObject != null && !manager.areElementsEquivalent(javaLangObject, subClass)) {
        bases.add(javaLangObject);
      }
    }

    ArrayList<PsiClass> basesList = new ArrayList<PsiClass>(bases);

    if (sortAlphabetically) {
      Collections.sort(
          basesList, new Comparator<PsiClass>() {
            public int compare(PsiClass c1, PsiClass c2) {
              return c1.getQualifiedName().compareTo(c2.getQualifiedName());
            }
          }
      );
    }

    return basesList;
  }

  /**
   * Checks whether given element is below the given superClass in class hierarchy.
   * @param superClass
   * @return
   * @param subClass
   * @param member
   */
  public static boolean isMemberBetween(PsiClass superClass, PsiClass subClass, PsiMember member) {
    PsiClass elementClass = null;
    if (member instanceof PsiField || member instanceof PsiMethod) {
      elementClass = member.getContainingClass();
    }

    if (elementClass == null) return false;
    if (superClass != null) {
      return !superClass.getManager().areElementsEquivalent(superClass, elementClass) &&
             elementClass.isInheritor(superClass, true);
    }
    else {
      return subClass.getManager().areElementsEquivalent(subClass, elementClass);
    }
  }

  /**
   * Gets all superclasses. Classes are added to result in DFS order
   * @param aClass
   * @param results
   * @param includeNonProject
   */
  public static void getSuperClasses(PsiClass aClass, Set<PsiClass> results, boolean includeNonProject) {
    getSuperClassesOfList(aClass.getSuperTypes(), results, includeNonProject);
  }

  private static void getSuperClassesOfList(PsiClassType[] types, Set<PsiClass> results,
                                                     boolean includeNonProject) {
    for (int i = 0; i < types.length; i++) {
      PsiClass resolved = types[i].resolve();
      if (resolved != null) {
        if (!results.contains(resolved)) {
          if (includeNonProject || resolved.getManager().isInProject(resolved)) {
            results.add(resolved);
          }
          getSuperClasses(resolved, results, includeNonProject);
        }
      }
    }
  }

  public static void processSuperTypes(PsiType type, SuperTypeVisitor visitor) {
    processSuperTypes(type, visitor, new HashSet<PsiType>());
  }
  public static void processSuperTypes(PsiType type, SuperTypeVisitor visitor, Set<PsiType> visited) {
    if (visited.contains(type)) return;
    visited.add(type);
    if (type instanceof PsiPrimitiveType) {
      int index = PRIMITIVE_TYPES.indexOf(type);
      if (index >= 0) {
        for (int i = index + 1; i < PRIMITIVE_TYPES.size(); i++) {
          visitor.visitType(PRIMITIVE_TYPES.get(i));
        }
      }
    }
    else {
      final PsiType[] superTypes = type.getSuperTypes();
      for (int i = 0; i < superTypes.length; i++) {
        PsiType superType = superTypes[i];
        visitor.visitType(superType);
        processSuperTypes(superType, visitor, visited);
      }
    }
  }

  public static PsiClass[] findImplementingClasses(PsiClass anInterface) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    _findImplementingClasses(anInterface, new HashSet<PsiClass>(), result);
    loop1:
    for (Iterator<PsiClass> iterator = result.iterator(); iterator.hasNext();) {
      final PsiClass psiClass = iterator.next();
      for (Iterator<PsiClass> iterator1 = result.iterator(); iterator1.hasNext();) {
        final PsiClass aClass = iterator1.next();
        if (psiClass.isInheritor(aClass, true)) {
          iterator.remove();
          break loop1;
        }
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  private static void _findImplementingClasses(PsiClass anInterface, final Set<PsiClass> visited, final List<PsiClass> result) {
    LOG.assertTrue(anInterface.isInterface());
    PsiManager manager = anInterface.getManager();
    visited.add(anInterface);
    PsiSearchHelper searchHelper = manager.getSearchHelper();
    searchHelper.processInheritors(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
          final PsiClass psiClass = element;
          if (!psiClass.isInterface()) {
            result.add(psiClass);
          }
          else if (!visited.contains(psiClass)){
            _findImplementingClasses(psiClass, visited, result);
          }
        return true;
      }

      public Object getHint(Class hintClass) {
        return null;
      }
    },
                                   anInterface,
                                   GlobalSearchScope.projectScope(manager.getProject()),
                                   false);
  }


  public static interface SuperTypeVisitor {
    void visitType(PsiType aType);

    void visitClass(PsiClass aClass);
  }
}
