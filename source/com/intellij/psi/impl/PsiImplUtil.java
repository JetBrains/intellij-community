package com.intellij.psi.impl;

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiImplUtil");

  private static final Key<Pair<Map<String,List<MethodCandidateInfo>>,Runnable>> METHODS_MAP_IN_CLASS_KEY = Key.create("METHODS_MAP_IN_CLASS_KEY");
  private static final Key<Pair<Map<String,List<CandidateInfo>>,Runnable>> FIELDS_MAP_IN_CLASS_KEY = Key.create("FIELDS_MAP_IN_CLASS_KEY");

  public static PsiMethod[] getConstructors(PsiClass aClass) {
    final List<PsiMethod> constructorsList = new ArrayList<PsiMethod>();
    final PsiMethod[] methods = aClass.getMethods();
    for (int i = 0; i < methods.length; i++) {
      final PsiMethod method = methods[i];
      if (method.isConstructor()) constructorsList.add(method);
    }
    return constructorsList.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public static Map<String, List<MethodCandidateInfo>> getAllMethodsMap(final PsiClass psiClass){
    if (!psiClass.isPhysical()) {
      return _getAllMethodsMap(psiClass);
    }

    final Pair<Map<String, List<MethodCandidateInfo>>, Runnable> value = psiClass.getUserData(METHODS_MAP_IN_CLASS_KEY);

    if (value == null) {
      final Map<String, List<MethodCandidateInfo>> allMethods = _getAllMethodsMap(psiClass);
      final Runnable cleaner = new Runnable() {
        public void run() {
          psiClass.putUserData(METHODS_MAP_IN_CLASS_KEY, null);
        }
      };
      psiClass.putUserData(METHODS_MAP_IN_CLASS_KEY, new Pair<Map<String, List<MethodCandidateInfo>>, Runnable>(allMethods, cleaner));
      PsiManagerImpl manager = (PsiManagerImpl)psiClass.getManager();
      manager.registerWeakRunnableToRunOnChange(cleaner);
      return allMethods;
    }

    return value.first;
  }

  private static Map<String, List<MethodCandidateInfo>> _getAllMethodsMap(PsiClass psiClass) {
    final List<MethodCandidateInfo> list = new ArrayList<MethodCandidateInfo>();
    PsiScopesUtil.processScope(psiClass, new FilterScopeProcessor(new ClassFilter(PsiMethod.class), null, list){
      protected void add(PsiElement element, PsiSubstitutor substitutor) {
        list.add(new MethodCandidateInfo(element, substitutor));
      }
    }, PsiSubstitutor.EMPTY, null, psiClass);

    final Map<String, List<MethodCandidateInfo>> methods = new HashMap<String,List<MethodCandidateInfo>>();
    final Iterator<MethodCandidateInfo> iterator = list.iterator();
    while (iterator.hasNext()) {
      final MethodCandidateInfo info = iterator.next();
      final PsiMethod method = info.getElement();
      final String name = method.getName();
      List<MethodCandidateInfo> methodsList = methods.get(name);
      if(methodsList == null){
        methodsList = new ArrayList<MethodCandidateInfo>(1);
        methods.put(name, methodsList);
      }
      methodsList.add(info);
    }
    return methods;
  }

  public static Map<String, List<CandidateInfo>> getAllFieldsMap(final PsiClass psiClass){
    if (!psiClass.isPhysical()) {
      return _getAllFieldsMap(psiClass);
    }

    final Pair<Map<String, List<CandidateInfo>>, Runnable> value = psiClass.getUserData(FIELDS_MAP_IN_CLASS_KEY);

    if (value == null) {
      final Map<String, List<CandidateInfo>> allFields = _getAllFieldsMap(psiClass);
      final Runnable cleaner = new Runnable() {
        public void run() {
          psiClass.putUserData(FIELDS_MAP_IN_CLASS_KEY, null);
        }
      };
      psiClass.putUserData(FIELDS_MAP_IN_CLASS_KEY, new Pair<Map<String, List<CandidateInfo>>, Runnable>(allFields, cleaner));
      PsiManagerImpl manager = (PsiManagerImpl)psiClass.getManager();
      manager.registerWeakRunnableToRunOnChange(cleaner);
      return allFields;
    }

    return value.first;
  }

  private static Map<String, List<CandidateInfo>> _getAllFieldsMap(PsiClass psiClass) {
    final List<CandidateInfo> list = new ArrayList<CandidateInfo>();
    PsiScopesUtil.processScope(psiClass, new FilterScopeProcessor(new ClassFilter(PsiField.class), null, list){
      protected void add(PsiElement element, PsiSubstitutor substitutor) {
        list.add(new CandidateInfo(element, substitutor));
      }
    }, PsiSubstitutor.EMPTY, null, psiClass);

    final Map<String, List<CandidateInfo>> fields = new HashMap<String,List<CandidateInfo>>();
    final Iterator<CandidateInfo> iterator = list.iterator();
    while (iterator.hasNext()) {
      final CandidateInfo info = iterator.next();
      final PsiField field = (PsiField)info.getElement();
      final String name = field.getName();
      List<CandidateInfo> fieldsList = fields.get(name);
      if(fieldsList == null){
        fieldsList = new ArrayList<CandidateInfo>(1);
        fields.put(name, fieldsList);
      }
      fieldsList.add(info);
    }
    return fields;
  }


  public static PsiPointcutDef findPointcutDefBySignature(PsiAspect psiAspect, PsiPointcutDef patternPointcut,
                                                          boolean checkBases) {
    if (!checkBases) {
      PsiPointcutDef[] pointcuts = psiAspect.getPointcutDefs();
      for (int i = 0; i < pointcuts.length; i++) {
        if (MethodSignatureUtil.areSignaturesEqual(pointcuts[i], patternPointcut)) {
          return pointcuts[i];
        }
      }
      return null;
    }
    else {
      FindPointcutBySignatureProcessor processor = new FindPointcutBySignatureProcessor(patternPointcut);
      PsiScopesUtil.processScope(psiAspect, processor, PsiSubstitutor.UNKNOWN, null, patternPointcut);

      return processor.getResult();
    }
  }

  public static PsiAnnotationMemberValue findAttributeValue(PsiAnnotation annotation, String attributeName) {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    for (int i = 0; i < attributes.length; i++) {
      PsiNameValuePair attribute = attributes[i];
      if (attributeName.equals(attribute.getName())) return attribute.getValue();
    }

    PsiElement resolved = annotation.getNameReferenceElement().resolve();
    if (resolved != null) {
      PsiMethod[] methods = ((PsiClass)resolved).getMethods();
      for (int i = 0; i < methods.length; i++) {
        PsiMethod method = methods[i];
        if (method instanceof PsiAnnotationMethod && attributeName.equals(method.getName())) {
          return ((PsiAnnotationMethod)method).getDefaultValue();
        }
      }
    }
    return null;
  }

  private static class FindPointcutBySignatureProcessor extends BaseScopeProcessor implements NameHint,
                                                                                              ElementClassHint {
    private final String myName;
    private final PsiPointcutDef myPatternPointcut;
    private PsiPointcutDef myResult = null;

    public FindPointcutBySignatureProcessor(PsiPointcutDef patternPointcut) {
      myName = patternPointcut.getName();
      myPatternPointcut = patternPointcut;
    }

    public PsiPointcutDef getResult() {
      return myResult;
    }

    public String getName() {
      return myName;
    }

    public boolean shouldProcess(Class elementClass) {
      return PsiMethod.class.isAssignableFrom(elementClass);
    }

    public void handleEvent(Event event, Object associated) {
    }

    public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
      if (element instanceof PsiPointcutDef) {
        PsiPointcutDef method = (PsiPointcutDef)element;
        if (MethodSignatureUtil.areSignaturesEqual(method, myPatternPointcut)) {
          myResult = method;
          return false;
        }
      }
      return true;
    }
  }

  public static PsiJavaCodeReferenceElement[] namesToPackageReferences(PsiManager manager, String[] names) {
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[names.length];
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      try {
        refs[i] = manager.getElementFactory().createPackageReferenceElement(name);
      }
      catch (IncorrectOperationException e) {
        e.printStackTrace();
      }
    }
    return refs;
  }

  public static int getParameterIndex(PsiParameter parameter, PsiParameterList parameterList) {
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameter.equals(parameters[i])) return i;
    }
    LOG.assertTrue(false);
    return -1;
  }

  public static int getTypeParameterIndex(PsiTypeParameter typeParameter, PsiTypeParameterList typeParameterList) {
    PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      if (typeParameter.equals(typeParameters[i])) return i;
    }
    LOG.assertTrue(false);
    return -1;
  }

  public static final Object[] getReferenceVariantsByFilter(PsiJavaCodeReferenceElement reference,
                                                            ElementFilter filter) {
    FilterScopeProcessor processor = new FilterScopeProcessor(filter, reference);
    PsiScopesUtil.resolveAndWalk(processor, reference, null, true);
    return processor.getResults().toArray();
  }

  public static final MethodSignature getMethodSignature(PsiMethod method, PsiSubstitutor substitutor) {
    return new MethodSignatureBackedByPsiMethod(method, substitutor);
  }

  public static boolean processDeclarationsInMethod(PsiMethod method, PsiScopeProcessor processor, PsiSubstitutor substitutor,
                                                    PsiElement lastParent, PsiElement place) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, method);
    if (hint == null || hint.shouldProcess(PsiClass.class)) {
      final PsiTypeParameterList list = method.getTypeParameterList();
      if (list != null && !list.processDeclarations(processor, substitutor, null, place)) return false;
    }
    if (lastParent instanceof PsiCodeBlock) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        if (!processor.execute(parameters[i], substitutor)) return false;
      }
    }

    return true;
  }

  public static boolean hasTypeParameters(PsiTypeParameterListOwner psiMethod) {
    final PsiTypeParameterList typeParameterList = psiMethod.getTypeParameterList();
    return typeParameterList != null && typeParameterList.getTypeParameters().length != 0;
  }

  public static PsiType[] typesByReferenceParameterList(final PsiReferenceParameterList parameterList) {
    PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();

    return typesByTypeElements(typeElements);
  }

  public static PsiType[] typesByTypeElements(PsiTypeElement[] typeElements) {
    PsiType[] types = new PsiType[typeElements.length];
    for(int i = 0; i < types.length; i++){
      types[i] = typeElements[i].getType();
    }
    return types;
  }

  public static PsiType getType (PsiClassObjectAccessExpression classAccessExpression) {
    GlobalSearchScope resolveScope = classAccessExpression.getResolveScope();
    PsiManager manager = classAccessExpression.getManager();
    final PsiClass classClass = manager.findClass("java.lang.Class", resolveScope);
    if (classClass == null){
      return new PsiClassReferenceType(new LightClassReference(manager, "Class", "java.lang.Class", resolveScope));
    }
    else {
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      PsiType operandType = classAccessExpression.getOperand().getType();
      if (operandType instanceof PsiPrimitiveType && !PsiType.NULL.equals(operandType)) {
        if (PsiType.VOID.equals(operandType)) {
          operandType = manager.getElementFactory().createTypeByFQClassName("java.lang.Void", classAccessExpression.getResolveScope());
        } else {
          operandType = ((PsiPrimitiveType)operandType).getBoxedType(manager, classAccessExpression.getResolveScope());
        }
      }
      final PsiTypeParameterList typeParameterList = classClass.getTypeParameterList();
      if (typeParameterList != null) {
        final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
        if (typeParameters.length == 1) {
          substitutor = substitutor.put(typeParameters[0], operandType);
        }
      }

      return new PsiImmediateClassType(classClass, substitutor);
    }
  }

  public static PsiAnnotation findAnnotation(PsiModifierList modifierList, String qualifiedName) {
    PsiAnnotation[] annotations = modifierList.getAnnotations();
    for (int i = 0; i < annotations.length; i++) {
      PsiAnnotation annotation = annotations[i];
      final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef != null && qualifiedName.equals(nameRef.getCanonicalText())) return annotation;
    }

    return null;
  }

public static PsiType normalizeWildcardTypeByPosition(final PsiType type, final PsiExpression expression) {
  if (type instanceof PsiCapturedWildcardType) {
    return normalizeWildcardTypeByPosition(((PsiCapturedWildcardType)type).getWildcard(), expression);
  }
  if (type instanceof PsiWildcardType) {
    final PsiWildcardType wildcardType = (PsiWildcardType)type;
    if (PsiUtil.isInCovariantPosition(expression)) {
      return wildcardType.isSuper() ? wildcardType.getBound() : PsiCapturedWildcardType.create(wildcardType);
    }
    else {
      if (wildcardType.isExtends()) {
        return wildcardType.getBound();
      }
      else {
        return PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
      }
    }
  }
  else if (type instanceof PsiArrayType) {
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    final PsiType normalizedComponentType = normalizeWildcardTypeByPosition(componentType, expression);
    if (normalizedComponentType != componentType) {
      return normalizedComponentType.createArrayType();
    }
    else {
      return type;
    }
  }
  else {
    return type;
  }
}
}
