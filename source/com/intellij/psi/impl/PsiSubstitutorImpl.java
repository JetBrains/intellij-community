package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author ik, dsl
 */
public class PsiSubstitutorImpl implements PsiSubstitutorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSubstitutorImpl");
  private final Map<PsiTypeParameter, PsiType> mySubstitutionMap;

  protected PsiSubstitutorImpl(Map<PsiTypeParameter, PsiType> map) {
    mySubstitutionMap = map;
  }

  public PsiSubstitutorImpl() {
    mySubstitutionMap = new HashMap<PsiTypeParameter, PsiType>();
  }

  public PsiType substitute(PsiTypeParameter typeParameter){
    if(!mySubstitutionMap.containsKey(typeParameter)){
      return typeParameter.getManager().getElementFactory().createType(typeParameter);
    }
    return mySubstitutionMap.get(typeParameter);
  }

  public PsiType substitute(PsiType type) {
    if (type == null) return null;
    PsiType substituted = type.accept(myInternalSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type);
  }

  public PsiType substituteAndCapture(PsiType type) {
    if (type == null) return null;
    PsiType substituted = type.accept(myInternalCapturingSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type);
  }

  public PsiType substituteAndFullCapture(PsiType type) {
    if (type == null) return null;
    PsiType substituted = type.accept(myInternalFullCapturingSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type);
  }

  private PsiType rawTypeForTypeParameter(final PsiTypeParameter typeParameter) {
    final PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
    if (extendsTypes.length > 0) {
      // First bound
      return substitute(extendsTypes[0]);
    }
    else {
      // Object
      return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
    }
  }

  private abstract static class SubstitutionVisitor extends PsiTypeVisitorEx<PsiType> {
    public PsiType visitType(PsiType type) {
      LOG.assertTrue(false);
      return null;
    }

    public PsiType visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound == null) {
        return wildcardType;
      }
      else {
        final PsiType newBound = bound.accept(this);
        if (newBound == null) {
          return null;
        }
        else if (newBound instanceof PsiWildcardType) {
          final PsiType newBoundBound = ((PsiWildcardType)newBound).getBound();
          if (newBoundBound != null) {
            return PsiWildcardType.changeBound(wildcardType, newBoundBound);
          }
          else {
            return PsiWildcardType.createUnbounded(wildcardType.getManager());
          }
        }
        else {
          return PsiWildcardType.changeBound(wildcardType, newBound);
        }
      }
    }

    public PsiType visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return primitiveType;
    }

    public PsiType visitArrayType(PsiArrayType arrayType) {
      final PsiType componentType = arrayType.getComponentType();
      final PsiType substitutedComponentType = componentType.accept(this);
      if (substitutedComponentType == null) return null;
      if (substitutedComponentType == componentType) return arrayType; // optimization
      return new PsiArrayType(substitutedComponentType);
    }

    public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
      final PsiType componentType = ellipsisType.getComponentType();
      final PsiType substitutedComponentType = componentType.accept(this);
      if (substitutedComponentType == null) return null;
      if (substitutedComponentType == componentType) return ellipsisType; // optimization
      return new PsiEllipsisType(substitutedComponentType);
    }

    public PsiType visitTypeVariable(final PsiTypeVariable var) {
      return var;
    }

    public PsiType visitBottom(final Bottom bottom) {
      return bottom;
    }

    public abstract PsiType visitClassType(PsiClassType classType);
  }

  private final InternalSubstitutionVisitor myInternalSubstitutionVisitor = new InternalSubstitutionVisitor();
  private final InternalCapturingSubstitutionVisitor myInternalCapturingSubstitutionVisitor = new InternalCapturingSubstitutionVisitor();
  private final InternalFullCapturingSubstitutionVisitor myInternalFullCapturingSubstitutionVisitor =
    new InternalFullCapturingSubstitutionVisitor();

  private static final Key<Boolean> PREVENT_RECURSION_KEY = Key.create("PREVENT_RECURSION_KEY");

  private class InternalSubstitutionVisitor extends SubstitutionVisitor {
    public PsiType visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return classType;
      if (aClass instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = ((PsiTypeParameter)aClass);
        if (!mySubstitutionMap.containsKey(typeParameter)) {
          return classType;
        }
        else {
          return substituteTypeParameter(typeParameter);
        }
      }
      final Map<PsiTypeParameter, PsiType> hashMap = new HashMap<PsiTypeParameter, PsiType>();
      if (!processClass(aClass, resolveResult.getSubstitutor(), hashMap)) {
        return null;
      }
      return aClass.getManager().getElementFactory().createType(aClass, createSubstitutor(hashMap), classType.getLanguageLevel());
    }

    protected PsiType substituteTypeParameter(final PsiTypeParameter typeParameter) {
      PsiType substituted = mySubstitutionMap.get(typeParameter);
      if (typeParameter.getUserData(PREVENT_RECURSION_KEY) != null) return PsiWildcardType.createUnbounded(typeParameter.getManager());
      if (substituted instanceof PsiWildcardType && !((PsiWildcardType)substituted).isSuper()) {
        PsiType originalBound = ((PsiWildcardType)substituted).getBound();
        PsiManager manager = typeParameter.getManager();
        final PsiType[] boundTypes = typeParameter.getExtendsListTypes();
        for (PsiType boundType : boundTypes) {
          PsiType substitutedBoundType;
          synchronized (PsiLock.LOCK) {
            //prevent infinite recursion
            try {
              typeParameter.putUserData(PREVENT_RECURSION_KEY, Boolean.TRUE);
              substitutedBoundType = boundType.accept(this);
            }
            finally {
              typeParameter.putUserData(PREVENT_RECURSION_KEY, null);
            }
          }
          PsiWildcardType wildcardType = (PsiWildcardType)substituted;
          if (substitutedBoundType != null && !substitutedBoundType.equalsToText("java.lang.Object")) {
            if (originalBound == null || !substitutedBoundType.isAssignableFrom(originalBound)) {
              if (wildcardType.isExtends()) {
                final PsiType glb = GenericsUtil.getGreatestLowerBound(wildcardType.getBound(), substitutedBoundType);
                if (glb != null) {
                 substituted = PsiWildcardType.createExtends(manager, glb);
                }
              }
              else {
                //unbounded
                substituted = PsiWildcardType.createExtends(manager, substitutedBoundType);
              }
            }
          }
        }
      }

      return substituted;
    }

    private PsiType substituteInternal(PsiType type) {
      if (type == null) return null;
      return type.accept(this);
    }

    private boolean processClass(PsiClass resolve, PsiSubstitutor originalSubstitutor, final Map<PsiTypeParameter, PsiType> substMap) {
      final PsiTypeParameter[] params = resolve.getTypeParameters();
      for (final PsiTypeParameter param : params) {
        final PsiType substituted = substituteInternal(originalSubstitutor.substitute(param));
        if (substituted == null) return false;
        substMap.put(param, substituted);
      }
      if (resolve.hasModifierProperty(PsiModifier.STATIC)) return true;

      final PsiClass containingClass = resolve.getContainingClass();
      return containingClass == null ||
             processClass(containingClass, originalSubstitutor, substMap);
    }
  }

  private PsiType correctExternalSubstitution(PsiType substituted, final PsiType original) {
    if (original == null) return null;

    if (substituted == null) {
      return original.accept(new PsiTypeVisitor<PsiType>() {
        public PsiType visitArrayType(PsiArrayType arrayType) {
          return new PsiArrayType(arrayType.getComponentType().accept(this));
        }

        public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
          return new PsiEllipsisType(ellipsisType.getComponentType().accept(this));
        }

        public PsiType visitClassType(PsiClassType classType) {
          PsiClass aClass = classType.resolve();
          if (aClass != null) {
            if (aClass instanceof PsiTypeParameter) {
              return rawTypeForTypeParameter((PsiTypeParameter)aClass);
            } else {
              return aClass.getManager().getElementFactory().createType(aClass);
            }
          }
          else {
            return classType;
          }
        }

        public PsiType visitType(PsiType type) {
          LOG.assertTrue(false);
          return null;
        }
      });
    }
    return substituted;
  }

  private class InternalCapturingSubstitutionVisitor extends InternalSubstitutionVisitor {
    protected PsiType substituteTypeParameter(PsiTypeParameter typeParameter) {
      PsiType type = super.substituteTypeParameter(typeParameter);
      if (type instanceof PsiWildcardType && typeParameter.getOwner() instanceof PsiClass) {
        return PsiCapturedWildcardType.create((PsiWildcardType)type);
      }
      return type;
    }
  }

  private class InternalFullCapturingSubstitutionVisitor extends InternalSubstitutionVisitor {
    protected PsiType substituteTypeParameter(PsiTypeParameter typeParameter) {
      PsiType type = super.substituteTypeParameter(typeParameter);
      if (type instanceof PsiWildcardType) {
        return PsiCapturedWildcardType.create((PsiWildcardType)type);
      }
      return type;
    }
  }

  public synchronized PsiSubstitutor put(PsiTypeParameter typeParameter, PsiType mapping) {
    final PsiSubstitutorImpl ret = new PsiSubstitutorImpl(new HashMap<PsiTypeParameter, PsiType>(mySubstitutionMap));
    ret.mySubstitutionMap.put(typeParameter, mapping);
    return ret;
  }

  public synchronized PsiSubstitutor putAll(PsiClass parentClass, PsiType[] mappings) {
    final PsiTypeParameter[] params = parentClass.getTypeParameters();
    PsiSubstitutorImpl substitutor = new PsiSubstitutorImpl(new HashMap<PsiTypeParameter, PsiType>(mySubstitutionMap));
    for (int i = 0; i < params.length; i++) {
      if (mappings != null && mappings.length > i) {
        substitutor.mySubstitutionMap.put(params[i], mappings[i]);
      }
      else {
        substitutor.mySubstitutionMap.put(params[i], null);
      }
    }

    return substitutor;
  }

  public PsiSubstitutor putAll(PsiSubstitutor another) {
    if (another instanceof EmptySubstitutorImpl) return this;
    final PsiSubstitutorImpl anotherImpl = (PsiSubstitutorImpl)another;
    final PsiTypeParameter[] params = anotherImpl.mySubstitutionMap.keySet().toArray(PsiTypeParameter.EMPTY_ARRAY);
    PsiSubstitutorImpl substitutor = new PsiSubstitutorImpl(new HashMap<PsiTypeParameter, PsiType>(mySubstitutionMap));
    for (final PsiTypeParameter param : params) {
      substitutor.mySubstitutionMap.put(param, another.substitute(param));
    }

    return substitutor;
  }


  public String toString() {
    @NonNls StringBuffer buffer = new StringBuffer();
    final Set<Map.Entry<PsiTypeParameter, PsiType>> set = mySubstitutionMap.entrySet();
    for (Map.Entry<PsiTypeParameter, PsiType> entry : set) {
      final PsiTypeParameter typeParameter = entry.getKey();
      buffer.append(typeParameter.getName());
      final PsiElement owner = typeParameter.getOwner();
      if (owner instanceof PsiClass) {
        buffer.append(" of ");
        buffer.append(((PsiClass)owner).getQualifiedName());
      }
      else if (owner instanceof PsiMethod) {
        buffer.append(" of ");
        buffer.append(((PsiMethod)owner).getName());
        buffer.append(" in ");
        buffer.append(((PsiMethod)owner).getContainingClass().getQualifiedName());
      }
      buffer.append(" -> ");
      if (entry.getValue() != null) {
        buffer.append(entry.getValue().getCanonicalText());
      }
      else {
        buffer.append("null");
      }
      buffer.append('\n');
    }
    return buffer.toString();
  }

  public static PsiSubstitutor createSubstitutor(Map<PsiTypeParameter, PsiType> map) {
    if (map == null || map.keySet().isEmpty()) return EMPTY;
    return new PsiSubstitutorImpl(map);
  }

  public boolean isValid() {
    Collection<PsiType> substitutorValues = mySubstitutionMap.values();
    for (PsiType type : substitutorValues) {
      if (type != null && !type.isValid()) return false;
    }
    return true;
  }

  @NotNull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.unmodifiableMap(mySubstitutionMap);
  }

  public PsiSubstitutor inplacePut(PsiTypeParameter typeParameter, PsiType mapping) {
    mySubstitutionMap.put(typeParameter, mapping);
    return this;
  }

  public PsiSubstitutor inplacePutAll(PsiClass parentClass, PsiType[] mappings) {
    final PsiTypeParameter[] params = parentClass.getTypeParameters();

    for (int i = 0; i < params.length; i++) {
      if (mappings != null && mappings.length > i) {
        mySubstitutionMap.put(params[i], mappings[i]);
      }
      else {
        mySubstitutionMap.put(params[i], null);
      }
    }

    return this;
  }
}
