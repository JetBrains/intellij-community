package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
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
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.10.2003
 * Time: 16:50:37
 * To change this template use Options | File Templates.
 */
public class PsiClassImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiClassImplUtil");
  private static final Key<String> NAME_MAPS_BUILT_FLAG = Key.create("NAME_MAPS_BUILT_FLAG");

  private static final Key<CachedValue<Map>> MAP_IN_CLASS_KEY = Key.create("MAP_KEY");

  @NotNull public static PsiField[] getAllFields(final PsiClass aClass) {
    return getAllByMap(aClass, PsiField.class);
  }

  @NotNull public static PsiMethod[] getAllMethods(final PsiClass aClass) {
    return getAllByMap(aClass, PsiMethod.class);
  }

  @NotNull public static PsiClass[] getAllInnerClasses(PsiClass aClass) {
    return getAllByMap(aClass, PsiClass.class);
  }

  @Nullable public static PsiField findFieldByName(PsiClass aClass, String name, boolean checkBases) {
    final PsiField[] byMap = findByMap(aClass, name, checkBases, PsiField.class);
    return byMap.length >= 1 ? byMap[0] : null;
  }

  @NotNull public static PsiMethod[] findMethodsByName(PsiClass aClass, String name, boolean checkBases) {
    return findByMap(aClass, name, checkBases, PsiMethod.class);
  }

  @Nullable public static PsiMethod findMethodBySignature(final PsiClass aClass, final PsiMethod patternMethod, final boolean checkBases) {
    final PsiMethod[] result = findMethodsBySignature(aClass, patternMethod, checkBases, true);
    return (result.length != 0 ? result[0] : null);
  }

  // ----------------------------- findMethodsBySignature -----------------------------------

  @NotNull public static PsiMethod[] findMethodsBySignature(final PsiClass aClass, final PsiMethod patternMethod, final boolean checkBases) {
    return findMethodsBySignature(aClass, patternMethod, checkBases, false);
  }

  @NotNull private static PsiMethod[] findMethodsBySignature(final PsiClass aClass,
                                                    final PsiMethod patternMethod,
                                                    final boolean checkBases,
                                                    final boolean stopOnFirst) {
/*    final MethodSignature patternSignature = MethodSignatureBackedByPsiMethod.create(patternMethod, PsiSubstitutor.EMPTY);
    if (!checkBases) {
      final PsiMethod[] methodsByName = aClass.findMethodsByName(patternMethod.getName(), false);
      if (methodsByName.length == 0) return PsiMethod.EMPTY_ARRAY;
      List<PsiMethod> result = new ArrayList<PsiMethod>();
      for (PsiMethod method : methodsByName) {
        final MethodSignature otherSignature = method.getSignature(PsiSubstitutor.EMPTY);
        if (otherSignature.equals(patternSignature)) {
          result.add(method);
          if (stopOnFirst) break;
        }
      }

      return result.toArray(new PsiMethod[result.size()]);
    }
    else {
      final Set<HierarchicalMethodSignature> signatures = getOverrideEquivalentSignatures(aClass);
      final HierarchicalMethodSignature signatureWithSupers = signatures.get(patternSignature);
      if (signatureWithSupers == null) return PsiMethod.EMPTY_ARRAY;
      final List<PsiMethod> result = new ArrayList<PsiMethod>();
      MethodSignatureUtil.processMethodHierarchy(signatureWithSupers, new Processor<HierarchicalMethodSignature>() {
        public boolean process(final HierarchicalMethodSignature sig) {
          result.add(sig.getSignature().getMethod());
          return !stopOnFirst;
        }
      });
      return result.toArray(new PsiMethod[result.size()]);
    }*/

    final PsiMethod[] methodsByName = aClass.findMethodsByName(patternMethod.getName(), checkBases);
    if (methodsByName.length == 0) return PsiMethod.EMPTY_ARRAY;
    final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (final PsiMethod method : methodsByName) {
      final PsiClass superClass = method.getContainingClass();
      final PsiSubstitutor substitutor;
      if (checkBases && !aClass.equals(superClass)) {
        substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
        LOG.assertTrue(substitutor != null);
      }
      else {
        substitutor = PsiSubstitutor.EMPTY;
      }
      final MethodSignature signature = method.getSignature(substitutor);
      if (signature.equals(patternSignature)) {
        methods.add(method);
        if (stopOnFirst) {
          break;
        }
      }
    }
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  // ----------------------------------------------------------------------------------------

  @Nullable public static PsiClass findInnerByName(PsiClass aClass, String name, boolean checkBases) {
    final PsiClass[] byMap = findByMap(aClass, name, checkBases, PsiClass.class);
    return byMap.length >= 1 ? byMap[0] : null;
  }

  @NotNull private static final <T> T[] emptyArrayByType(Class<T> type) {
    if (type.isAssignableFrom(PsiMethod.class)) return (T[])PsiMethod.EMPTY_ARRAY;
    if (type.isAssignableFrom(PsiField.class)) return (T[])PsiField.EMPTY_ARRAY;
    if (type.isAssignableFrom(PsiClass.class)) return (T[])PsiClass.EMPTY_ARRAY;

    LOG.assertTrue(false);
    return (T[])ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull private static <T extends PsiMember> T[] findByMap(PsiClass aClass, String name, boolean checkBases, Class<T> type) {
    if (name == null) return emptyArrayByType(type);

    if (!checkBases) {
      T[] members = null;
      if (type.isAssignableFrom(PsiMethod.class)) {
        members = (T[])aClass.getMethods();
      }
      else if (type.isAssignableFrom(PsiClass.class)) {
        members = (T[])aClass.getInnerClasses();
      }
      else if (type.isAssignableFrom(PsiField.class)) {
        members = (T[])aClass.getFields();
      }
      if (members == null) return emptyArrayByType(type);

      List<T> list = new ArrayList<T>();
      for (T member : members) {
        if (name.equals(member.getName())) list.add(member);
      }
      return list.toArray(emptyArrayByType(type));
    }
    else {
      final Map<String, List<Pair<T, PsiSubstitutor>>> allMethodsMap = getMap(aClass, type);
      final List<Pair<T, PsiSubstitutor>> list = allMethodsMap.get(name);
      if (list == null) return emptyArrayByType(type);
      final List<T> ret = new ArrayList<T>();
      for (final Pair<T, PsiSubstitutor> info : list) {
        ret.add(info.getFirst());
      }

      return ret.toArray(emptyArrayByType(type));
    }
  }

  public static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> getAllWithSubstitutorsByMap(PsiClass aClass, Class<T> type) {
    final Map<String, List<Pair<T, PsiSubstitutor>>> allMap = getMap(aClass, type);
    final List<Pair<T, PsiSubstitutor>> pairs = allMap.get(ALL);
    return pairs;
  }

  @NotNull private static <T extends PsiMember> T[] getAllByMap(PsiClass aClass, Class<T> type) {
    final Map<String, List<Pair<T, PsiSubstitutor>>> allMap = getMap(aClass, type);
    final List<Pair<T, PsiSubstitutor>> pairs = allMap.get(ALL);

    if (pairs == null) {
      LOG.error("pairs should be already computed. Wrong allMap: " + allMap);
    }

    final List<T> ret = new ArrayList<T>(pairs.size());
    for (final Pair<T, PsiSubstitutor> pair : pairs) {
      ret.add(pair.getFirst());
    }
    return ret.toArray(emptyArrayByType(type));
  }

  private static final @NonNls String ALL = "Intellij-IDEA-ALL";

  private static Map<Class<? extends PsiMember>, Map<String, List>> buildAllMaps(final PsiClass psiClass) {
    final List<Pair<PsiClass, PsiSubstitutor>> classes = new ArrayList<Pair<PsiClass, PsiSubstitutor>>();
    final List<Pair<PsiField, PsiSubstitutor>> fields = new ArrayList<Pair<PsiField, PsiSubstitutor>>();
    final List<Pair<PsiMethod, PsiSubstitutor>> methods = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();


    final List<MethodCandidateInfo> list = new ArrayList<MethodCandidateInfo>();
    processDeclarationsInClassNotCached(psiClass, new FilterScopeProcessor(new OrFilter(new ClassFilter(PsiMethod.class),
                                                                                        new ClassFilter(PsiField.class),
                                                                                        new ClassFilter(PsiClass.class)), null, list) {
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
    }, PsiSubstitutor.EMPTY, new HashSet<PsiClass>(), null, psiClass, false);

    synchronized (PsiLock.LOCK) {
      Map<Class<? extends PsiMember>, Map<String, List>> result = new HashMap<Class<? extends PsiMember>, Map<String, List>>(3);
      result.put(PsiClass.class, generateMapByList(classes));
      result.put(PsiMethod.class, generateMapByList(methods));
      result.put(PsiField.class, generateMapByList(fields));
      psiClass.putUserData(NAME_MAPS_BUILT_FLAG, "");
      return result;
    }
  }

  private static <T extends PsiMember> Map<String, List> generateMapByList(final List<Pair<T, PsiSubstitutor>> list) {
    LOG.assertTrue(list != null);
    Map<String, List> map = new HashMap<String, List>();
    map.put(ALL, list);
    for (final Pair<T, PsiSubstitutor> info : list) {
      final T element = info.getFirst();
      final String currentName = element.getName();
      List<Pair<T, PsiSubstitutor>> listByName = map.get(currentName);
      if (listByName == null) {
        listByName = new ArrayList<Pair<T, PsiSubstitutor>>(1);
        map.put(currentName, listByName);
      }
      listByName.add(info);
    }
    return map;
  }

  private static <T extends PsiMember> Map<String, List<Pair<T, PsiSubstitutor>>> getMap(final PsiClass psiClass, Class<T> memberClazz) {
    CachedValue<Map> cachedValue = psiClass.getUserData(MAP_IN_CLASS_KEY);
    if (cachedValue == null) {
      final CachedValueProvider<Map> provider = new ByNameCachedValueProvider(psiClass);
      cachedValue = psiClass.getManager().getCachedValuesManager().createCachedValue(provider, false);
      //Do not cache for nonphysical elements
      if (psiClass.isPhysical()) {
        psiClass.putUserData(MAP_IN_CLASS_KEY, cachedValue);
      }
    }
    return ((Map<String, List<Pair<T, PsiSubstitutor>>>)cachedValue.getValue().get(memberClazz));
  }

  private static class ByNameCachedValueProvider implements CachedValueProvider<Map> {
    private PsiClass myClass;

    public ByNameCachedValueProvider(final PsiClass aClass) {
      myClass = aClass;
    }

    public Result<Map> compute() {
      final Map<Class<? extends PsiMember>, Map<String, List>> map = buildAllMaps(myClass);
      return new Result<Map>(map, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }

  public static boolean processDeclarationsInClass(PsiClass aClass, PsiScopeProcessor processor,
                                                   PsiSubstitutor substitutor, Set<PsiClass> visited, PsiElement last,
                                                   PsiElement place, boolean isRaw) {
    if (visited.contains(aClass)) return true;
    isRaw = isRaw || PsiUtil.isRawSubstitutor(aClass, substitutor);
    if (last instanceof PsiTypeParameterList || last instanceof PsiModifierList) return true; //TypeParameterList and ModifierList do not see our declarations
    final Object data;
    synchronized (PsiLock.LOCK) {
      data = aClass.getUserData(NAME_MAPS_BUILT_FLAG);
    }
    if (data == null) {
      return processDeclarationsInClassNotCached(aClass, processor, substitutor, visited, last, place, isRaw);
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
            for (final Pair<PsiField, PsiSubstitutor> candidate : list) {
              PsiField candidateField = candidate.getFirst();
              PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(candidateField.getContainingClass(), candidate.getSecond(), aClass,
                                                                       substitutor, place);

              processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, candidateField.getContainingClass());
              if (!processor.execute(candidateField, finalSubstitutor)) return false;
            }
          }
        }
      }
      if (classHint == null || classHint.shouldProcess(PsiClass.class)) {
        if (last != null && last.getParent() == aClass) {
          if (last instanceof PsiClass) {
            if (!processor.execute(last, substitutor)) return false;
          }
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
              for (final Pair<PsiClass, PsiSubstitutor> candidate : list) {
                final PsiClass inner = candidate.getFirst();
                final PsiClass containingClass = inner.getContainingClass();
                if (containingClass != null) {
                  PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, candidate.getSecond(), aClass,
                                                                           substitutor, place);
                  processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
                  if (!processor.execute(inner, finalSubstitutor)) return false;
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
            for (PsiMethod constructor : constructors) {
              if (!methodResolverProcessor.execute(constructor, substitutor)) return false;
            }
            return true;
          }
        }
        final Map<String, List<Pair<PsiMethod, PsiSubstitutor>>> allMethodsMap = getMap(aClass, PsiMethod.class);
        final List<Pair<PsiMethod, PsiSubstitutor>> list = allMethodsMap.get(nameHint.getName());
        if (list != null) {
          for (final Pair<PsiMethod, PsiSubstitutor> candidate : list) {
            PsiMethod candidateMethod = candidate.getFirst();
            if (processor instanceof MethodResolverProcessor) {
              if (candidateMethod.isConstructor() != ((MethodResolverProcessor)processor).isConstructor()) continue;
            }
            final PsiClass containingClass = candidateMethod.getContainingClass();
            PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, candidate.getSecond(), aClass,
                                                                     substitutor, place);
            if (isRaw && !candidateMethod.hasModifierProperty(PsiModifier.STATIC)) { //static methods are not erased due to raw overriding
              PsiTypeParameter[] methodTypeParameters = candidateMethod.getTypeParameters();
              for (PsiTypeParameter methodTypeParameter : methodTypeParameters) {
                finalSubstitutor = ((PsiSubstitutorEx)finalSubstitutor).inplacePut(methodTypeParameter, null);
              }
            }
            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
            if (!processor.execute(candidateMethod, finalSubstitutor)) return false;
          }
        }
      }
      return true;
    }

    return processDeclarationsInClassNotCached(aClass, processor, substitutor, visited, last, place, isRaw);
  }

  private static PsiSubstitutor obtainFinalSubstitutor(PsiClass candidateClass,
                                               PsiSubstitutor candidateSubstitutor,
                                               PsiClass aClass,
                                               PsiSubstitutor substitutor,
                                               final PsiElement place) {
    PsiElementFactory elementFactory = candidateClass.getManager().getElementFactory();
    if (PsiUtil.isRawSubstitutor(aClass, substitutor)) {
      return elementFactory.createRawSubstitutor(candidateClass);
    }

    final PsiType containingType = elementFactory.createType(candidateClass, candidateSubstitutor, PsiUtil.getLanguageLevel(place));
    PsiType type = substitutor.substitute(containingType);
    if (!(type instanceof PsiClassType)) return candidateSubstitutor;
    return ((PsiClassType)type).resolveGenerics().getSubstitutor();
  }

  public static boolean processDeclarationsInClassNotCached(PsiClass aClass,
                                                            PsiScopeProcessor processor,
                                                            PsiSubstitutor substitutor,
                                                            Set<PsiClass> visited,
                                                            PsiElement last,
                                                            PsiElement place,
                                                            boolean isRaw) {
    if (visited.contains(aClass)) return true;
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
    final ElementClassHint classHint = processor.getHint(ElementClassHint.class);
    final NameHint nameHint = processor.getHint(NameHint.class);


    if (classHint == null || classHint.shouldProcess(PsiField.class)) {
      if (nameHint != null) {
        final PsiField fieldByName = aClass.findFieldByName(nameHint.getName(), false);
        if (fieldByName != null) {
          if (!processor.execute(fieldByName, substitutor)) return false;
        }
      }
      else {
        final PsiField[] fields = aClass.getFields();
        for (final PsiField field : fields) {
          if (!processor.execute(field, substitutor)) return false;
        }
      }
    }

    if (classHint == null || classHint.shouldProcess(PsiMethod.class)) {
      final PsiMethod[] methods = nameHint != null ? aClass.findMethodsByName(nameHint.getName(), false) : aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (isRaw && !method.hasModifierProperty(PsiModifier.STATIC)) { //static methods are not erased due to raw overriding
          PsiTypeParameter[] methodTypeParameters = method.getTypeParameters();
          for (PsiTypeParameter methodTypeParameter : methodTypeParameters) {
            substitutor = substitutor.put(methodTypeParameter, null);
          }
        }
        if (!processor.execute(method, substitutor)) return false;
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
        if (nameHint != null) {
          final PsiClass inner = aClass.findInnerClassByName(nameHint.getName(), false);
          if (inner != null) {
            if (!processor.execute(inner, substitutor)) return false;
          }
        }
        else {
          final PsiClass[] inners = aClass.getInnerClasses();
          for (final PsiClass inner : inners) {
            if (!processor.execute(inner, substitutor)) return false;
          }
        }
      }
    }

    visited.add(aClass);
    if (!(last instanceof PsiReferenceList)) {
      if (!processSuperTypes(
        aClass.getSuperTypes(),
        processor, visited, last, place, aClass, substitutor, isRaw)) {
        return false;
      }
    }
    return true;
  }

  private static boolean processSuperTypes(final PsiClassType[] superTypes,
                                           PsiScopeProcessor processor,
                                           Set<PsiClass> visited,
                                           PsiElement last,
                                           PsiElement place,
                                           PsiClass aClass,
                                           PsiSubstitutor substitutor,
                                           boolean isRaw) {
    for (final PsiClassType superType : superTypes) {
      final PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass, substitutor,
                                                               place);
      if (!processDeclarationsInClass(superClass, processor, finalSubstitutor, visited, last, place, isRaw)) {
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

    final PsiClassType[] referenceElements = psiClass.getExtendsListTypes();

    if (referenceElements.length == 0) return manager.findClass("java.lang.Object", resolveScope);

    PsiClass psiResoved = referenceElements[0].resolve();
    return psiResoved == null ? manager.findClass("java.lang.Object", resolveScope) : psiResoved;
  }

  @NotNull public static PsiClass[] getSupers(PsiClass psiClass) {
    final PsiClass[] supers = getSupersInner(psiClass);
    for (final PsiClass aSuper : supers) {
      LOG.assertTrue(aSuper != null);///
    }
    return supers;
  }

  private static PsiClass[] getSupersInner(PsiClass psiClass) {
    PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
    PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();

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
      if (extendsListTypes.length == 0) {
        final PsiClass objectClass = psiClass.getManager().findClass("java.lang.Object", psiClass.getResolveScope());
        return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
      }
      return resolveClassReferenceList(extendsListTypes, psiClass.getManager(),
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

  @NotNull public static PsiClassType[] getSuperTypes(PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null || !baseClass.isInterface()) {
        return new PsiClassType[]{baseClassType};
      }
      else {
        PsiClassType objectType = psiClass.getManager().getElementFactory().createTypeByFQClassName("java.lang.Object",
                                                                                                    psiClass.getResolveScope());
        return new PsiClassType[]{objectType, baseClassType};
      }
    }

    List<PsiClassType> result = new ArrayList<PsiClassType>();

    PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
    result.addAll(Arrays.asList(extendsTypes));
    boolean noExtends = extendsTypes.length == 0;

    result.addAll(Arrays.asList(psiClass.getImplementsListTypes()));

    if (noExtends) {
      PsiManager manager = psiClass.getManager();
      if (!"java.lang.Object".equals(psiClass.getQualifiedName())) {
        PsiClassType objectType = manager.getElementFactory().createTypeByFQClassName("java.lang.Object", psiClass.getResolveScope());
        result.add(0, objectType);
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

  public static PsiClass[] getInterfaces(PsiTypeParameter typeParameter) {
    final ArrayList<PsiClass> result = new ArrayList<PsiClass>();
    final PsiClassType[] referencedTypes = typeParameter.getExtendsListTypes();
    for (PsiClassType referencedType : referencedTypes) {
      final PsiClass psiClass = referencedType.resolve();
      if (psiClass != null && psiClass.isInterface()) {
        result.add(psiClass);
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  public static PsiClass[] getInterfaces(PsiClass psiClass) {
    final PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
    if (psiClass.isInterface()) {
      return resolveClassReferenceList(extendsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass != null && baseClass.isInterface()) return new PsiClass[]{baseClass};
      return PsiClass.EMPTY_ARRAY;
    }

    final PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();

    return resolveClassReferenceList(implementsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);
  }

  private static PsiClass[] resolveClassReferenceList(final PsiClassType[] listOfTypes,
                                                      final PsiManager manager, final GlobalSearchScope resolveScope, boolean includeObject)
  {
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
    for (PsiClassType reference : listOfTypes) {
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

  public static List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(PsiClass psiClass, String name, boolean checkBases) {
    if (!checkBases) {
      final PsiMethod[] methodsByName = psiClass.findMethodsByName(name, false);
      final List<Pair<PsiMethod, PsiSubstitutor>> ret = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>(methodsByName.length);
      for (final PsiMethod method : methodsByName) {
        ret.add(new Pair<PsiMethod, PsiSubstitutor>(method, PsiSubstitutor.EMPTY));
      }
      return ret;
    }
    final Map<String, List<Pair<PsiMethod, PsiSubstitutor>>> map = getMap(psiClass, PsiMethod.class);
    final List<Pair<PsiMethod, PsiSubstitutor>> list = map.get(name);
    return list == null ?
           Collections.<Pair<PsiMethod, PsiSubstitutor>>emptyList() :
           Collections.unmodifiableList(list);
  }

  public static PsiClassType[] getExtendsListTypes(PsiClass psiClass) {
    if (psiClass.isEnum()) {
      return new PsiClassType[]{getEnumSuperType(psiClass)};
    }
    else if (psiClass.isAnnotationType()) {
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
