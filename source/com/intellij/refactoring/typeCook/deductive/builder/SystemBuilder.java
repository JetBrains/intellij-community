package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.dupLocator.util.PsiAnchor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.deductive.PsiTypeIntersection;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.util.VictimCollector;
import com.intellij.util.containers.HashMap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 27.06.2003
 * Time: 22:48:08
 * To change this template use Options | File Templates.
 */
public class SystemBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder");

  private PsiManager myManager;
  private HashMap<PsiElement, Boolean> myMethodCache;
  private HashMap<PsiParameter, PsiParameter> myParameters;
  private HashMap<PsiMethod, PsiMethod> mySuper;
  private HashMap<PsiElement, PsiType> myTypes;
  private HashSet<PsiAnchor> myVisitedConstructions;
  private Settings mySettings;
  private PsiTypeVariableFactory myTypeVariableFactory;

  public SystemBuilder(PsiManager manager, final Settings settings) {
    myManager = manager;
    mySettings = settings;
    myMethodCache = new HashMap<PsiElement, Boolean>();
    myParameters = new HashMap<PsiParameter, PsiParameter>();
    mySuper = new HashMap<PsiMethod, PsiMethod>();
    myTypes = new HashMap<PsiElement, PsiType>();
    myVisitedConstructions = new HashSet<PsiAnchor>();
    myTypeVariableFactory = new PsiTypeVariableFactory();
  }

  public HashSet<PsiElement> collect(final PsiElement[] scopes) {
    return new VictimCollector(scopes, mySettings).getVictims();
  }

  private boolean verifyMethod(final PsiElement element, final HashSet<PsiElement> victims, final PsiSearchHelper helper) {
    PsiMethod method = null;
    PsiParameter parameter = null;
    int index = 0;

    if (element instanceof PsiMethod) {
      method = (PsiMethod)element;
    }
    else if (element instanceof PsiParameter) {
      parameter = (PsiParameter)element;
      method = (PsiMethod)parameter.getDeclarationScope();
      index = method.getParameterList().getParameterIndex(parameter);
    }
    else {
      LOG.error("Parameter or method expected, but found " + (element == null ? "null" : element.getClass().getName()));
      return false;
    }

    final PsiMethod superMethod = PsiSuperMethodUtil.findDeepestSuperMethod(method);
    PsiMethod keyMethod;
    PsiParameter keyParameter = null;

    if (superMethod != null) {
      Boolean good = myMethodCache.get(superMethod);

      if (good != null && !good.booleanValue()) {
        return false;
      }

      final PsiElement e =
        parameter == null ? (PsiElement)superMethod : superMethod.getParameterList().getParameters()[index];

      if (!victims.contains(e)) {
        myMethodCache.put(superMethod, new Boolean(false));
        return false;
      }

      keyMethod = superMethod;

      if (parameter != null) {
        keyParameter = (PsiParameter)e;
        myParameters.put(parameter, keyParameter);
      }
    }
    else {
      Boolean good = myMethodCache.get(method);

      if (good != null) {
        return good.booleanValue();
      }

      keyMethod = method;
      keyParameter = parameter;
    }

    final PsiMethod[] overriders = helper.findOverridingMethods(keyMethod, helper.getAccessScope(keyMethod), true);
    PsiMethod prev = keyMethod;

    for (int i = 0; i < overriders.length; i++) {
      PsiMethod overrider = overriders[i];

      if (prev != null) {
        mySuper.put(overrider, prev);
        prev = overrider;
      }

      PsiElement e =
        parameter == null ? (PsiElement)overrider : overrider.getParameterList().getParameters()[index];

      if (!victims.contains(e)) {
        myMethodCache.put(keyMethod, new Boolean(false));
        return false;
      }

      if (parameter != null) {
        myParameters.put(parameter, keyParameter);
      }
    }

    myMethodCache.put(keyMethod, new Boolean(true));

    return true;
  }

  private void setType(final PsiElement e, final PsiType t) {
    myTypes.put(e, t);
  }

  private PsiType defineType(final PsiElement e) {
    PsiType t = myTypes.get(e);

    if (t != null) {
      return t;
    }

    if (e instanceof PsiVariable) {
      t = ((PsiVariable)e).getType();
    }
    else if (e instanceof PsiTypeCastExpression) {
      t = ((PsiTypeCastExpression)e).getCastType().getType();
    }
    else if (e instanceof PsiNewExpression) {
      t = ((PsiNewExpression)e).getType();
    }
    else if (e instanceof PsiMethod) {
      t = ((PsiMethod)e).getReturnType();
    }
    else {
      LOG.error("Variable, method, new or cast expected but found " + (e == null ? " null" : e.getClass().getName()));
    }

    final PsiType parameterizedType = Util.createParameterizedType(t, myTypeVariableFactory);

    myTypes.put(e, parameterizedType);

    return parameterizedType;
  }

  private PsiType getType(final PsiElement e) {
    final PsiType t = myTypes.get(e);

    if (t != null) {
      return t;
    }

    if (e instanceof PsiVariable) {
      return ((PsiVariable)e).getType();
    }
    else if (e instanceof PsiExpression) {
      return ((PsiExpression)e).getType();
    }

    return null;
  }

  private boolean isCooked(final PsiElement element) {
    return myTypes.get(element) != null;
  }

  private void addUsage(final System system, final PsiElement element) {

    class TypeEvaluator {
      PsiType valuateType(final PsiExpression expr) {
        return evaluateType(expr);
      }

      PsiType evaluateType(final PsiExpression expr) {
        if (expr instanceof PsiArrayAccessExpression && !mySettings.preserveRawArrays()) {
          final PsiType at = evaluateType(((PsiArrayAccessExpression)expr).getArrayExpression());

          if (at instanceof PsiArrayType) {
            return ((PsiArrayType)at).getComponentType();
          }
        }
        else if (expr instanceof PsiAssignmentExpression) {
          return evaluateType(((PsiAssignmentExpression)expr).getLExpression());
        }
        else if (expr instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression call = ((PsiMethodCallExpression)expr);
          final PsiMethod method = call.resolveMethod();

          if (method != null) {
            final PsiClass aClass = method.getContainingClass();
            final PsiTypeParameter[] aTypeParms = method.getTypeParameterList().getTypeParameters();
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            final PsiExpression[] arguments = call.getArgumentList().getExpressions();
            final PsiExpression aQualifier = call.getMethodExpression().getQualifierExpression();
            final PsiExpression[] actualParms = call.getArgumentList().getExpressions();
            final HashSet<PsiTypeParameter> typeParameters = new HashSet<PsiTypeParameter>();

            for (int i = 0; i < aTypeParms.length; i++) {
              typeParameters.add(aTypeParms[i]);
            }

            PsiSubstitutor qualifierSubstitutor = PsiSubstitutor.EMPTY;
            PsiSubstitutor supertypeSubstitutor = PsiSubstitutor.EMPTY;

            PsiType aType = method.getReturnType();

            if (aQualifier != null) {
              final PsiType qualifierType = evaluateType(aQualifier);
              final PsiClassType.ClassResolveResult result = Util.resolveType(qualifierType);

              if (result.getElement() != null) {
                final PsiClass qualifierClass = result.getElement();

                qualifierSubstitutor = result.getSubstitutor();

                if (!qualifierClass.equals(aClass)) {
                  supertypeSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, qualifierClass, PsiSubstitutor.EMPTY);

                  aType = Util.substituteType(aType, supertypeSubstitutor);
                }

                aType = Util.substituteType(aType, qualifierSubstitutor);
              }
            }

            // Bind method type parameters here. Remember: intersection types are needed :((
            // Example:
            //     <T extends Foo> T foo (List<T> a, List<T> b) {...}
            //     class List<X extends Foo> {...}
            //
            //     List x; -- List<'a> x
            //     List y; -- List<'b> y
            //
            //     foo (x, y) -- type is 'a ^ 'b. Woof, woof....

            final HashMap<PsiTypeParameter, PsiType> mapping = new HashMap<PsiTypeParameter, PsiType>();

            for (int i = 0; i < Math.min(parameters.length, arguments.length); i++) {
              final PsiType argumenType = evaluateType(arguments[i]);
              final PsiType parmType = getType(parameters[i]);

              if (isCooked(parameters[i])) {
                system.addSubtypeConstraint(argumenType, parmType);
              }
              else {
                final PsiType theType =
                  new Object() {
                    PsiType introduceAdditionalTypeVariables(final PsiType type,
                                                             final PsiSubstitutor qualifier,
                                                             final PsiSubstitutor supertype) {
                      final PsiClassType.ClassResolveResult result = Util.resolveType(type);
                      final PsiClass aClass = result.getElement();

                      if (aClass != null) {
                        if (aClass instanceof PsiTypeParameter){
                          final PsiTypeParameter tp = ((PsiTypeParameter)aClass);

                          PsiType pv = mapping.get(tp);

                          if (pv == null) {
                            pv = myTypeVariableFactory.create();
                            mapping.put(tp, pv);
                          }

                          return pv;
                        }

                        final Map<PsiTypeParameter, PsiType> substitutionMap = result.getSubstitutor().getSubstitutionMap();

                        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                        for (Iterator<PsiTypeParameter> t = substitutionMap.keySet().iterator(); t.hasNext();) {
                          final PsiTypeParameter p = t.next();
                          final PsiType pType = substitutionMap.get(p);

                          if (pType instanceof PsiWildcardType) {
                            final PsiWildcardType wildcard = ((PsiWildcardType)pType);
                            final PsiType theBound = wildcard.getBound();

                            if (theBound != null) {
                              final PsiType bound = Util.substituteType(Util.substituteType(theBound, supertype), qualifier);

                              if (Util.bindsTypeVariables(bound)) {
                                final PsiType var = myTypeVariableFactory.create();

                                if (wildcard.isExtends()) {
                                  system.addSubtypeConstraint(bound, var);
                                }
                                else {
                                  system.addSubtypeConstraint(var, bound);
                                }

                                theSubst = theSubst.put(p, var);
                              }
                              else if (Util.bindsTypeParameters(bound, typeParameters)) {
                                final PsiType var = myTypeVariableFactory.create();
                                PsiSubstitutor subst = PsiSubstitutor.EMPTY;

                                for (int i = 0; i < aTypeParms.length; i++) {
                                  final PsiTypeParameter aTypeParm = aTypeParms[i];

                                  PsiType parmVar = mapping.get(aTypeParm);

                                  if (parmVar == null) {
                                    parmVar = myTypeVariableFactory.create();
                                    mapping.put(aTypeParm, parmVar);
                                  }

                                  subst = subst.put(aTypeParm, parmVar);
                                }

                                final PsiType bnd = Util.substituteType(bound, subst);

                                if (wildcard.isExtends()) {
                                  system.addSubtypeConstraint(bnd, var);
                                }
                                else {
                                  system.addSubtypeConstraint(var, bnd);
                                }

                                theSubst = theSubst.put(p, var);
                              }
                              else {
                                theSubst = theSubst.put(p, pType);
                              }
                            }
                          }
                          else {
                            theSubst = theSubst.put(p, introduceAdditionalTypeVariables(pType, qualifier, supertype));
                          }
                        }

                        return aClass.getManager().getElementFactory().createType(aClass, theSubst);
                      }

                      return type;
                    }
                  }.introduceAdditionalTypeVariables(Util.substituteType(Util.substituteType(parmType, supertypeSubstitutor), qualifierSubstitutor), qualifierSubstitutor, supertypeSubstitutor);

                system.addSubtypeConstraint(argumenType, theType);
                                            //Util.substituteType(Util.substituteType(theType, supertypeSubstitutor), qualifierSubstitutor));
              }

              new Object() {
                private void update(final PsiTypeParameter p, final PsiType t) {
                  final PsiType binding = mapping.get(p);

                  if (binding == null) {
                    mapping.put(p, t);
                  }
                  else if (t != null) {
                    mapping.put(p, new PsiTypeIntersection(binding, t));
                  }
                }

                void bindTypeParameters(final PsiType formal, final PsiType actual) {
                  final PsiClassType.ClassResolveResult resultF = Util.resolveType(formal);

                  if (resultF.getElement() != null) {
                    final PsiClass classF = resultF.getElement();

                    if (classF instanceof PsiTypeParameter) {
                      update((PsiTypeParameter)classF, actual);
                      return;
                    }

                    final PsiClassType.ClassResolveResult resultA = Util.resolveType(actual);

                    if (resultA.getElement() == null) {
                      return;
                    }

                    final PsiClass classA = resultA.getElement();

                    if (!classA.equals(classF)) {
                      final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(classF, classA,
                                                                                                               PsiSubstitutor.EMPTY);
                      final PsiType aligned = classF.getManager().getElementFactory().createType(classF, superClassSubstitutor);

                      bindTypeParameters(formal, Util.substituteType(aligned, resultA.getSubstitutor()));
                    }

                    final PsiTypeParameter[] typeParms = Util.getTypeParametersList(classA);
                    final PsiSubstitutor substA = resultA.getSubstitutor();
                    final PsiSubstitutor substF = resultF.getSubstitutor();

                    for (int i = 0; i < typeParms.length; i++) {
                      PsiTypeParameter typeParm = typeParms[i];
                      bindTypeParameters(substF.substitute(typeParm), substA.substitute(typeParm));
                    }
                  }
                }
              }.bindTypeParameters(parmType, evaluateType(actualParms[i]));
            }

            PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

            for (Iterator<PsiTypeParameter> i = mapping.keySet().iterator(); i.hasNext();){
              final PsiTypeParameter parm = i.next();
              final PsiType type = mapping.get(parm);

              theSubst = theSubst.put(parm, type);
            }

            return Util.substituteType(aType, theSubst);
          }
        }
        else if (expr instanceof PsiParenthesizedExpression) {
          return evaluateType(((PsiParenthesizedExpression)expr).getExpression());
        }
        else if (expr instanceof PsiConditionalExpression) {
          return evaluateType(((PsiConditionalExpression)expr).getThenExpression());
        }
        else if (expr instanceof PsiNewExpression) {
          final PsiExpression qualifier = ((PsiNewExpression)expr).getQualifier();

          if (qualifier != null) {
            final PsiClassType.ClassResolveResult qualifierResult = Util.resolveType(evaluateType(qualifier));

            if (qualifierResult.getElement() != null) {
              final PsiSubstitutor qualifierSubs = qualifierResult.getSubstitutor();
              final PsiClassType.ClassResolveResult result = Util.resolveType(expr.getType());

              if (result.getElement() != null) {
                final PsiClass aClass = result.getElement();

                return aClass.getManager().getElementFactory().createType(aClass, result.getSubstitutor().putAll(qualifierSubs));
              }
            }
          }
        }
        else if (expr instanceof PsiReferenceExpression) {
          final PsiReferenceExpression ref = ((PsiReferenceExpression)expr);
          final PsiExpression qualifier = ref.getQualifierExpression();

          if (qualifier == null) {
            return getType(ref.resolve());
          }
          else {
            final PsiType qualifierType = evaluateType(qualifier);
            final PsiElement element = ref.resolve();

            final PsiClassType.ClassResolveResult result = Util.resolveType(qualifierType);

            if (result.getElement() != null) {
              final PsiClass aClass = result.getElement();
              final PsiSubstitutor aSubst = result.getSubstitutor();

              if (element instanceof PsiField) {
                final PsiField field = (PsiField)element;
                final PsiType fieldType = getType(field);
                final PsiClass superClass = field.getContainingClass();

                PsiType aType = fieldType;

                if (!aClass.equals(superClass)) {
                  aType =
                  Util.substituteType(aType, TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY));
                }

                return Util.substituteType(aType, aSubst);
              }
            }
          }
        }

        return getType(expr);
      }
    }

    final TypeEvaluator e = new TypeEvaluator();

    if (element instanceof PsiVariable) {
      final PsiExpression initializer = ((PsiVariable)element).getInitializer();

      if (initializer != null) {
        system.addSubtypeConstraint(e.valuateType(initializer), getType(element));
      }

      return;
    }

    final PsiStatement root = (PsiStatement)PsiTreeUtil.getParentOfType(element, PsiStatement.class);

    if (root != null) {
      final PsiAnchor anchor = new PsiAnchor(root);

      if (!myVisitedConstructions.contains(anchor)) {
        root.accept(new PsiRecursiveElementVisitor() {
          public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);

            system.addSubtypeConstraint(e.valuateType(expression.getRExpression()), e.valuateType(expression.getLExpression()));
          }

          public void visitConditionalExpression(final PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);

            system.addSubtypeConstraint(e.valuateType(expression.getThenExpression()), e.valuateType(expression.getElseExpression()));
            system.addSubtypeConstraint(e.valuateType(expression.getElseExpression()), e.valuateType(expression.getThenExpression()));
          }

          public void visitMethodCallExpression(final PsiMethodCallExpression expression) {
            e.valuateType(expression);
          }

          public void visitReturnStatement(final PsiReturnStatement statement) {
            super.visitReturnStatement(statement);

            final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);

            if (method != null) {
              system.addSubtypeConstraint(method.getReturnType(), e.valuateType(statement.getReturnValue()));
            }
          }

          public void visitTypeCastExpression(final PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);

            system.addSubtypeConstraint(e.valuateType(expression.getOperand()), e.valuateType(expression));
          }

          public void visitReferenceExpression(final PsiReferenceExpression expression) {
          }
        });

        myVisitedConstructions.add(anchor);
      }
    }
  }

  private void addBoundConstraints(final System system, final PsiType definedType, final PsiElement element) {
    final PsiType elemenType =
      (element instanceof PsiMethod) ? ((PsiMethod)element).getReturnType() :
      (element instanceof PsiVariable) ? ((PsiVariable)element).getType() : null;

    if (elemenType != null) {
      new Object() {
        void brrrr(final PsiType defined, final PsiType type) {
          final PsiClassType.ClassResolveResult resultDefined = Util.resolveType(defined);
          final PsiClassType.ClassResolveResult resultType = Util.resolveType(type);
          final PsiClass definedClass = resultDefined.getElement();

          if (definedClass == null || !definedClass.equals(resultType.getElement())) {
            return;
          }

          final PsiSubstitutor definedSubst = resultDefined.getSubstitutor();
          final PsiSubstitutor typeSubst = resultType.getSubstitutor();

          for (Iterator<PsiTypeParameter> i = definedSubst.getSubstitutionMap().keySet().iterator(); i.hasNext();) {
            final PsiTypeParameter parameter = i.next();
            final PsiClassType[] extendsList = parameter.getExtendsList().getReferencedTypes();
            final PsiType definedType = definedSubst.substitute(parameter);

            if (definedType instanceof PsiTypeVariable) {
              for (int j = 0; j < extendsList.length; j++) {
                final PsiType extendsType =
                  new Object() {
                    PsiType replaceWildCards(final PsiType type) {
                      if (type instanceof PsiWildcardType) {
                        final PsiWildcardType wildcard = ((PsiWildcardType)type);
                        final PsiType var = myTypeVariableFactory.create();
                        final PsiType bound = wildcard.getBound();

                        if (bound != null) {
                          if (wildcard.isExtends()) {
                            system.addSubtypeConstraint(Util.substituteType(replaceWildCards(bound), definedSubst), var);
                          }
                          else {
                            system.addSubtypeConstraint(var, Util.substituteType(replaceWildCards(bound), definedSubst));
                          }
                        }

                        return var;
                      }
                      else if (type instanceof PsiClassType) {
                        final PsiClassType.ClassResolveResult result = Util.resolveType(type);
                        final PsiClass aClass = result.getElement();
                        final PsiSubstitutor aSubst = result.getSubstitutor();

                        if (aClass != null) {
                          PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                          for (Iterator<PsiTypeParameter> i = aSubst.getSubstitutionMap().keySet().iterator(); i.hasNext();) {
                            final PsiTypeParameter p = i.next();

                            theSubst = theSubst.put(p, replaceWildCards(aSubst.substitute(p)));
                          }

                          return aClass.getManager().getElementFactory().createType(aClass, theSubst);
                        }
                      }

                      return type;
                    }
                  }.replaceWildCards(extendsList[j]);

                system.addSubtypeConstraint(Util.substituteType(extendsType, definedSubst), definedType);
              }
            }
            else {
              brrrr(definedType, typeSubst.substitute(parameter));
            }
          }
        }
      }.brrrr(definedType, elemenType);
    }
  }

  public System build(final HashSet<PsiElement> victims) {
    final PsiSearchHelper helper = myManager.getSearchHelper();

    System system = new System(victims, myTypes, myTypeVariableFactory);

    for (Iterator<PsiElement> i = victims.iterator(); i.hasNext();) {
      final PsiElement element = i.next();

      if (element instanceof PsiParameter) {
        if (!verifyMethod(element, victims, helper)) {
          continue;
        }
      }
      else if (element instanceof PsiMethod) {
        if (!verifyMethod(element, victims, helper)) {
          continue;
        }
      }
    }

    PsiType definedType;

    for (Iterator<PsiElement> i = victims.iterator(); i.hasNext();) {
      final PsiElement element = i.next();

      if (element instanceof PsiParameter) {
        final PsiParameter p = myParameters.get(element);

        if (p != null) {
          setType(element, definedType = defineType(p));
        }
        else {
          definedType = defineType(element);
        }
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod m = mySuper.get(element);

        if (m != null) {
          system.addSubtypeConstraint(defineType(element), definedType = defineType(m));
        }
        else {
          definedType = defineType(element);
        }
      }
      else {
        definedType = defineType(element);
      }

      addBoundConstraints(system, definedType, element);
    }

    for (Iterator<PsiElement> i = victims.iterator(); i.hasNext();) {
      final PsiElement element = i.next();

      addUsage(system, element);

      if (!(element instanceof PsiExpression)) {
        PsiReference[] refs = helper.findReferences(element, helper.getAccessScope(element), true);

        for (int k = 0; k < refs.length; k++) {
          final PsiElement ref = refs[k].getElement();

          if (ref != null) {
            addUsage(system, ref);
          }
        }
      }
    }

    return system;
  }
}
