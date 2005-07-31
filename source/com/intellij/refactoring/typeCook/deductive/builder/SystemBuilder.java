package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.util.VictimCollector;
import com.intellij.util.containers.HashMap;

import java.util.Arrays;
import java.util.HashSet;
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
  private HashMap<PsiMethod, PsiMethod> myMethods;
  private HashMap<PsiMethod, PsiMethod> mySuper;
  private HashMap<PsiElement, PsiType> myTypes;
  private HashSet<PsiAnchor> myVisitedConstructions;
  private Settings mySettings;
  private PsiTypeVariableFactory myTypeVariableFactory;
  private Project myProject;

  public SystemBuilder(final Project project, final Settings settings) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
    mySettings = settings;
    myMethodCache = new HashMap<PsiElement, Boolean>();
    myParameters = new HashMap<PsiParameter, PsiParameter>();
    myMethods = new HashMap<PsiMethod, PsiMethod>();
    mySuper = new HashMap<PsiMethod, PsiMethod>();
    myTypes = new HashMap<PsiElement, PsiType>();
    myVisitedConstructions = new HashSet<PsiAnchor>();
    myTypeVariableFactory = new PsiTypeVariableFactory();
  }

  private HashSet<PsiElement> collect(final PsiElement[] scopes) {
    return new VictimCollector(scopes, mySettings).getVictims();
  }

  private boolean verifyMethod(final PsiElement element, final HashSet<PsiElement> victims, final PsiSearchHelper helper) {
    PsiMethod method;
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

      myMethods.put(method, keyMethod);

      if (parameter != null) {
        keyParameter = (PsiParameter)e;
      }
    }
    else {
      Boolean good = myMethodCache.get(method);

      if (good != null && good.booleanValue()) {
        if (myMethods.get(method) == null) {
          myMethods.put(method, method);
        }

        if (parameter != null && myParameters.get(parameter) == null) {
          myParameters.put(parameter, parameter);
        }

        return true;
      }

      keyMethod = method;
      keyParameter = parameter;
    }

    final PsiMethod[] overriders = helper.findOverridingMethods(keyMethod, keyMethod.getUseScope(), true);

    for (final PsiMethod overrider : overriders) {
      final PsiElement e = parameter == null ? (PsiElement)overrider : overrider.getParameterList().getParameters()[index];

      if (!victims.contains(e)) {
        myMethodCache.put(keyMethod, new Boolean(false));
        return false;
      }
    }

    for (final PsiMethod overrider : overriders) {
      final PsiElement e = parameter == null ? (PsiElement)overrider : overrider.getParameterList().getParameters()[index];

      mySuper.put(overrider, keyMethod);
      myMethods.put(overrider, keyMethod);

      if (parameter != null) {
        myParameters.put((PsiParameter)e, keyParameter);
      }
    }

    myMethods.put(method, keyMethod);

    if (parameter != null) {
      myParameters.put(parameter, keyParameter);
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

    t = Util.getType(e);

    final PsiType parameterizedType = Util.createParameterizedType(t, myTypeVariableFactory, e);

    myTypes.put(e, parameterizedType);

    return parameterizedType;
  }

  private PsiType getType(final PsiElement e) {
    final PsiType t = myTypes.get(e);

    if (t != null) {
      return t;
    }

    return Util.banalize(Util.getType(e));
  }

  private boolean isCooked(final PsiElement element) {
    return myTypes.get(element) != null;
  }

  private void addUsage(final System system, final PsiElement element) {

    class TypeEvaluator {
      public PsiType inferTypeForMethodTypeParameter(final PsiTypeParameter typeParameter,
                                                     final PsiParameter[] parameters,
                                                     PsiExpression[] arguments, PsiSubstitutor partialSubstitutor, PsiElement parent) {
        PsiType substitution = PsiType.NULL;
        PsiResolveHelper helper = typeParameter.getManager().getResolveHelper();
        if (parameters.length > 0) {
          for (int j = 0; j < arguments.length; j++) {
            PsiExpression argument = arguments[j];
            final PsiParameter parameter = parameters[Math.min(j, parameters.length - 1)];
            if (j >= parameters.length && !parameter.isVarArgs()) break;
            PsiType parameterType = parameter.getType();
            PsiType argumentType = evaluateType(argument);

            if (parameterType instanceof PsiEllipsisType) {
              parameterType = ((PsiEllipsisType)parameterType).getComponentType();
              if (arguments.length == parameters.length && argumentType instanceof PsiArrayType &&
                  !(((PsiArrayType)argumentType).getComponentType() instanceof PsiPrimitiveType)) {
                argumentType = ((PsiArrayType)argumentType).getComponentType();
              }
            }
            final PsiType currentSubstitution = helper.getSubstitutionForTypeParameter(typeParameter, parameterType,
                                                                                       argumentType, true);
            if (currentSubstitution == null) {
              substitution = null;
              break;
            }
            else if (currentSubstitution instanceof PsiWildcardType) {
              if (substitution instanceof PsiWildcardType) return PsiType.NULL;
            }
            else if (currentSubstitution == PsiType.NULL) continue;

            if (substitution == PsiType.NULL) {
              substitution = currentSubstitution;
              continue;
            }
            if (!substitution.equals(currentSubstitution)) {
              if (substitution instanceof PsiTypeVariable || currentSubstitution instanceof PsiTypeVariable) {
                substitution = GenericsUtil.getLeastUpperBound(substitution, currentSubstitution, typeParameter.getManager());
                if (substitution == null) break;
              }
            }
          }
        }

        if (substitution == PsiType.NULL) {
          substitution = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent);
        }
        return substitution;
      }

      private PsiType inferMethodTypeParameterFromParent(final PsiTypeParameter typeParameter,
                                                         PsiSubstitutor substitutor,
                                                         PsiElement parent) {
        PsiTypeParameterListOwner owner = typeParameter.getOwner();
        PsiType substitution = PsiType.NULL;
        if (owner instanceof PsiMethod) {
          if (parent instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
            substitution = inferMethodTypeParameterFromParent(methodCall.getParent(), methodCall, typeParameter, substitutor);
          }
        }
        return substitution;
      }

      private PsiType inferMethodTypeParameterFromParent(PsiElement parent,
                                                         PsiMethodCallExpression methodCall,
                                                         final PsiTypeParameter typeParameter,
                                                         PsiSubstitutor substitutor) {
        PsiType type = null;

        if (parent instanceof PsiVariable && methodCall.equals(((PsiVariable)parent).getInitializer())) {
          type = getType(parent);
        }
        else if (parent instanceof PsiAssignmentExpression && methodCall.equals(((PsiAssignmentExpression)parent).getRExpression())) {
          type = evaluateType(((PsiAssignmentExpression)parent).getLExpression());
        }
        else if (parent instanceof PsiTypeCastExpression && methodCall.equals(((PsiTypeCastExpression)parent).getOperand())) {
          type = evaluateType((PsiExpression)parent);
        }
        else if (parent instanceof PsiReturnStatement) {
          PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
          if (method != null) {
            type = getType(method);
          }
        }

        if (type == null) {
          type = PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope());
        }

        PsiType returnType = ((PsiMethod)typeParameter.getOwner()).getReturnType();
        PsiType guess = parent.getManager().getResolveHelper().getSubstitutionForTypeParameter(typeParameter, returnType, type, false);

        if (guess == PsiType.NULL) {
          PsiType superType = substitutor.substitute(typeParameter.getSuperTypes()[0]);
          return superType == null ? PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope()) : superType;
        }

        //The following code is the result of deep thought, do not shit it out before discussing with [ven]
        if (returnType instanceof PsiClassType && typeParameter.equals(((PsiClassType)returnType).resolve())) {
          PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
          PsiSubstitutor newSubstitutor = substitutor.put(typeParameter, guess);
          for (PsiClassType t : extendsTypes) {
            PsiType extendsType = newSubstitutor.substitute(t);
            if (!extendsType.isAssignableFrom(guess)) {
              if (guess.isAssignableFrom(extendsType)) {
                guess = extendsType;
                newSubstitutor = substitutor.put(typeParameter, guess);
              }
              else {
                break;
              }
            }
          }
        }

        return guess;
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
        else if (expr instanceof PsiCallExpression) {
          final PsiCallExpression call = ((PsiCallExpression)expr);
          final PsiMethod method = call.resolveMethod();

          if (method != null) {
            final PsiClass aClass = method.getContainingClass();
            final PsiTypeParameter[] methodTypeParams = method.getTypeParameters();
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            final PsiExpression[] arguments = call.getArgumentList().getExpressions();
            final PsiExpression aQualifier = expr instanceof PsiMethodCallExpression
              ? ((PsiMethodCallExpression)expr).getMethodExpression().getQualifierExpression()
              : null;

            final HashSet<PsiTypeParameter> typeParameters = new HashSet<PsiTypeParameter>(Arrays.asList(methodTypeParams));

            PsiSubstitutor qualifierSubstitutor = PsiSubstitutor.EMPTY;
            PsiSubstitutor supertypeSubstitutor = PsiSubstitutor.EMPTY;

            PsiType aType;

            if (method.isConstructor()) {
              aType = isCooked(expr) ? getType(expr) : expr.getType();
              qualifierSubstitutor = Util.resolveType(aType).getSubstitutor();
            }
            else {
              aType = getType(method);
            }

            if (aQualifier != null) {
              final PsiType qualifierType = evaluateType(aQualifier);
              final PsiClassType.ClassResolveResult result = Util.resolveType(qualifierType);

              if (result.getElement() != null) {
                final PsiClass qualifierClass = result.getElement();

                qualifierSubstitutor = TypeConversionUtil.getClassSubstitutor(aClass, qualifierClass, result.getSubstitutor());

                if (qualifierSubstitutor != null) {
                  aType = qualifierSubstitutor.substitute(aType);
                }
              }
            }

            final HashMap<PsiTypeParameter, PsiType> mapping = new HashMap<PsiTypeParameter, PsiType>();

            for (int i = 0; i < Math.min(parameters.length, arguments.length); i++) {
              final PsiType argumentType = evaluateType(arguments[i]);

              PsiType parmType;

              if (isCooked(parameters[i])) {
                parmType = getType(parameters[i]);
                system.addSubtypeConstraint(argumentType, parmType);
              }
              else {
                parmType = qualifierSubstitutor.substitute(supertypeSubstitutor.substitute(parameters[i].getType()));

                if (!Util.bindsTypeVariables(parmType) && !Util.bindsTypeParameters(parmType, typeParameters)) {
                  parmType = Util.banalize(parmType);
                }

                final PsiType theType =
                new Object() {
                  PsiType introduceAdditionalTypeVariables(final PsiType type,
                                                           final PsiSubstitutor qualifier,
                                                           final PsiSubstitutor supertype) {
                    final int level = type.getArrayDimensions();
                    final PsiClassType.ClassResolveResult result = Util.resolveType(type);
                    final PsiClass aClass = result.getElement();

                    if (aClass != null) {
                      if (aClass instanceof PsiTypeParameter) {
                        final PsiTypeParameter tp = ((PsiTypeParameter)aClass);
                        final PsiClassType[] extypes = tp.getExtendsListTypes();

                        PsiType pv = mapping.get(tp);

                        if (pv == null) {
                          pv = myTypeVariableFactory.create();
                          mapping.put(tp, pv);
                        }

                        for (final PsiClassType ext : extypes) {
                          final PsiType extype = qualifier.substitute(new Object() {
                            public PsiType substitute(final PsiType ext) {
                              final PsiClassType.ClassResolveResult result =
                                Util.resolveType(ext);
                              final PsiClass aClass = result.getElement();

                              if (aClass != null) {
                                if (aClass instanceof PsiTypeParameter) {
                                  final PsiType type = mapping.get(aClass);

                                  if (type != null) {
                                    return type;
                                  }

                                  return ext;
                                }

                                final PsiSubstitutor aSubst = result.getSubstitutor();
                                PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                                for (final PsiTypeParameter parm : aSubst.getSubstitutionMap().keySet()) {
                                  PsiType type = aSubst.substitute(parm);

                                  if (type != null) {
                                    type = substitute(type);
                                  }

                                  theSubst = theSubst.put(parm, type);
                                }

                                return aClass.getManager().getElementFactory()
                                  .createType(aClass, theSubst);
                              }

                              return ext;
                            }
                          }.substitute(ext));
                          system.addSubtypeConstraint(pv, extype);
                        }

                        return Util.createArrayType(pv, level);
                      }

                      final Map<PsiTypeParameter, PsiType> substitutionMap = result.getSubstitutor().getSubstitutionMap();

                      PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                      for (final PsiTypeParameter p : substitutionMap.keySet()) {
                        final PsiType pType = substitutionMap.get(p);

                        if (pType instanceof PsiWildcardType) {
                          final PsiWildcardType wildcard = ((PsiWildcardType)pType);
                          final PsiType theBound = wildcard.getBound();

                          if (theBound != null) {
                            final PsiType bound = qualifier.substitute(supertype.substitute(theBound));

                            if (Util.bindsTypeVariables(bound)) {
                              final PsiType var = myTypeVariableFactory.create();

                              if (wildcard.isExtends()) {
                                system.addSubtypeConstraint(var, bound);
                              }
                              else {
                                system.addSubtypeConstraint(bound, var);
                              }

                              theSubst = theSubst.put(p, var);
                            }
                            else if (Util.bindsTypeParameters(bound, typeParameters)) {
                              final PsiType var = myTypeVariableFactory.create();
                              PsiSubstitutor subst = PsiSubstitutor.EMPTY;

                              for (final PsiTypeParameter aTypeParm : methodTypeParams) {
                                PsiType parmVar = mapping.get(aTypeParm);

                                if (parmVar == null) {
                                  parmVar = myTypeVariableFactory.create();
                                  mapping.put(aTypeParm, parmVar);
                                }

                                subst = subst.put(aTypeParm, parmVar);
                              }

                              final PsiType bnd = subst.substitute(bound);

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

                      return Util.createArrayType(aClass.getManager().getElementFactory().createType(aClass, theSubst), level);
                    }

                    return Util.createArrayType(type, level);
                  }
                }.introduceAdditionalTypeVariables(parmType, qualifierSubstitutor, supertypeSubstitutor);

                system.addSubtypeConstraint(argumentType, theType);
              }
            }

            PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

            for (final PsiTypeParameter parm : mapping.keySet()) {
              final PsiType type = mapping.get(parm);

              theSubst = theSubst.put(parm, type);
            }

            for (PsiTypeParameter typeParam : methodTypeParams) {
              final PsiType inferred = inferTypeForMethodTypeParameter(typeParam, parameters, arguments, theSubst, expr);
              theSubst = theSubst.put(typeParam, inferred);
            }

            return theSubst.substitute(aType);
          }
        }
        else if (expr instanceof PsiParenthesizedExpression) {
          return evaluateType(((PsiParenthesizedExpression)expr).getExpression());
        }
        else if (expr instanceof PsiConditionalExpression) {
          return evaluateType(((PsiConditionalExpression)expr).getThenExpression());
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

                if (!aClass.equals(superClass) && field.isPhysical()) {
                  aType =
                    TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY).substitute(aType);
                }

                return aSubst.substitute(aType);
              }
            }
            else if (element != null) {
              return getType(element);
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
        final PsiExpression core = deparenthesizeExpression(initializer);

        if (core instanceof PsiArrayInitializerExpression) {
          final PsiExpression[] inits = ((PsiArrayInitializerExpression)core).getInitializers();
          final PsiType type = getType(element);

          for (PsiExpression init : inits) {
            system.addSubtypeConstraint(e.evaluateType(init).createArrayType(), type);
          }
        }
        else if (core instanceof PsiNewExpression) {
          final PsiArrayInitializerExpression init = ((PsiNewExpression)core).getArrayInitializer();

          if (init != null) {
            final PsiExpression[] inits = init.getInitializers();
            final PsiType type = getType(element);

            for (PsiExpression init1 : inits) {
              system.addSubtypeConstraint(e.evaluateType(init1).createArrayType(), type);
            }
          }

          system.addSubtypeConstraint(e.evaluateType(core), getType(element));
        }
        else {
          system.addSubtypeConstraint(e.evaluateType(core), getType(element));
        }
      }

      if (element instanceof PsiParameter) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

        if (method != null) {
          final PsiSearchHelper helper = myManager.getSearchHelper();
          SearchScope scope = getScope(helper, method);

          final PsiReference[] refs = helper.findReferences(method, scope, true);

          for (PsiReference ref : refs) {
            final PsiElement elt = ref.getElement();

            if (elt != null) {
              final PsiCallExpression call = PsiTreeUtil.getParentOfType(elt, PsiCallExpression.class);

              if (call != null) {
                final PsiExpression arg = call.getArgumentList().getExpressions()[method.getParameterList().getParameterIndex(
                  (PsiParameter)element)];

                system.addSubtypeConstraint(e.evaluateType(arg), myTypes.get(element));
              }
            }
          }
        }
      }
      return;
    }
    else if (element instanceof PsiMethod) {
      final PsiType reType = getType(element);

      element.accept(new PsiRecursiveElementVisitor() {
                       public void visitReturnStatement(final PsiReturnStatement statement) {
                         super.visitReturnStatement(statement);

                         final PsiExpression retExpr = statement.getReturnValue();

                         if (retExpr != null) {
                           system.addSubtypeConstraint(e.evaluateType(retExpr), reType);
                         }
                       }
                     }
      );

      return;
    }

    final PsiStatement root = PsiTreeUtil.getParentOfType(element, PsiStatement.class);

    if (root != null) {
      final PsiAnchor anchor = new PsiAnchor(root);

      if (!myVisitedConstructions.contains(anchor)) {
        root.accept(new PsiRecursiveElementVisitor() {
                      public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
                        super.visitAssignmentExpression(expression);

                        system.addSubtypeConstraint(e.evaluateType(expression.getRExpression()), e.evaluateType(expression.getLExpression()));
                      }

                      public void visitConditionalExpression(final PsiConditionalExpression expression) {
                        super.visitConditionalExpression(expression);

                        system.addSubtypeConstraint(e.evaluateType(expression.getThenExpression()),
                                                    e.evaluateType(expression.getElseExpression()));
                        system.addSubtypeConstraint(e.evaluateType(expression.getElseExpression()),
                                                    e.evaluateType(expression.getThenExpression()));
                      }

                      public void visitCallExpression(final PsiCallExpression expression) {
                        super.visitCallExpression(expression);
                        e.evaluateType(expression);
                      }

                      public void visitReturnStatement(final PsiReturnStatement statement) {
                        super.visitReturnStatement(statement);

                        final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);

                        if (method != null) {
                          system.addSubtypeConstraint(e.evaluateType(statement.getReturnValue()), getType(method));
                        }
                      }

                      public void visitTypeCastExpression(final PsiTypeCastExpression expression) {
                        super.visitTypeCastExpression(expression);

                        final PsiType operandType = e.evaluateType(expression.getOperand());
                        final PsiType castType = e.evaluateType(expression);
                        if (operandType == null || castType == null) return;

                        if (Util.bindsTypeVariables(operandType)) {
                          system.addCast(expression);
                        }

                        if (operandType.getDeepComponentType() instanceof PsiTypeVariable || castType.getDeepComponentType() instanceof PsiTypeVariable) {
                          system.addSubtypeConstraint(operandType, castType);
                        }
                        else {
                          final PsiClassType.ClassResolveResult operandResult = Util.resolveType(operandType);
                          final PsiClassType.ClassResolveResult castResult = Util.resolveType(castType);

                          final PsiClass operandClass = operandResult.getElement();
                          final PsiClass castClass = castResult.getElement();

                          if (operandClass != null && castClass != null) {
                            if (InheritanceUtil.isCorrectDescendant(operandClass, castClass, true)) {
                              system.addSubtypeConstraint(operandType, castType);
                            }
                          }
                        }
                      }

                      public void visitVariable(final PsiVariable variable) {
                        super.visitVariable(variable);

                        final PsiExpression init = variable.getInitializer();

                        if (init != null) {
                          system.addSubtypeConstraint(e.evaluateType(init), getType(variable));
                        }
                      }

                      public void visitNewExpression(final PsiNewExpression expression) {
                        super.visitNewExpression(expression);

                        final PsiArrayInitializerExpression init = expression.getArrayInitializer();

                        if (init != null) {
                          final PsiExpression[] inits = init.getInitializers();
                          final PsiType type = getType(expression);

                          for (PsiExpression init1 : inits) {
                            system.addSubtypeConstraint(e.evaluateType(init1).createArrayType(), type);
                          }
                        }
                      }

                      public void visitReferenceExpression(final PsiReferenceExpression expression) {
                        final PsiExpression qualifierExpression = expression.getQualifierExpression();

                        if (qualifierExpression != null) {
                          qualifierExpression.accept(this);
                        }
                      }
                    });

        myVisitedConstructions.add(anchor);
      }
    }
  }

  private SearchScope getScope(final PsiSearchHelper helper, final PsiElement element) {
    SearchScope scope = helper.getUseScope(element);
    if (scope instanceof GlobalSearchScope) {
      scope = GlobalSearchScope.getScopeRestrictedByFileTypes(((GlobalSearchScope)scope), StdFileTypes.JAVA, StdFileTypes.JSP, StdFileTypes.JSPX);
    }
    return scope;
  }

  private PsiExpression deparenthesizeExpression(PsiExpression rExpression) {
    if (rExpression instanceof PsiParenthesizedExpression) {
      return deparenthesizeExpression(
        ((PsiParenthesizedExpression)rExpression).getExpression());
    }
    return rExpression;
  }

  private void addBoundConstraints(final System system, final PsiType definedType, final PsiElement element) {
    final PsiType elemenType = Util.getType(element);

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

          for (final PsiTypeParameter parameter : definedSubst.getSubstitutionMap().keySet()) {
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
                            system.addSubtypeConstraint(Util.banalize(definedSubst.substitute(replaceWildCards(bound))), var);
                          }
                          else {
                            system.addSubtypeConstraint(var, Util.banalize(definedSubst.substitute(replaceWildCards(bound))));
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

                          for (final PsiTypeParameter p : aSubst.getSubstitutionMap().keySet()) {
                            theSubst = theSubst.put(p, replaceWildCards(aSubst.substitute(p)));
                          }

                          return aClass.getManager().getElementFactory().createType(aClass, theSubst);
                        }
                      }

                      return type;
                    }
                  }.replaceWildCards(extendsList[j]);

                system.addSubtypeConstraint(definedType, Util.banalize(definedSubst.substitute(extendsType)));
              }
            }
            else {
              brrrr(definedType, typeSubst.substitute(parameter));
            }
          }
        }
      }.brrrr(definedType, elemenType);

      if (mySettings.cookObjects() && elemenType.getCanonicalText().equals("java.lang.Object")) {
        system.addSubtypeConstraint(definedType, elemenType);
      }
    }
  }

  public System build(final PsiElement[] scopes) {
    return build(collect(scopes));
  }

  public System build(final HashSet<PsiElement> victims) {
    final PsiSearchHelper helper = myManager.getSearchHelper();

    System system = new System(myProject, victims, myTypes, myTypeVariableFactory, mySettings);

    for (final PsiElement element : victims) {
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

    for (final PsiElement element : victims) {
      if (element instanceof PsiParameter) {
        final PsiParameter p = myParameters.get(element);

        if (p != null) {
          setType(element, definedType = defineType(p));
        }
        else {
          continue;
        }
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod m = myMethods.get(element);

        if (m != null) {
          system.addSubtypeConstraint(defineType(element), definedType = defineType(m));
        }
        else {
          continue;
        }
      }
      else {
        definedType = defineType(element);
      }

      addBoundConstraints(system, definedType, element);
    }

    for (final PsiElement element : victims) {
      if (element instanceof PsiParameter) {
        final PsiParameter p = myParameters.get(element);

        if (p == null) continue;
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod m = myMethods.get(element);

        if (m == null) continue;
      }

      addUsage(system, element);

      if (!(element instanceof PsiExpression)) {
        final PsiReference[] refs = helper.findReferences(element, getScope(helper, element), true);

        for (PsiReference ref : refs) {
          final PsiElement elt = ref.getElement();

          if (elt != null) {
            addUsage(system, elt);
          }
        }
      }
    }

    return system;
  }
}
