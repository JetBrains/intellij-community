package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.10.2003
 * Time: 16:50:37
 * To change this template use Options | File Templates.
 */
public class PsiClassImplUtil{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiClassImplUtil");
  private static final Key MAP_IN_CLASS_KEY = Key.create("MAP_IN_CLASS_KEY");
  private static final Key ALL_MAPS_BUILT_FLAG = Key.create("ALL_MAPS_BUILT_FLAG");

  public static PsiField[] getAllFields(final PsiClass aClass) {
    return getAllByMap(aClass, PsiField.class);
  }

  public static PsiMethod[] getAllMethods(final PsiClass aClass) {
    return getAllByMap(aClass, PsiMethod.class);
  }

  public static PsiClass[] getAllInnerClasses(PsiClass aClass) {
    return getAllByMap(aClass, PsiClass.class);
  }

  public static PsiField findFieldByName(PsiClass aClass, String name, boolean checkBases) {
    final PsiField[] byMap = findByMap(aClass, name, checkBases, PsiField.class);
    return byMap.length >= 1 ? byMap[0] : null;
  }

  public static PsiMethod[] findMethodsByName(PsiClass aClass, String name, boolean checkBases) {
    return findByMap(aClass, name, checkBases, PsiMethod.class);
  }

  public static PsiMethod findMethodBySignature(final PsiClass aClass, final PsiMethod patternMethod, final boolean checkBases) {
    final PsiMethod[] result = findMethodsBySignature(aClass, patternMethod, checkBases, true);
    return (result.length != 0 ? result [0] : null);
  }

  // ----------------------------- findMethodsBySignature -----------------------------------

  public static PsiMethod[] findMethodsBySignature(final PsiClass aClass, final PsiMethod patternMethod, final boolean checkBases) {
    return findMethodsBySignature(aClass, patternMethod, checkBases, false);
  }

  private static PsiMethod[] findMethodsBySignature(final PsiClass aClass, final PsiMethod patternMethod, final boolean checkBases, final boolean stopOnFirst) {
    final PsiMethod[] methodsByName = aClass.findMethodsByName(patternMethod.getName(), checkBases);
    final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (int i = 0; i < methodsByName.length; i++) {
      final PsiMethod method = methodsByName[i];
      final PsiClass superClass = method.getContainingClass();
      final PsiSubstitutor substitutor;
      if (checkBases && !aClass.equals(superClass)) {
        substitutor =  TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
        LOG.assertTrue(substitutor != null);
      }
      else {
        substitutor = PsiSubstitutor.EMPTY;
      }
      final MethodSignature signature = method.getSignature(substitutor);
      if (signature.equals(patternSignature)) {
        methods.add(method);
        if (stopOnFirst)
          break;
      }
    }
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  // ----------------------------------------------------------------------------------------

  public static PsiClass findInnerByName(PsiClass aClass, String name, boolean checkBases) {
    final PsiClass[] byMap = findByMap(aClass, name, checkBases, PsiClass.class);
    return byMap.length >= 1 ? byMap[0] : null;
  }

  private static final<T> T[] emptyArrayByType(Class<T> type){
    if(type.isAssignableFrom(PsiMethod.class)) return (T[])PsiMethod.EMPTY_ARRAY;
    if(type.isAssignableFrom(PsiField.class)) return (T[])PsiField.EMPTY_ARRAY;
    if(type.isAssignableFrom(PsiClass.class)) return (T[])PsiClass.EMPTY_ARRAY;

    return (T[])ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private static <T extends PsiNamedElement> T[] findByMap(PsiClass aClass, String name, boolean checkBases, Class<T> type) {
    if(name == null) return emptyArrayByType(type);

    if (!checkBases) {
      Object[] members = null;
      if(type.isAssignableFrom(PsiMethod.class)){
        members = aClass.getMethods();
      }
      else if(type.isAssignableFrom(PsiClass.class)){
        members = aClass.getInnerClasses();
      }
      else if(type.isAssignableFrom(PsiField.class)){
        members = aClass.getFields();
      }
      if(members == null) return emptyArrayByType(type);

      List<T> list = new ArrayList<T>();
      for (int i = 0; i < members.length; i++) {
        final T method = (T)members[i];
        if(name.equals(method.getName())) list.add(method);
      }
      return list.toArray(emptyArrayByType(type));
    }
    else {
      final Map<String, List<Pair<T, PsiSubstitutor>>> allMethodsMap = getMap(aClass, type);
      final List<Pair<T, PsiSubstitutor>> list = allMethodsMap.get(name);
      if(list == null) return emptyArrayByType(type);
      final List<T> ret = new ArrayList<T>();
      final Iterator<Pair<T, PsiSubstitutor>> iterator = list.iterator();
      while (iterator.hasNext()) {
        final Pair<T, PsiSubstitutor> info = iterator.next();
        ret.add(info.getFirst());
      }

      return ret.toArray(emptyArrayByType(type));
    }
  }

  public static <T extends PsiNamedElement> List<Pair<T, PsiSubstitutor>> getAllWithSubstitutorsByMap(PsiClass aClass, Class<T> type) {
    final Map<String, List<Pair<T, PsiSubstitutor>>> allMap = getMap(aClass, type);
    final List<Pair<T, PsiSubstitutor>> pairs = allMap.get(ALL);
    return pairs;
  }

  private static <T extends PsiNamedElement> T[] getAllByMap(PsiClass aClass, Class<T> type) {
    final Map<String, List<Pair<T, PsiSubstitutor>>> allMap = getMap(aClass, type);
    final List<Pair<T, PsiSubstitutor>> pairs = allMap.get(ALL);

    if (pairs == null) {
      LOG.error("pairs should be already computed. Wrong allMap: " + allMap);
    }

    final List<T> ret = new ArrayList<T>(pairs.size());
    final Iterator<Pair<T,PsiSubstitutor>> iterator = pairs.iterator();
    while (iterator.hasNext()) {
      final Pair<T,PsiSubstitutor> pair = iterator.next();
      ret.add(pair.getFirst());
    }
    return ret.toArray(emptyArrayByType(type));
  }

  private static final String NULL = "Intellij-IDEA-NULL";
  private static final String ALL = "Intellij-IDEA-ALL";

  private static <T extends PsiNamedElement> void buildMap(PsiClass psiClass,
                                                           Map<String, List<Pair<T, PsiSubstitutor>>> map,
                                                           final Class<T> type) {
    final Object data;
    synchronized (PsiLock.LOCK) {
      data = psiClass.getUserData(ALL_MAPS_BUILT_FLAG);
    }
    if (data != null) return;
    if (map.containsKey(NULL)) return;

    final List<Pair<T, PsiSubstitutor>> list = new ArrayList<Pair<T, PsiSubstitutor>>();
    processDeclarationsInClassNotCached(psiClass, new FilterScopeProcessor(new ClassFilter(type), null, list) {
      protected void add(PsiElement element, PsiSubstitutor substitutor) {
        list.add(new Pair<T, PsiSubstitutor>((T)element, substitutor));
      }
    }, PsiSubstitutor.EMPTY, new HashSet(), null, psiClass);


    synchronized (map) {
      map.put(ALL, list);
      final Iterator<Pair<T, PsiSubstitutor>> iterator = list.iterator();
      while (iterator.hasNext()) {
        final Pair<T, PsiSubstitutor> info = iterator.next();
        final T element = info.getFirst();
        final String currentName = element.getName();
        List<Pair<T, PsiSubstitutor>> elementsList = map.get(currentName);
        if (elementsList == null) {
          elementsList = new ArrayList<Pair<T, PsiSubstitutor>>(1);
          map.put(currentName, elementsList);
        }
        elementsList.add(info);
      }
      map.put(NULL, null);
    }
  }

  private static <T extends PsiNamedElement> Map<String, List<Pair<T, PsiSubstitutor>>> buildAllMaps(final PsiClass psiClass, Class<T> type) {
    if (!psiClass.isPhysical()) return getMapInner(psiClass, type);
    final Object data;
    synchronized (PsiLock.LOCK) {
      data = psiClass.getUserData(ALL_MAPS_BUILT_FLAG);
    }

    if (data != null) {
      final Map<String, List<Pair<T, PsiSubstitutor>>> mapInner = getMapInner(psiClass, type);
      if (mapInner.isEmpty()) {
        LOG.assertTrue(false);
      }
      return mapInner;
    }

    final List<Pair<PsiClass, PsiSubstitutor>> classes = new ArrayList<Pair<PsiClass, PsiSubstitutor>>();
    final List<Pair<PsiField, PsiSubstitutor>> fields = new ArrayList<Pair<PsiField, PsiSubstitutor>>();
    final List<Pair<PsiMethod, PsiSubstitutor>> methods = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();


    final List<MethodCandidateInfo> list = new ArrayList<MethodCandidateInfo>();
    processDeclarationsInClassNotCached(psiClass, new FilterScopeProcessor(new OrFilter(new ElementFilter[]{
      new ClassFilter(PsiMethod.class),
      new ClassFilter(PsiField.class),
      new ClassFilter(PsiClass.class)
    }), null, list) {
      protected void add(PsiElement element, PsiSubstitutor substitutor) {
        if (element instanceof PsiMethod) {
          methods.add(new Pair<PsiMethod, PsiSubstitutor>((PsiMethod)element, substitutor));
        }
        else if (element instanceof PsiField) {
          fields.add(new Pair<PsiField, PsiSubstitutor>((PsiField)element, substitutor));
        }
        else if (element instanceof PsiClass) {
          classes.add(new Pair<PsiClass, PsiSubstitutor>((PsiClass)element, substitutor));
        }
      }
    }, PsiSubstitutor.EMPTY, new HashSet(), null, psiClass);

    synchronized (PsiLock.LOCK) {
      //This is quite a critical doubled check. Actually, the first check might be removed
//But at least one check should be performed inside the non-cancelable action
//In order not to put uncomplete data to map
      if (psiClass.getUserData(ALL_MAPS_BUILT_FLAG) != null) {
        final Map<String, List<Pair<T, PsiSubstitutor>>> mapInner = getMapInner(psiClass, type);
        if (mapInner.isEmpty()) {
          LOG.assertTrue(false);
        }
        return mapInner;
      }
      final Map<String, List<Pair<PsiClass, PsiSubstitutor>>> classesMap = getMapInner(psiClass, PsiClass.class);
      final Map<String, List<Pair<PsiField, PsiSubstitutor>>> fieldsMap = getMapInner(psiClass, PsiField.class);
      final Map<String, List<Pair<PsiMethod, PsiSubstitutor>>> methodsMap = getMapInner(psiClass, PsiMethod.class);
      if (classesMap.isEmpty()) {
        generateMapByList(classesMap, classes);
      }
      ;
      if (fieldsMap.isEmpty()) {
        generateMapByList(fieldsMap, fields);
      }
      ;
      if (methodsMap.isEmpty()) {
        generateMapByList(methodsMap, methods);
      }
      ;
      psiClass.putUserData(ALL_MAPS_BUILT_FLAG, "");
      return getMapInner(psiClass, type);
    }
  }

  private static <T extends PsiNamedElement> void generateMapByList(final Map<String, List<Pair<T, PsiSubstitutor>>> map,
                                            final List<Pair<T, PsiSubstitutor>> list) {
    LOG.assertTrue(list != null);
    synchronized(map){
      map.put(ALL, list);
      final Iterator<Pair<T, PsiSubstitutor>> iterator = list.iterator();
      while (iterator.hasNext()) {
        final Pair<T, PsiSubstitutor> info = iterator.next();
        final T element = info.getFirst();
        final String currentName = element.getName();
        List<Pair<T, PsiSubstitutor>> listByName = map.get(currentName);
        if(listByName == null){
          listByName = new ArrayList<Pair<T, PsiSubstitutor>>(1);
          map.put(currentName, listByName);
        }
        listByName.add(info);
      }
    }
  }

  private static <T extends PsiNamedElement> Map<String, List<Pair<T, PsiSubstitutor>>> getMap(final PsiClass psiClass, final Class<T> type){
    return buildAllMaps(psiClass, type);
  }

  private static <T extends PsiNamedElement> Map<String, List<Pair<T, PsiSubstitutor>>> getMapInner(final PsiClass psiClass, final Class<T> type) {
    if (!psiClass.isPhysical()) {
      final Map<String, List<Pair<T, PsiSubstitutor>>> hashMap = new HashMap<String, List<Pair<T, PsiSubstitutor>>>();
      buildMap(psiClass, hashMap, type);
      return hashMap;
    }
    final Map<String, List<Pair<T, PsiSubstitutor>>> map;
    synchronized (PsiLock.LOCK) {
      Map<String, Pair<Map<String, List<Pair<T, PsiSubstitutor>>>, Runnable>> mapInClass = (Map<String, Pair<Map<String, List<Pair<T, PsiSubstitutor>>>, Runnable>>)psiClass.getUserData(MAP_IN_CLASS_KEY);
      if (mapInClass == null) {
        mapInClass = new HashMap<String, Pair<Map<String, List<Pair<T, PsiSubstitutor>>>, Runnable>>();
        psiClass.putUserData(MAP_IN_CLASS_KEY, mapInClass);
      }
      final String typeName = type.getName();
      final Pair<Map<String, List<Pair<T, PsiSubstitutor>>>, Runnable> value = mapInClass.get(typeName);
      if (value == null) {
        map = Collections.synchronizedMap(new HashMap<String, List<Pair<T, PsiSubstitutor>>>());
        final Map<String, Pair<Map<String, List<Pair<T, PsiSubstitutor>>>, Runnable>> mapInClass1 = mapInClass;
        final Runnable cleaner = new Runnable() {
          public void run() {
            synchronized (PsiLock.LOCK) {
              psiClass.putUserData(ALL_MAPS_BUILT_FLAG, null);
              mapInClass1.remove(typeName);
            }
          }
        };
        mapInClass.put(typeName, new Pair<Map<String, List<Pair<T, PsiSubstitutor>>>, Runnable>(map, cleaner));
        PsiManagerImpl manager = (PsiManagerImpl)psiClass.getManager();
        manager.registerWeakRunnableToRunOnChange(cleaner);
      }
      else {
        map = value.first;
      }
      ;
    }
    return map;
  }

  public static boolean processDeclarationsInClass(PsiClass aClass, PsiScopeProcessor processor,
                                                   PsiSubstitutor substitutor, Set visited, PsiElement last,
                                                   PsiElement place) {
    if (visited.contains(aClass)) return true;
    if (last instanceof PsiTypeParameterList) return true; //TypeParameterList doesn't see our declarations
    final Object data;
    synchronized (PsiLock.LOCK) {
      data = aClass.getUserData(ALL_MAPS_BUILT_FLAG);
    }
    if (last instanceof PsiReferenceList && data == null || aClass instanceof PsiTypeParameter) {
      return processDeclarationsInClassNotCached(aClass, processor, substitutor, visited, last, place);
    }

    final NameHint nameHint = processor.getHint(NameHint.class);
    final ElementClassHint classHint = processor.getHint(ElementClassHint.class);

    if (nameHint != null) {
      if (classHint == null || classHint.shouldProcess(PsiField.class)) {
        final PsiField fieldByName = aClass.findFieldByName(nameHint.getName(), false);
        if (fieldByName != null) {
          processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
          if (!processor.execute(fieldByName, substitutor)) return false;
        }
        else {
          final Map<String, List<Pair<PsiField, PsiSubstitutor>>> allFieldsMap = getMap(aClass, PsiField.class);

          final List<Pair<PsiField, PsiSubstitutor>> list = allFieldsMap.get(nameHint.getName());
          if (list != null) {
            final Iterator<Pair<PsiField, PsiSubstitutor>> iterator = list.iterator();
            while (iterator.hasNext()) {
              final Pair<PsiField, PsiSubstitutor> candidate = iterator.next();
              PsiField candidateField = candidate.getFirst();
              PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(candidateField.getContainingClass(), candidate.getSecond(), aClass,
                                                                       substitutor);

              processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, candidateField.getContainingClass());
              if (!processor.execute(candidateField, finalSubstitutor)) return false;
            }
          }
        }
      }
      if (classHint == null || classHint.shouldProcess(PsiClass.class)) {
        if (last != null && last.getParent() == aClass) {
          // Parameters
          final PsiTypeParameterList list = aClass.getTypeParameterList();
          if (list != null && !PsiScopesUtil.processScope(list, processor, substitutor, last, place)) return false;
        }
        if (!(last instanceof PsiReferenceList) && !(last instanceof PsiModifierList)) {
          final PsiClass classByName = aClass.findInnerClassByName(nameHint.getName(), false);
          if (classByName != null) {
            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
            if (!processor.execute(classByName, substitutor)) return false;
          }
          else {
            final Map<String, List<Pair<PsiClass, PsiSubstitutor>>> allClassesMap = getMap(aClass, PsiClass.class);

            final List<Pair<PsiClass, PsiSubstitutor>> list = allClassesMap.get(nameHint.getName());
            if (list != null) {
              final Iterator<Pair<PsiClass, PsiSubstitutor>> iterator = list.iterator();
              while (iterator.hasNext()) {
                final Pair<PsiClass, PsiSubstitutor> candidate = iterator.next();
                final PsiElement psiClass = candidate.getFirst().getParent();
                if (psiClass instanceof PsiClass) {
                  PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(((PsiClass)psiClass), candidate.getSecond(), aClass,
                                                                           substitutor);
                  processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, psiClass);
                  if (!processor.execute(candidate.getFirst(), finalSubstitutor)) return false;
                }
              }
            }
          }
        }
      }
      if (classHint == null || classHint.shouldProcess(PsiMethod.class)) {
        if (processor instanceof MethodResolverProcessor) {
          final MethodResolverProcessor methodResolverProcessor = (MethodResolverProcessor)processor;
          if (methodResolverProcessor.isConstructor()) {
            final PsiMethod[] constructors = aClass.getConstructors();
            methodResolverProcessor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
            for (int i = 0; i < constructors.length; i++) {
              if (!methodResolverProcessor.execute(constructors[i], substitutor)) return false;
            }
            return true;
          }
        }
        final Map<String, List<Pair<PsiMethod, PsiSubstitutor>>> allMethodsMap = getMap(aClass, PsiMethod.class);
        final List<Pair<PsiMethod, PsiSubstitutor>> list = allMethodsMap.get(nameHint.getName());
        if (list != null) {
          final Iterator<Pair<PsiMethod, PsiSubstitutor>> iterator = list.iterator();
          while (iterator.hasNext()) {
            final Pair<PsiMethod, PsiSubstitutor> candidate = iterator.next();
            PsiMethod candidateMethod = candidate.getFirst();
            PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(candidateMethod.getContainingClass(), candidate.getSecond(), aClass,
                                                                     substitutor);
            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, candidate.getFirst().getContainingClass());
            if (!processor.execute(candidate.getFirst(), finalSubstitutor)) return false;
          }
        }
      }
      return true;
    }

    return processDeclarationsInClassNotCached(aClass, processor, substitutor, visited, last, place);
  }

  private static PsiSubstitutor obtainFinalSubstitutor(PsiClass candidateClass, PsiSubstitutor candidateSubstitutor, PsiClass aClass,
                                                       PsiSubstitutor substitutor) {
    PsiElementFactory elementFactory = candidateClass.getManager().getElementFactory();
    if (PsiUtil.isRawSubstitutorForClass(aClass, substitutor)) {
      return candidateSubstitutor.merge(elementFactory.createRawSubstitutor(candidateClass));
    }

    final PsiType containingType = elementFactory.createType(candidateClass, candidateSubstitutor);
    PsiType type = substitutor.substitute(containingType);
    if (!(type instanceof PsiClassType)) return candidateSubstitutor;
    PsiSubstitutor finalSubstitutor = ((PsiClassType)type).resolveGenerics().getSubstitutor();
    return finalSubstitutor;
  }

  public static boolean processDeclarationsInClassNotCached(PsiClass aClass, PsiScopeProcessor processor,
                                                   PsiSubstitutor substitutor, Set visited, PsiElement last,
                                                   PsiElement place) {
    if (visited.contains(aClass)) return true;
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
    final ElementClassHint classHint = processor.getHint(ElementClassHint.class);
    final NameHint nameHint = processor.getHint(NameHint.class);


    PsiManager manager = aClass.getManager();
    if (classHint == null || classHint.shouldProcess(PsiField.class)) {
      if (nameHint != null) {
        final PsiField fieldByName = aClass.findFieldByName(nameHint.getName(), false);
        if(fieldByName != null)
          if(!processor.execute(fieldByName, substitutor)) return false;
      }
      else {
        final PsiField[] fields = aClass.getFields();
        for (int i = 0; i < fields.length; i++) {
          final PsiField field = fields[i];
          if (!processor.execute(field, substitutor)) return false;
        }
      }

      final PsiField[] introducedFields = manager.getAspectManager().getIntroducedFields(aClass);
      for (int i = 0; i < introducedFields.length; i++) {
        if (!processor.execute(introducedFields[i], substitutor)) return false;
      }
    }

    if (classHint == null || classHint.shouldProcess(PsiMethod.class)) {
      if (nameHint != null) {
        final PsiMethod[] methods = aClass.findMethodsByName(nameHint.getName(), false);
        for (int i = 0; methods != null && i < methods.length; i++) {
          final PsiMethod method = methods[i];
          if (!processor.execute(method, substitutor)) return false;
        }
      }
      else {
        final PsiMethod[] methods = aClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
          final PsiMethod method = methods[i];
          if (!processor.execute(method, substitutor)) return false;
        }
      }

      PsiMethod[] introducedMethods = manager.getAspectManager().getIntroducedMethods(aClass);
      for (int i = 0; i < introducedMethods.length; i++) {
        if (!processor.execute(introducedMethods[i], substitutor)) return false;
      }
    }

    if (classHint == null || classHint.shouldProcess(PsiClass.class)) {
      if (last != null && last.getParent() == aClass) {
        // Parameters
        final PsiTypeParameterList list = aClass.getTypeParameterList();
        if (list != null && !PsiScopesUtil.processScope(list, processor, PsiSubstitutor.EMPTY, last, place)) return false;
      }

      if (!(last instanceof PsiReferenceList) && !(last instanceof PsiModifierList)) {
        // Inners
        if(nameHint != null){
          final PsiClass inner = aClass.findInnerClassByName(nameHint.getName(), false);
          if(inner != null)
            if(!processor.execute(inner, substitutor)) return false;
        }
        else{
          final PsiClass[] inners = aClass.getInnerClasses();
          for (int i = 0; i < inners.length; i++) {
            final PsiClass inner = inners[i];
            if (!processor.execute(inner, substitutor)) return false;
          }
        }
      }
    }

    visited.add(aClass);
    if (!(last instanceof PsiReferenceList)) {
      if (!processSuperTypes(
            aClass.getSuperTypes(),
            processor, visited, last, place, aClass, substitutor)) {
          return false;
      }
    }
    return true;
  }

  private static boolean processSuperTypes(final PsiClassType[] superTypes, PsiScopeProcessor processor,
                                           Set visited, PsiElement last, PsiElement place, PsiClass aClass, PsiSubstitutor substitutor) {
    if (superTypes == null) return true;
    for (int i = 0; i < superTypes.length; i++) {
      final PsiClassType superType = superTypes[i];
      if(superType == null) continue;
      final PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass, substitutor);
      if (!processDeclarationsInClass(superClass, processor, finalSubstitutor, visited, last, place)) {
        return false;
      }
    }
    return true;
  }

  public static PsiClass getSuperClass(PsiClass psiClass) {
    PsiManager manager = psiClass.getManager();
    GlobalSearchScope resolveScope = psiClass.getResolveScope();

    if (psiClass.isInterface()) {
      return manager.findClass("java.lang.Object", resolveScope);
    }
    if (psiClass.isEnum()) {
      return manager.findClass("java.lang.Enum", resolveScope);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass == null || baseClass.isInterface()) return manager.findClass("java.lang.Object", resolveScope);
      return baseClass;
    }

    if ("java.lang.Object".equals(psiClass.getQualifiedName())) return null;

    PsiReferenceList[] introducedExtendsList = new PsiReferenceList[1];
    PsiReferenceList[] introducedImplementsList = new PsiReferenceList[1];

    manager.getAspectManager().getIntroducedParents(psiClass, introducedExtendsList,
                                                                  introducedImplementsList);
    final PsiClassType[] referenceElements;
    if (introducedExtendsList[0] != null) {
      referenceElements = introducedExtendsList[0].getReferencedTypes();
    }
    else {
      referenceElements = psiClass.getExtendsListTypes();
    }

    if (referenceElements.length == 0) return manager.findClass("java.lang.Object", resolveScope);

    PsiClass psiResoved = referenceElements[0].resolve();
    return psiResoved == null ? manager.findClass("java.lang.Object", resolveScope) : psiResoved;
  }

  public static PsiClass[] getSupers(PsiClass psiClass) {
    final PsiClass[] supers = getSupersInner(psiClass);
    for (int i = 0; i < supers.length; i++) {
      final PsiClass aSuper = supers[i];
      LOG.assertTrue(aSuper != null);///
    }
    return supers;
  }

  private static PsiClass[] getSupersInner(PsiClass psiClass) {
    PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
    PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();

    PsiReferenceList[] introducedExtendsList = new PsiReferenceList[1];
    PsiReferenceList[] introducedImplementsList = new PsiReferenceList[1];

    psiClass.getManager().getAspectManager().getIntroducedParents(psiClass, introducedExtendsList,
                                                                  introducedImplementsList);
    if (introducedExtendsList[0] != null) {
      extendsListTypes = introducedExtendsList[0].getReferencedTypes();
    }
    if (introducedImplementsList[0] != null) {
      implementsListTypes = introducedImplementsList[0].getReferencedTypes();
    }

    if (psiClass.isInterface()) {
      return resolveClassReferenceList(extendsListTypes,
                                       psiClass.getManager(), psiClass.getResolveScope(), true);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass)psiClass;
      PsiClassType baseClassReference = psiAnonymousClass.getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass != null) {
        if (baseClass.isInterface()) {
          PsiClass objectClass = psiClass.getManager().findClass("java.lang.Object", psiClass.getResolveScope());
          return objectClass != null ? new PsiClass[]{objectClass, baseClass} : new PsiClass[]{baseClass};
        }
        return new PsiClass[]{baseClass};
      }

      PsiClass objectClass = psiClass.getManager().findClass("java.lang.Object", psiClass.getResolveScope());
      return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
    }
    else if (psiClass instanceof PsiTypeParameter) {
      PsiClassType[] referencedTypes = extendsListTypes;
      if (referencedTypes.length == 0) {
        final PsiClass objectClass = psiClass.getManager().findClass("java.lang.Object", psiClass.getResolveScope());
        return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
      }
      return resolveClassReferenceList(referencedTypes, psiClass.getManager(),
                                       psiClass.getResolveScope(), false);
    }

    PsiClass[] interfaces = resolveClassReferenceList(implementsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);

    PsiClass superClass = getSuperClass(psiClass);
    if (superClass == null) return interfaces;
    PsiClass[] types = new PsiClass[interfaces.length + 1];
    types[0] = superClass;
    System.arraycopy(interfaces, 0, types, 1, interfaces.length);

    return types;
  }

  public static PsiClassType[] getSuperTypes(PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null || !baseClass.isInterface()) {
        return new PsiClassType[]{baseClassType};
      }
      else {
        PsiClass objectClass = psiClass.getManager().findClass("java.lang.Object", psiClass.getResolveScope());
        if (objectClass != null) {
          return new PsiClassType[]{new PsiImmediateClassType(objectClass, PsiSubstitutor.EMPTY), baseClassType};
        }
        else {
          return new PsiClassType[]{baseClassType};
        }
      }
    }

    List<PsiClassType> result = new ArrayList<PsiClassType>();

    PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
    result.addAll(Arrays.asList(extendsTypes));
    boolean noExtends = extendsTypes.length == 0;

    result.addAll(Arrays.asList(psiClass.getImplementsListTypes()));

    final PsiReferenceList[] extendsListOut = new PsiReferenceList[1];
    final PsiReferenceList[] implementsListOut = new PsiReferenceList[1];
    psiClass.getManager().getAspectManager().getIntroducedParents(psiClass, extendsListOut,
                                                                  implementsListOut);
    noExtends = noExtends && extendsListOut[0] == null;
    addReferenceTypes(extendsListOut[0], result);
    addReferenceTypes(implementsListOut[0], result);


    if (noExtends) {
      PsiManager manager = psiClass.getManager();
      PsiClass objectClass = manager.findClass("java.lang.Object", psiClass.getResolveScope());
      if (objectClass != null && !manager.areElementsEquivalent(psiClass, objectClass)) {
        result.add(0, manager.getElementFactory().createType(objectClass));
      }
    }

    return result.toArray(new PsiClassType[result.size()]);
  }

  private static PsiClassType getAnnotationSuperType(PsiClass psiClass) {
    return psiClass.getManager().getElementFactory().createTypeByFQClassName("java.lang.annotation.Annotation", psiClass.getResolveScope());
  }

  private static PsiClassType getEnumSuperType(PsiClass psiClass) {
    PsiClassType superType;
    final PsiManager manager = psiClass.getManager();
    final PsiClass enumClass = manager.findClass("java.lang.Enum", psiClass.getResolveScope());
    if (enumClass == null) {
      try {
        superType = (PsiClassType)manager.getElementFactory().createTypeFromText("java.lang.Enum", null);
      }
      catch (IncorrectOperationException e) {
        superType = null;
      }
    }
    else {
      final PsiTypeParameter[] typeParameters = enumClass.getTypeParameters();
      if (typeParameters.length != 1) {
        superType = new PsiImmediateClassType(enumClass, PsiSubstitutor.EMPTY);
      }
      else {
        superType = new PsiImmediateClassType(enumClass, PsiSubstitutor.EMPTY.put(
                              typeParameters[0], manager.getElementFactory().createType(psiClass)
                            ));
      }
    }
    return superType;
  }

  private static void addReferenceTypes(final PsiReferenceList referenceList, List<PsiClassType> result) {
    if (referenceList != null) {
      final PsiClassType[] referenceElements = referenceList.getReferencedTypes();
      for (int i = 0; i < referenceElements.length; i++) {
        final PsiClassType referenceElement = referenceElements[i];
        LOG.assertTrue(referenceElement != null);
        result.add(referenceElement);
      }
    }
  }

  public static PsiClass[] getInterfaces(PsiTypeParameter typeParameter) {
    final ArrayList<PsiClass> result = new ArrayList<PsiClass>();
    final PsiClassType[] referencedTypes = typeParameter.getExtendsListTypes();
    for (int i = 0; i < referencedTypes.length; i++) {
      PsiClassType referencedType = referencedTypes[i];
      final PsiClass psiClass = referencedType.resolve();
      if (psiClass != null && psiClass.isInterface()) {
        result.add(psiClass);
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  public static PsiClass[] getInterfaces(PsiClass psiClass) {
    PsiReferenceList[] introducedExtendsList = new PsiReferenceList[1];
    PsiReferenceList[] introducedImplementsList = new PsiReferenceList[1];

    psiClass.getManager().getAspectManager().getIntroducedParents(psiClass, introducedExtendsList,
                                                                  introducedImplementsList);
    final PsiClassType[] extendsListTypes;
    if (introducedExtendsList[0] == null) {
      extendsListTypes = psiClass.getExtendsListTypes();
    }
    else {
      extendsListTypes = introducedExtendsList[0].getReferencedTypes();
    }
    final PsiClassType[] implementsListTypes;
    if (introducedImplementsList[0] == null) {
      implementsListTypes = psiClass.getImplementsListTypes();
    }
    else {
      implementsListTypes = introducedImplementsList[0].getReferencedTypes();
    }


    if (psiClass.isInterface()) {
      return resolveClassReferenceList(extendsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass != null && baseClass.isInterface()) return new PsiClass[]{baseClass};
      return PsiClass.EMPTY_ARRAY;
    }

    return resolveClassReferenceList(implementsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);
  }

  private static PsiClass[] resolveClassReferenceList(final PsiClassType[] listOfTypes,
                                                      final PsiManager manager, final GlobalSearchScope resolveScope, boolean includeObject) {
    PsiClass objectClass = manager.findClass("java.lang.Object", resolveScope);
    if (objectClass == null) includeObject = false;
    if (listOfTypes == null || listOfTypes.length == 0) {
      if (includeObject) return new PsiClass[]{objectClass};
      return PsiClass.EMPTY_ARRAY;
    }

    int referenceCount = listOfTypes.length;
    if (includeObject) referenceCount++;

    PsiClass[] resolved = new PsiClass[referenceCount];
    int resolvedCount = 0;

    if (includeObject) resolved[resolvedCount++] = objectClass;
    for (int i = 0; i < listOfTypes.length; i++) {
      PsiClassType reference = listOfTypes[i];
      PsiClass refResolved = reference.resolve();
      if (refResolved != null) resolved[resolvedCount++] = refResolved;
    }

    if (resolvedCount < referenceCount) {
      PsiClass[] shorter = new PsiClass[resolvedCount];
      System.arraycopy(resolved, 0, shorter, 0, resolvedCount);
      resolved = shorter;
    }

    return resolved;
  }

  public static List<Pair<PsiMethod,PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(PsiClass psiClass, String name, boolean checkBases) {
    if(!checkBases){
      final PsiMethod[] methodsByName = psiClass.findMethodsByName(name, false);
      final List<Pair<PsiMethod,PsiSubstitutor>> ret = new ArrayList<Pair<PsiMethod,PsiSubstitutor>>(methodsByName.length);
      for (int i = 0; i < methodsByName.length; i++) {
        final PsiMethod method = methodsByName[i];
        ret.add(new Pair<PsiMethod,PsiSubstitutor>(method, PsiSubstitutor.EMPTY));
      }
      return ret;
    }
    final Map<String,List<Pair<PsiMethod,PsiSubstitutor>>> map = getMap(psiClass, PsiMethod.class);
    final List<Pair<PsiMethod,PsiSubstitutor>> list = map.get(name);
    return list == null ? Collections.EMPTY_LIST : Collections.unmodifiableList(list);
  }

  public static PsiTypeParameter[] getTypeParameters(PsiClass psiClass) {
    final PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
    if (typeParameterList != null) {
      return typeParameterList.getTypeParameters();
    }
    else {
      return PsiTypeParameter.EMPTY_ARRAY;
    }
  }

  public static PsiClassType[] getExtendsListTypes(PsiClass psiClass) {
    if (psiClass.isEnum()) {
      return new PsiClassType[]{getEnumSuperType(psiClass)};
    } else if (psiClass.isAnnotationType()) {
      return new PsiClassType[]{getAnnotationSuperType(psiClass)};
    }
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  public static PsiClassType[] getImplementsListTypes(PsiClass psiClass) {
    final PsiReferenceList extendsList = psiClass.getImplementsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }
}
