package com.intellij.refactoring.typeCook;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiTypeIntersection;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 30.07.2003
 * Time: 18:57:30
 * To change this template use Options | File Templates.
 */
public class Util {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.Util");

  public static int getArrayLevel(PsiType t) {
    if (t instanceof PsiArrayType) {
      return 1 + getArrayLevel(((PsiArrayType)t).getComponentType());
    }

    return 0;
  }

  public static PsiType createArrayType(PsiType theType, int level) {
    while (level-- > 0) {
      theType = theType.createArrayType();
    }

    return theType;
  }

  public static PsiClassType.ClassResolveResult resolveType(PsiType type) {
    type = avoidAnonymous(type);

    if (type == null) {
      return PsiClassType.ClassResolveResult.EMPTY;
    }

    if (type instanceof PsiClassType) {
      return ((PsiClassType)type).resolveGenerics();
    }
    else if (type instanceof PsiArrayType) {
      return resolveType(((PsiArrayType)type).getComponentType());
    }
    else {
      return PsiClassType.ClassResolveResult.EMPTY;
    }
  }

  public static PsiType normalize(PsiType t, boolean objectBottom) {
    if (t instanceof PsiArrayType) {
      PsiType normType = normalize(((PsiArrayType)t).getComponentType(), objectBottom);

      return normType == null ? null : normType.createArrayType();
    }
    else if (t instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = resolveType(t);

      if (result == null) {
        return null;
      }

      PsiClass aclass = result.getElement();
      PsiSubstitutor subst = result.getSubstitutor();
      PsiManager manager = aclass.getManager();

      if (aclass.hasTypeParameters()) {
        PsiTypeParameter[] parms = getTypeParametersList(aclass);
        PsiSubstitutor newbst = PsiSubstitutor.EMPTY;

        boolean anyBottom = false;

        for (int i = 0; i < parms.length; i++) {
          PsiType p = subst.substitute(parms[i]);

          if (p != null) {
            PsiType pp = normalize(p, objectBottom);

            if (pp == null) {
              return null;
            }

            if (pp == Bottom.BOTTOM || (objectBottom && pp.getCanonicalText().equals("java.lang.Object"))) {
              anyBottom = true;
            }

            newbst = newbst.put(parms[i], pp);
          }
          else {
            anyBottom = true;
          }
        }

        if (anyBottom || newbst == PsiSubstitutor.EMPTY) {
          newbst = manager.getElementFactory().createRawSubstitutor(aclass);
        }

        return manager.getElementFactory().createType(aclass, newbst);
      }

      return manager.getElementFactory().createType(aclass);
    }
    else {
      return t;
    }
  }

  public static boolean isRaw(PsiType t, final Settings settings) {
    return isRaw(t, settings, true);
  }

  private static boolean isRaw(PsiType t, final Settings settings, final boolean upper) {
    if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = resolveType(t);

      if (resolveResult.getElement() == null) {
        return false;
      }

      if (PsiClassType.isRaw(resolveResult)) {
        return true;
      }

      final PsiSubstitutor subst = resolveResult.getSubstitutor();
      final PsiClass element = resolveResult.getElement();
      final PsiManager manager = element.getManager();

      if (settings.cookObjects() && upper &&
          t.equals(PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(manager.getProject())))) {
        return true;
      }

      final PsiTypeParameter[] parameters = getTypeParametersList(element);

      for (int i = 0; i < parameters.length; i++) {
        final PsiType actual = subst.substitute(parameters[i]);
        if (!(actual instanceof PsiTypeParameter) && isRaw(actual, settings, false)) return true;
      }

      return false;
    }
    else if (t instanceof PsiArrayType) {
      return settings.preserveRawArrays() ? false : isRaw(((PsiArrayType)t).getComponentType(), settings, upper);
    }

    return false;
  }

  public static PsiType banalize(final PsiType t) {
    if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = resolveType(t);
      final PsiClass theClass = result.getElement();

      if (theClass == null) {
        return t;
      }

      final PsiSubstitutor theSubst = result.getSubstitutor();
      final PsiManager theManager = theClass.getManager();

      PsiSubstitutor subst = PsiSubstitutor.EMPTY;

      for (final Iterator<PsiTypeParameter> p = theSubst.getSubstitutionMap().keySet().iterator(); p.hasNext();) {
        final PsiTypeParameter theParm = p.next();
        final PsiType actualType = theSubst.substitute(theParm);

        if (actualType == null || actualType instanceof PsiWildcardType) {
          subst = subst.put(theParm, Bottom.BOTTOM);
        }
        else {
          final PsiType banType = banalize(actualType);

          if (banType == null) {
            return t;
          }

          subst = subst.put(theParm, banType);
        }
      }

      return theManager.getElementFactory().createType(theClass, subst);
    }
    else if (t instanceof PsiArrayType) {
      return banalize(((PsiArrayType)t).getComponentType()).createArrayType();
    }

    return t;
  }

  public static PsiSubstitutor composeSubstitutors(PsiSubstitutor f, PsiSubstitutor g) {
    if (f == PsiSubstitutor.EMPTY) {
      return g;
    }

    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    Set<PsiTypeParameter> base = g.getSubstitutionMap().keySet();

    for (Iterator<PsiTypeParameter> i = base.iterator(); i.hasNext();) {
      PsiTypeParameter p = i.next();
      PsiType type = g.substitute(p);
      subst = subst.put(p, type == null ? null : f.substitute(type));
    }

    return subst;
  }

  public static boolean bindsTypeParameters(PsiType t, HashSet<PsiTypeParameter> params) {
    if (t instanceof PsiWildcardType) {
      final PsiWildcardType wct = ((PsiWildcardType)t);
      final PsiType bound = wct.getBound();

      if (bound != null && wct.isExtends()) {
        return bindsTypeParameters(bound, params);
      }

      return false;
    }

    final PsiClassType.ClassResolveResult result = Util.resolveType(t);
    final PsiClass theClass = result.getElement();
    final PsiSubstitutor theSubst = result.getSubstitutor();

    if (theClass == null) {
      return false;
    }

    if (theClass instanceof PsiTypeParameter) {
      return params == null || params.contains(theClass);
    }
    else if (theClass.hasTypeParameters()) {
      PsiTypeParameter[] parms = Util.getTypeParametersList(theClass);

      for (int i = 0; i < parms.length; i++) {
        PsiType bound = theSubst.substitute(parms[i]);

        if (bound != null && bindsTypeParameters(bound, params)) {
          return true;
        }
      }
    }

    return false;
  }

  public static PsiType getType(PsiElement element) {
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiExpression) {
      return ((PsiExpression)element).getType();
    }
    else if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getReturnType();
    }

    return null;
  }

  public static PsiTypeParameter[] getTypeParametersList(PsiClass a) {
    PsiTypeParameterList list = a.getTypeParameterList();

    if (list == null) {
      return PsiTypeParameter.EMPTY_ARRAY;
    }
    else {
      return list.getTypeParameters();
    }
  }

  public static PsiType avoidAnonymous(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      final PsiClass aClass = result.getElement();

      if (result.getElement() == null) {
        return null;
      }

      if (aClass instanceof PsiAnonymousClass) {
        return aClass.getSuperTypes()[0];
      }

      final PsiSubstitutor aSubst = result.getSubstitutor();

      PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

      for (Iterator<PsiTypeParameter> i = aSubst.getSubstitutionMap().keySet().iterator(); i.hasNext();) {
        final PsiTypeParameter p = i.next();
        PsiType t = aSubst.substitute(p);

        if (t != null) {
          t = avoidAnonymous(t);
        }

        theSubst = theSubst.put(p, t);
      }

      return aClass.getManager().getElementFactory().createType(aClass, theSubst);
    }
    else if (type instanceof PsiArrayType) {
      PsiType avoided = avoidAnonymous(((PsiArrayType)type).getComponentType());

      return avoided == null ? null : avoided.createArrayType();
    }

    return type;
  }

  public static PsiType createParameterizedType(final PsiType t, final PsiTypeVariableFactory factory, final PsiElement context) {
    return createParameterizedType(t, factory, true, context);
  }

  public static PsiType createParameterizedType(final PsiType t, final PsiTypeVariableFactory factory) {
    return createParameterizedType(t, factory, true, null);
  }

  private static PsiType createParameterizedType(final PsiType t,
                                                 final PsiTypeVariableFactory factory,
                                                 final boolean upper,
                                                 final PsiElement context) {
    if (t == null || (upper && t.getCanonicalText().equals("java.lang.Object"))) {
      return factory.create(context);
    }

    if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = resolveType(t);
      final PsiSubstitutor aSubst = result.getSubstitutor();
      final PsiClass aClass = result.getElement();

      PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

      final HashSet<PsiTypeVariable> cluster = new HashSet<PsiTypeVariable>();

      for (Iterator<PsiTypeParameter> i = aSubst.getSubstitutionMap().keySet().iterator(); i.hasNext();) {
        final PsiTypeParameter parm = i.next();
        final PsiType type = createParameterizedType(aSubst.substitute(parm), factory, false, context);

        if (type instanceof PsiTypeVariable) {
          cluster.add((PsiTypeVariable)type);
        }

        theSubst = theSubst.put(parm, type);
      }

      if (cluster.size() > 1) {
        factory.registerCluster(cluster);
      }

      return aClass.getManager().getElementFactory().createType(aClass, theSubst);
    }
    else if (t instanceof PsiArrayType) {
      return createParameterizedType(((PsiArrayType)t).getComponentType(), factory, upper, context).createArrayType();
    }

    return t;
  }

  public static PsiType substituteType(final PsiType type, final PsiSubstitutor subst) {
    if (type instanceof PsiWildcardType) {
      final PsiWildcardType wcType = ((PsiWildcardType)type);
      final PsiType bound = wcType.getBound();

      if (bound != null) {
        final PsiClass aClass = resolveType(bound).getElement();

        if (aClass != null) {
          final PsiManager manager = aClass.getManager();

          return wcType.isExtends()
                 ? PsiWildcardType.createExtends(manager, substituteType(bound, subst))
                 : PsiWildcardType.createSuper(manager, substituteType(bound, subst));
        }
      }

      return type;
    }
    else {
      final int level = getArrayLevel(type);
      final PsiClassType.ClassResolveResult result = resolveType(type);
      final PsiClass aClass = result.getElement();

      if (aClass != null) {
        final PsiSubstitutor aSubst = result.getSubstitutor();
        final PsiManager manager = aClass.getManager();

        if (aClass instanceof PsiTypeParameter) {
          final PsiType sType = subst.substitute(((PsiTypeParameter)aClass));

          return createArrayType(sType == null ? PsiType.getJavaLangObject(manager) : sType, level);
        }

        final PsiTypeParameter[] aParms = getTypeParametersList(aClass);
        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

        for (int i = 0; i < aParms.length; i++) {
          PsiTypeParameter aParm = aParms[i];

          theSubst = theSubst.put(aParm, substituteType(aSubst.substitute(aParm), subst));
        }

        return createArrayType(aClass.getManager().getElementFactory().createType(aClass, theSubst), level);
      }

      return createArrayType(type, level);
    }
  }

  public static boolean bindsTypeVariables(final PsiType t) {
    if (t == null) {
      return false;
    }

    if (t instanceof PsiTypeVariable) {
      return true;
    }

    if (t instanceof PsiArrayType) {
      return bindsTypeVariables(((PsiArrayType)t).getComponentType());
    }

    if (t instanceof PsiWildcardType) {
      return bindsTypeVariables(((PsiWildcardType)t).getBound());
    }

    if (t instanceof PsiTypeIntersection) {
      final PsiTypeIntersection itype = ((PsiTypeIntersection)t);

      return bindsTypeVariables(itype.getLeft()) || bindsTypeVariables(itype.getRight());
    }

    final PsiClassType.ClassResolveResult result = resolveType(t);

    if (result.getElement() != null) {
      final PsiSubstitutor subst = result.getSubstitutor();

      for (Iterator<PsiType> types = subst.getSubstitutionMap().values().iterator(); types.hasNext();) {
        if (bindsTypeVariables(types.next())) {
          return true;
        }
      }
    }

    return false;
  }

  public static void changeType(final PsiElement element, final PsiType type) {
    try {
      if (element instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression cast = ((PsiTypeCastExpression)element);

        cast.getCastType().replace(cast.getManager().getElementFactory().createTypeElement(type));
      }
      else if (element instanceof PsiVariable) {
        final PsiVariable field = ((PsiVariable)element);

        field.normalizeDeclaration();
        field.getTypeElement().replace(field.getManager().getElementFactory().createTypeElement(type));
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod method = ((PsiMethod)element);

        method.getReturnTypeElement().replace(method.getManager().getElementFactory().createTypeElement(type));
      }
      else if (element instanceof PsiNewExpression) {
        final PsiNewExpression newx = (PsiNewExpression)element;
        final PsiClassType.ClassResolveResult result = Util.resolveType(type);

        if (result == null) {
          return;
        }

        final PsiSubstitutor subst = result.getSubstitutor();
        final PsiTypeParameter[] parms = Util.getTypeParametersList(result.getElement());

        if (parms.length > 0 && subst.substitute(parms[0]) != null) {
          PsiJavaCodeReferenceElement classReference = newx.getClassReference();
          PsiReferenceParameterList list = null;

          if (classReference == null) {
            list = newx.getAnonymousClass().getBaseClassReference().getParameterList();
          }
          else {
            list = classReference.getParameterList();
          }

          if (list == null) {
            return;
          }

          final PsiElementFactory factory = newx.getManager().getElementFactory();

          PsiTypeElement[] elements = list.getTypeParameterElements();
          for (int i = 0; i < elements.length; i++) {
            elements[i].delete();
          }

          for (int i = 0; i < parms.length; i++) {
            PsiType aType = subst.substitute(parms[i]);

            if (aType instanceof PsiWildcardType) {
              aType = ((PsiWildcardType)aType).getBound();
            }

            list.add(factory.createTypeElement(aType == null ? PsiType.getJavaLangObject(list.getManager()) : aType));
          }
        }
      }
      else {
        LOG.error("Unexpected element type " + element.getClass().getName());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error("Incorrect Operation Exception thrown in CastRole.\n");
    }
  }
}

