package com.intellij.refactoring.typeCook;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
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

  private static final int HEIGHT_BOUND = 6;

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
    else if (type instanceof SubtypeOf) {
      return resolveType(((SubtypeOf)type).getSuperType());
    }
    else if (type instanceof SupertypeOf) {
      return resolveType(((SupertypeOf)type).getSubType());
    }
    else {
      return PsiClassType.ClassResolveResult.EMPTY;
    }
  }

  public static PsiElement getParentElement(PsiElement element) {
    PsiElement parent = element;

    while ((parent = parent.getParent()) instanceof PsiParenthesizedExpression) ;

    return parent;
  }

  public static boolean isSonOfReferenceExpression(PsiElement he) {
    PsiElement wormlet = he;

    while ((wormlet = wormlet.getParent()) instanceof PsiParenthesizedExpression) ;

    return wormlet instanceof PsiReferenceExpression;
  }

  public static PsiType normalize(PsiType t, boolean objectBottom) {
    if (t instanceof SubtypeOf) {
      return normalize(((SubtypeOf)t).getSuperType(), objectBottom);
    }
    else if (t instanceof SupertypeOf) {
      return normalize(((SupertypeOf)t).getSubType(), objectBottom);
    }
    else if (t instanceof PsiArrayType) {
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

  public static boolean hasNoParameters(PsiType t) {
    if (t instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = resolveType(t);

      if (resolveResult == null) {
        return true;
      }

      if (PsiClassType.isRaw(resolveResult)) {
        return true;
      }

      PsiSubstitutor subst = resolveResult.getSubstitutor();
      PsiClass element = resolveResult.getElement();

      if (element instanceof PsiTypeParameter) {
        return false;
      }

      PsiTypeParameter[] parameters = getTypeParametersList(element);

      for (int i = 0; i < parameters.length; i++) {
        PsiType actual = subst.substitute(parameters[i]);
        if (hasNoParameters(actual)) {
          return true;
        }
      }
    }
    else if (t instanceof PsiArrayType) {
      return hasNoParameters(((PsiArrayType)t).getComponentType());
    }

    return false;
  }

  public static boolean isRaw(PsiType t) {
    return isRaw(t, true);
  }

  public static boolean isRaw(PsiType t, boolean arrays) {
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

      final PsiTypeParameter[] parameters = getTypeParametersList(element);

      for (int i = 0; i < parameters.length; i++) {
        final PsiType actual = subst.substitute(parameters[i]);
        if (!(actual instanceof PsiTypeParameter) && isRaw(actual, arrays)) return true;
      }

      return false;
    }
    else if (t instanceof PsiArrayType) {
      return arrays ? false : isRaw(((PsiArrayType)t).getComponentType(), arrays);
    }

    return false;
  }

  public static boolean isTypeParameter(PsiType t) {

    PsiClassType.ClassResolveResult result = resolveType(t);

    if (result == null) {
      return false;
    }

    return result.getElement() instanceof PsiTypeParameter;
  }

  public static PsiType banalize(PsiType t) {
    if (t instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = resolveType(t);

      if (result == null) {
        return null;
      }

      PsiClass theClass = result.getElement();
      PsiSubstitutor theSubst = result.getSubstitutor();
      PsiManager theManager = theClass.getManager();

      PsiTypeParameter[] theParms = Util.getTypeParametersList(theClass);
      PsiSubstitutor subst = PsiSubstitutor.EMPTY;

      for (int i = 0; i < theParms.length; i++) {
        PsiTypeParameter theParm = theParms[i];

        PsiType actualType = theSubst.substitute(theParm);

        if (actualType == null || actualType instanceof PsiWildcardType) {// || isTypeParameter(actualType)) {
          subst = subst.put(theParm, Bottom.BOTTOM);
        }
        else {
          PsiType banType = banalize(actualType);

          if (banType == null) {
            return null;
          }

          subst = subst.put(theParm, banType);
        }
      }

      return theManager.getElementFactory().createType(theClass, subst);
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

  public static boolean bindsTypeParameters(PsiSubstitutor theSubst, HashSet<PsiTypeParameter> params) {
    Collection<PsiType> values = theSubst.getSubstitutionMap().values();

    for (Iterator<PsiType> i = values.iterator(); i.hasNext();) {
      PsiType type = i.next();
      PsiClassType.ClassResolveResult result = Util.resolveType(type);

      if (result == null) {
        return false;
      }

      PsiClass aClass = result.getElement();

      if (aClass instanceof PsiTypeParameter) {
        return params.contains(aClass);
      }
      else {
        if (bindsTypeParameters(result.getSubstitutor(), params)) {
          return true;
        }
      }
    }

    return false;
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

    PsiClassType.ClassResolveResult result = Util.resolveType(t);

    if (result == null) {
      return false;
    }

    PsiClass theClass = result.getElement();
    PsiSubstitutor theSubst = result.getSubstitutor();

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

  public static boolean bindsTypeParameters(PsiType t) {
    return bindsTypeParameters(t, null);
  }

  public static PsiSubstitutor createIdentitySubstitutor(PsiTypeParameterList p) {
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    PsiTypeParameter[] parms = p.getTypeParameters();

    for (int i = 0; i < parms.length; i++) {
      PsiTypeParameter pp = parms[i];
      subst = subst.put(pp, pp.getManager().getElementFactory().createType(pp));
    }

    return subst;
  }

  public static PsiSubstitutor createIdentitySubstitutor(Set<PsiTypeParameter> params) {
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;

    for (Iterator<PsiTypeParameter> i = params.iterator(); i.hasNext();) {
      PsiTypeParameter p = i.next();
      subst = subst.put(p, p.getManager().getElementFactory().createType(p));
    }

    return subst;
  }

  public static PsiType getNCA(PsiClass aClass, PsiClass bClass) {
    if (InheritanceUtil.isCorrectDescendant(aClass, bClass, true)) {
      return aClass.getManager().getElementFactory()
        .createType(bClass);
    }
    if (InheritanceUtil.isCorrectDescendant(bClass, aClass, true)) {
      return aClass.getManager().getElementFactory()
        .createType(aClass);
    }
    ;

    return PsiType.getJavaLangObject(aClass.getManager());
  }

  public static PsiType cloneType(PsiType t, PsiManager manager) {
    return manager.getElementFactory().detachType(t);
  }

  public static TypeNode killOthers(TypeNode baseNode,
                                    TypeNode objectNode,
                                    PsiClass mainClass,
                                    HashSet<PsiClass> boundParameters) {
    if (!mainClass.hasTypeParameters()) {
      return baseNode;
    }

    PsiTypeParameter[] mainParms = getTypeParametersList(mainClass);

    for (int i = 0; i < mainParms.length; i++) {
      PsiTypeParameter p = mainParms[i];

      if (!boundParameters.contains(p)) {
        TypeEdge.connectParameter(baseNode, objectNode, p.getIndex());
      }
    }

    return baseNode;
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

  public static boolean equals(PsiClass a, PsiClass b) {
    if (a.getManager().areElementsEquivalent(a, b)) {
      return getTypeParametersList(a).length == getTypeParametersList(b).length;
    }

    return false;
  }

  private static boolean clashes(PsiClass a, PsiClass b) {
    return
      a.getManager().areElementsEquivalent(a, b) &&
      (getTypeParametersList(a).length != getTypeParametersList(b).length);
  }

  public static boolean isDescendant(PsiClass a, PsiClass b) {
    boolean semi = InheritanceUtil.isCorrectDescendant(a, b, true);

    if (semi && clashes(a, b)) {
      return false;
    }

    return semi;
  }

  public static int getTypeKind(PsiType t) {
    if (t instanceof PsiClassType) return 0;
    if (t instanceof PsiArrayType) return 1;
    if (t instanceof SupertypeOf) return 2;
    if (t instanceof SubtypeOf) return 3;
    if (t instanceof Bottom) return 4;

    LOG.error("Class/Array/Super/Sub-type expected in getTypeKind.");

    return 5; // PsiPrimitiveType
  }

  public static PsiType balanceSubtype(PsiClass aClass, PsiClass bClass, PsiSubstitutor aSubst) {
    PsiSubstitutor subSubst = TypeConversionUtil.getClassSubstitutor(bClass, aClass, PsiSubstitutor.EMPTY);
    PsiSubstitutor theSubst = Util.composeSubstitutors(aSubst, subSubst);

    return aClass.getManager().getElementFactory().createType(bClass, theSubst);
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

  public static boolean isGeneric(PsiType a) {
    if (a instanceof PsiArrayType) {
      return isGeneric(((PsiArrayType)a).getDeepComponentType());
    }

    if (a instanceof SubtypeOf) {
      return isGeneric(((SubtypeOf)a).getSuperType());
    }

    if (a instanceof SupertypeOf) {
      return isGeneric(((SupertypeOf)a).getSubType());
    }

    if (a instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = resolveType(a);

      if (result == null) {
        return false;
      }

      PsiClass aClass = result.getElement();

      return aClass != null && aClass.hasTypeParameters();
    }

    return a == Bottom.BOTTOM;
  }

  public static PsiType undress(PsiType t) {
    if (t instanceof SupertypeOf) {
      return undress(((SupertypeOf)t).getSubType());
    }

    if (t instanceof SubtypeOf) {
      return undress(((SubtypeOf)t).getSuperType());
    }

    return t;
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

  private static boolean isValidType(PsiType type, PsiElement context) {
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = resolveType(type);

      if (result == null) {
        return false;
      }

      PsiClass aClass = result.getElement();
      PsiSubstitutor aSubst = result.getSubstitutor();

      if (!PsiUtil.isAccessible(aClass, context, null)) {
        return false;
      }

      PsiTypeParameter[] aParms = getTypeParametersList(aClass);

      for (int i = 0; i < aParms.length; i++) {
        if (!isValidType(aSubst.substitute(aParms[i]), context)) {
          return false;
        }
      }

      return true;
    }

    if (type instanceof PsiArrayType) {
      return isValidType(((PsiArrayType)type).getComponentType(), context);
    }

    return true;
  }

  public static boolean isValidTypeInContext(PsiType type, PsiElement context) {
    try {
      return isValidType(context.getManager().getElementFactory().createTypeFromText(type.getCanonicalText(), context),
                         context);
    }
    catch (IncorrectOperationException e) {
      LOG.error("Incorrect operation during factory.createTypeFromText");
    }

    return false;
  }

  private static int getHeight(PsiType t) {
    if (t instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)t).resolveGenerics();
      PsiClass aClass = result.getElement();
      PsiSubstitutor aSubst = result.getSubstitutor();

      if (aClass == null) {
        return 0;
      }

      PsiTypeParameter[] parms = getTypeParametersList(aClass);

      int max = 0;

      for (int i = 0; i < parms.length; i++) {
        max = Math.max(max, getHeight(aSubst.substitute(parms[i])));
        if (max > HEIGHT_BOUND) {
          break;
        }
      }

      return 1 + max;
    }
    else if (t instanceof PsiArrayType) {
      return getHeight(((PsiArrayType)t).getDeepComponentType());
    }
    else if (t instanceof SubtypeOf) {
      return getHeight(((SubtypeOf)t).getSuperType());
    }
    else if (t instanceof SupertypeOf) {
      return getHeight(((SupertypeOf)t).getSubType());
    }

    return 0;
  }

  public static boolean prunedType(PsiType t) {
    return getHeight(t) > HEIGHT_BOUND;
  }

  public static PsiType createParameterizedType(final PsiType t, final PsiTypeVariableFactory factory) {
    if (t == null) {
      return factory.create();
    }

    if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = resolveType(t);
      final PsiSubstitutor aSubst = result.getSubstitutor();
      final PsiClass aClass = result.getElement();

      PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

      for (Iterator<PsiTypeParameter> i = aSubst.getSubstitutionMap().keySet().iterator(); i.hasNext();) {
        final PsiTypeParameter parm = i.next();
        theSubst = theSubst.put(parm, createParameterizedType(aSubst.substitute(parm), factory));
      }

      return aClass.getManager().getElementFactory().createType(aClass, theSubst);
    }
    else if (t instanceof PsiArrayType) {
      return createParameterizedType(((PsiArrayType)t).getComponentType(), factory).createArrayType();
    }

    return t;
  }

  public static PsiType substituteType(final PsiType type, final PsiSubstitutor subst) {
    final int level = getArrayLevel(type);          
    final PsiClassType.ClassResolveResult result = resolveType(type);

    if (result.getElement() != null) {
      final PsiClass aClass = result.getElement();
      final PsiSubstitutor aSubst = result.getSubstitutor();
      final PsiManager manager = aClass.getManager();

      if (aClass instanceof PsiTypeParameter) {
        final PsiType sType = subst.substitute(((PsiTypeParameter)aClass));

        return createArrayType(sType == null ? PsiType.getJavaLangObject(manager): sType, level);
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

  public static boolean bindsTypeVariables(final PsiType t) {
    if (t == null){
      return false;
    }

    if (t instanceof PsiTypeVariable) {
      return true;
    }

    if (t instanceof PsiTypeIntersection){
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
}

