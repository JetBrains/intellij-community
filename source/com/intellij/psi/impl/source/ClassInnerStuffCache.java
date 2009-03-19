package com.intellij.psi.impl.source;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ClassInnerStuffCache {
  private volatile Map<String, PsiField> myCachedFieldsMap = null;
  private volatile Map<String, PsiMethod[]> myCachedMethodsMap = null;
  private volatile Map<String, PsiClass> myCachedInnersMap = null;

  private volatile PsiMethod[] myCachedConstructors = null;
  private final PsiClass myClass;

  public ClassInnerStuffCache(final PsiClass aClass) {
    myClass = aClass;
  }

  public void dropCaches() {
    myCachedConstructors = null;

    myCachedFieldsMap = null;
    myCachedMethodsMap = null;
    myCachedInnersMap = null;
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    PsiMethod[] constructors = myCachedConstructors;
    if (constructors == null) {
      myCachedConstructors = constructors = PsiImplUtil.getConstructors(myClass);
    }
    return constructors;
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    if (!checkBases) {
      Map<String, PsiField> cachedFields = myCachedFieldsMap;
      if (cachedFields == null) {
        final PsiField[] fields = myClass.getFields();
        if (fields.length > 0) {
          cachedFields = new THashMap<String, PsiField>();
          for (final PsiField field : fields) {
            cachedFields.put(field.getName(), field);
          }
          myCachedFieldsMap = cachedFields;
        }
        else {
          myCachedFieldsMap = Collections.emptyMap();
          return null;
        }
      }
      return cachedFields.get(name);
    }
    return PsiClassImplUtil.findFieldByName(myClass, name, checkBases);
  }


  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    if(!checkBases){
      Map<String, PsiMethod[]> cachedMethods = myCachedMethodsMap;
      if(cachedMethods == null){
        cachedMethods = new THashMap<String,PsiMethod[]>();

        Map<String, List<PsiMethod>> cachedMethodsMap = new THashMap<String,List<PsiMethod>>();
        final PsiMethod[] methods = myClass.getMethods();
        for (final PsiMethod method : methods) {
          List<PsiMethod> list = cachedMethodsMap.get(method.getName());
          if (list == null) {
            list = new ArrayList<PsiMethod>(1);
            cachedMethodsMap.put(method.getName(), list);
          }
          list.add(method);
        }
        for (Map.Entry<String, List<PsiMethod>> entry : cachedMethodsMap.entrySet()) {
          List<PsiMethod> cached = entry.getValue();
          String methodName = entry.getKey();
          cachedMethods.put(methodName, cached.toArray(new PsiMethod[cached.size()]));
        }
        myCachedMethodsMap = cachedMethods;
      }

      final PsiMethod[] psiMethods = cachedMethods.get(name);
      return psiMethods != null ? psiMethods : PsiMethod.EMPTY_ARRAY;
    }

    return PsiClassImplUtil.findMethodsByName(myClass, name, checkBases);
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    if (!checkBases) {
      Map<String, PsiClass> inners = myCachedInnersMap;
      if (inners == null) {
        final PsiClass[] classes = myClass.getInnerClasses();
        if (classes.length > 0) {
          inners = new THashMap<String, PsiClass>();
          for (final PsiClass psiClass : classes) {
            inners.put(psiClass.getName(), psiClass);
          }
          myCachedInnersMap = inners;
        }
        else {
          myCachedInnersMap = Collections.emptyMap();
          return null;
        }
      }
      return inners.get(name);
    }
    return PsiClassImplUtil.findInnerByName(myClass, name, checkBases);
  }
}