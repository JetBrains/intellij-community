package com.intellij.refactoring.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;

public class SearchUtils{
    private SearchUtils(){
    }

    public static Iterable<PsiReference> findAllReferences(PsiElement element, SearchScope scope){

        return new ArrayIterable<PsiReference>(ReferencesSearch.search(element, scope, true).toArray(new PsiReference[0]));
/*
        try {
            Class<?> searchClass = Class.forName("com.intellij.psi.search.searches.ReferencesSearch");

            final Method[] methods = searchClass.getMethods();
            for (Method method : methods) {
                if ("search".equals(method.getName()) &&) {
                    return (Iterable<PsiReference>) method.invoke(null, element, scope, true);
                }
            }
            return null;
        } catch (ClassNotFoundException ignore) {
            return null;
        } catch (IllegalAccessException ignore) {
            return null;
        } catch (InvocationTargetException ignore) {
            return null;
        }
        return ReferencesSearch.search(element, scope, true).findAll();
        */
    }

    public static Iterable<PsiReference> findAllReferences(PsiElement element){
        return findAllReferences(element, element.getUseScope());
    }

    public static Iterable<PsiMethod> findOverridingMethods(PsiMethod method){
        final PsiSearchHelper searchHelper = method.getManager().getSearchHelper();
        return new ArrayIterable<PsiMethod>(OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(new PsiMethod[0]));
       // return OverridingMethodsSearch.search(method, method.getUseScope(), true).findAll();
    }

    public static Iterable<PsiClass> findClassInheritors(PsiClass aClass, boolean deep){
        final PsiSearchHelper searchHelper = aClass.getManager().getSearchHelper();
        return new ArrayIterable<PsiClass>(ClassInheritorsSearch.search(aClass, aClass.getUseScope(), deep).toArray(new PsiClass[0]));
       // return ClassInheritorsSearch.search(aClass, deep);
    }

}

