package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;

import java.util.*;

public class ExpectedTypeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ExpectedTypeUtil");

  public static ExpectedTypeInfo[] intersect(List<ExpectedTypeInfo[]> typeInfos) {
    if (typeInfos.isEmpty()) return ExpectedTypeInfo.EMPTY;

    ExpectedTypeInfos result = new ExpectedTypeInfos(typeInfos.get(0));
    ExpectedTypeInfos acc = new ExpectedTypeInfos();

    for (int i = 1; i < typeInfos.size(); i++) {
      ExpectedTypeInfo[] next = typeInfos.get(i);
      acc.clear();
      for (int j = 0; j < next.length; j++) {
        ExpectedTypeInfo info = next[j];
        for (Iterator<ExpectedTypeInfo> iterator = result.iterator(); iterator.hasNext();) {
          ExpectedTypeInfo[] intersection = iterator.next().intersect(info);
          for (int k = 0; k < intersection.length; k++) {
            acc.addInfo(intersection[k]);
          }
        }
      }
      if (acc.isEmpty()) {
        return ExpectedTypeInfo.EMPTY;
      }
      result = acc;
    }

    return result.toArray();
  }

  private static class ExpectedTypeInfos {
    List<ExpectedTypeInfo> myInfos;

    public ExpectedTypeInfos() {
      myInfos = new ArrayList<ExpectedTypeInfo>();
    }

    public ExpectedTypeInfos(ExpectedTypeInfo[] infos) {
      myInfos = new ArrayList<ExpectedTypeInfo>(Arrays.asList(infos));
    }

    public void clear () { myInfos.clear(); }

    public void addInfo (ExpectedTypeInfo info) {
      for (Iterator<ExpectedTypeInfo> iterator = myInfos.iterator(); iterator.hasNext();) {
        ExpectedTypeInfo sub = iterator.next();
        int cmp = contains(sub, info);
        if (cmp > 0) return;
        else if (cmp < 0) {
          iterator.remove();
        }
      }
      myInfos.add(info);
    }

    public boolean isEmpty() {
      return myInfos.isEmpty();
    }

    public Iterator<ExpectedTypeInfo> iterator() {
      return myInfos.iterator();
    }

    public ExpectedTypeInfo[] toArray() {
      return myInfos.toArray(new ExpectedTypeInfo[myInfos.size()]);
    }
  }

  /**
   * @return <0 if info2 contains info1 (or they are equal)
   *         >0 if info1 contains info2
   *          0 otherwise
   */
  public static int contains(ExpectedTypeInfo info1, ExpectedTypeInfo info2) {
    int kind1 = info1.getKind();
    int kind2 = info2.getKind();
    if (kind1 == kind2) {
      if (matches(info1.getType(), info2)) return -1;
      if (matches(info2.getType(), info1)) return 1;
      return 0;
    } else if (kind1 == ExpectedTypeInfo.TYPE_STRICTLY) {
      return matches(info1.getType(), info2) ? -1 : 0;
    } else if (kind2 == ExpectedTypeInfo.TYPE_STRICTLY) {
      return matches(info2.getType(), info1) ? 1  : 0;
    }
    return 0;
  }

  public static boolean matches (PsiType type, ExpectedTypeInfo info) {
    PsiType infoType = info.getType();
    switch (info.getKind()) {
      case ExpectedTypeInfo.TYPE_STRICTLY:
        return type.equals(infoType);
      case ExpectedTypeInfo.TYPE_OR_SUBTYPE:
        return infoType.isAssignableFrom(type);
      case ExpectedTypeInfo.TYPE_OR_SUPERTYPE:
        return type.isAssignableFrom(infoType);
    }

    LOG.error("Unexpected ExpectedInfo kind");
    return false;
  }

  public static class ExpectedClassesFromSetProvider implements ExpectedTypesProvider.ExpectedClassProvider {
    private final Set<PsiClass> myOccurrenceClasses;

    public ExpectedClassesFromSetProvider(Set<PsiClass> occurrenceClasses) {
      myOccurrenceClasses = occurrenceClasses;
    }

    public PsiField[] findDeclaredFields(final PsiManager manager, String name) {
      List<PsiField> fields = new ArrayList<PsiField>();
      for (Iterator<PsiClass> iterator = myOccurrenceClasses.iterator(); iterator.hasNext();) {
        PsiClass aClass = iterator.next();
        final PsiField field = aClass.findFieldByName(name, true);
        if (field != null) fields.add(field);
      }
      return fields.toArray(new PsiField[fields.size()]);
    }

    public PsiMethod[] findDeclaredMethods(final PsiManager manager, String name) {
      List<PsiMethod> methods = new ArrayList<PsiMethod>();
      for (Iterator<PsiClass> iterator = myOccurrenceClasses.iterator(); iterator.hasNext();) {
        PsiClass aClass = iterator.next();
        final PsiMethod[] occMethod = aClass.findMethodsByName(name, true);
        methods.addAll(Arrays.asList(occMethod));
      }
      return methods.toArray(new PsiMethod[methods.size()]);
    }
  }
}
