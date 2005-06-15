package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author ik, dsl
 */
public class PsiSubstitutorImpl implements PsiSubstitutorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSubstitutorImpl");
  private final Map<PsiTypeParameter, PsiType> mySubstitutionMap;

  protected PsiSubstitutorImpl(Map<PsiTypeParameter, PsiType> map){
    mySubstitutionMap = map;
  }

  public PsiSubstitutorImpl(){
    mySubstitutionMap = new HashMap<PsiTypeParameter, PsiType>();
  }

  public PsiType substitute(PsiTypeParameter typeParameter){
    if(!mySubstitutionMap.containsKey(typeParameter)){
      return typeParameter.getManager().getElementFactory().createType(typeParameter);
    }
    return mySubstitutionMap.get(typeParameter);
  }

  public PsiType substitute(PsiType type){
    if (type == null) return null;
    PsiType substituted = type.accept(myInternalSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type, myInternalSubstitutionVisitor);
  }

  public PsiType substituteAndCapture(PsiType type){
    if (type == null) return null;
    PsiType substituted = type.accept(myInternalCapturingSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type, myInternalCapturingSubstitutionVisitor);
  }

  public PsiType substituteAndFullCapture(PsiType type) {
    if (type == null) return null;
    PsiType substituted = type.accept(myInternalFullCapturingSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type, myInternalFullCapturingSubstitutionVisitor);
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

  private abstract class SubstitutionVisitor extends PsiTypeVisitorEx<PsiType> {
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
  private final InternalFullCapturingSubstitutionVisitor myInternalFullCapturingSubstitutionVisitor = new InternalFullCapturingSubstitutionVisitor();

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
      processClass(aClass, resolveResult.getSubstitutor(), hashMap);
      return aClass.getManager().getElementFactory().createType(aClass, createSubstitutor(hashMap));
    }

    protected PsiType substituteTypeParameter(final PsiTypeParameter typeParameter) {
      return mySubstitutionMap.get(typeParameter);
    }

    private PsiType substituteInternal(PsiType type) {
      if (type == null) return null;
      return type.accept(this);
    }

    private void processClass(PsiClass resolve, PsiSubstitutor originalSubstitutor, final Map<PsiTypeParameter, PsiType> substMap) {
      final PsiTypeParameter[] params = resolve.getTypeParameters();
      for (final PsiTypeParameter param : params) {
        substMap.put(param, substituteInternal(originalSubstitutor.substitute(param)));
      }
      if (resolve.hasModifierProperty(PsiModifier.STATIC)) return;

      final PsiClass containingClass = resolve.getContainingClass();
      if (containingClass != null) {
        processClass(containingClass, originalSubstitutor, substMap);
      }
    }
  }

  private PsiType correctExternalSubstitution(PsiType substituted, final PsiType original, final InternalSubstitutionVisitor visitor) {
    if (original == null) return null;
    boolean captured = false;
    PsiType copy = substituted;
    if (copy instanceof PsiCapturedWildcardType) {
      captured = true;
      copy = ((PsiCapturedWildcardType)substituted).getWildcard();
    }
    if (copy instanceof PsiWildcardType && !((PsiWildcardType)copy).isSuper()) {
      PsiWildcardType wildcardType = (PsiWildcardType)copy;
      if (original instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)original).resolve();
        if (aClass instanceof PsiTypeParameter) {
          final PsiType boundType = visitor.substituteInternal(aClass.getSuperTypes()[0]);
          if (boundType != null && !boundType.equalsToText("java.lang.Object")) {
            final PsiManager manager = aClass.getManager();
            if (wildcardType.isExtends()) {
              final PsiType glb = GenericsUtil.getGreatestLowerBound(wildcardType.getBound(), boundType);
              if (glb != null) {
                PsiWildcardType corrected = PsiWildcardType.createExtends(manager, glb);
                return captured ? PsiCapturedWildcardType.create(corrected) : ((PsiType)corrected);
              }
            }
            else {
              //unbounded
              PsiWildcardType corrected = PsiWildcardType.createExtends(manager, boundType);
              return captured ? PsiCapturedWildcardType.create(corrected) : ((PsiType)corrected);
            }
          }
        }
      }
    }

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
          return rawTypeForTypeParameter((PsiTypeParameter)aClass);
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

  public synchronized PsiSubstitutor putAll(PsiClass parentClass, PsiType[] mappings){
    final PsiTypeParameter[] params = parentClass.getTypeParameters();
    PsiSubstitutorImpl substitutor = new PsiSubstitutorImpl(new HashMap<PsiTypeParameter, PsiType>(mySubstitutionMap));
    for(int i = 0; i < params.length; i++){
      if (mappings != null && mappings.length > i) {
        substitutor.mySubstitutionMap.put(params[i], mappings[i]);
      }
      else {
        substitutor.mySubstitutionMap.put(params[i], null);
      }
    }

    return substitutor;
  }

  public PsiSubstitutor putAll(PsiSubstitutor another){
    if(another instanceof EmptySubstitutorImpl) return this;
    final PsiSubstitutorImpl anotherImpl = (PsiSubstitutorImpl) another;
    final PsiTypeParameter[] params = anotherImpl.mySubstitutionMap.keySet().toArray(PsiTypeParameter.EMPTY_ARRAY);
    PsiSubstitutorImpl substitutor = new PsiSubstitutorImpl(new HashMap<PsiTypeParameter, PsiType>(mySubstitutionMap));
    for (final PsiTypeParameter param : params) {
      substitutor.mySubstitutionMap.put(param, another.substitute(param));
    }

    return substitutor;
  }


  public PsiSubstitutor merge(PsiSubstitutor other){
    if(other == PsiSubstitutor.EMPTY) return this;

    PsiSubstitutorImpl substitutor = new PsiSubstitutorImpl(new HashMap<PsiTypeParameter, PsiType>(mySubstitutionMap));
    substitutor.mySubstitutionMap.putAll(((PsiSubstitutorImpl)other).mySubstitutionMap);
    return substitutor;
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    final Set<Map.Entry<PsiTypeParameter,PsiType>> set = mySubstitutionMap.entrySet();
    for (Map.Entry<PsiTypeParameter, PsiType> entry : set) {
      final PsiTypeParameter typeParameter = entry.getKey();
      buffer.append(typeParameter.getName());
      final PsiElement owner = typeParameter.getOwner();
      if (owner instanceof PsiClass) {
        buffer.append(" of " + ((PsiClass)owner).getQualifiedName());
      }
      else if (owner instanceof PsiMethod) {
        buffer.append(" of " + ((PsiMethod)owner).getName() + " in " + ((PsiMethod)owner).getContainingClass().getQualifiedName());
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

  public static PsiSubstitutor createSubstitutor(Map<PsiTypeParameter, PsiType> map){
    if(map == null || map.keySet().isEmpty()) return EMPTY;
    return new PsiSubstitutorImpl(map);
  }

  public boolean isValid() {
    Collection<PsiType> substitutorValues = mySubstitutionMap.values();
    for (PsiType type : substitutorValues) {
      if (type != null && !type.isValid()) return false;
    }
    return true;
  }

  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.unmodifiableMap(mySubstitutionMap);
  }

  public PsiSubstitutor inplacePut(PsiTypeParameter typeParameter, PsiType mapping) {
    mySubstitutionMap.put(typeParameter, mapping);
    return this;
  }

  public PsiSubstitutor inplacePutAll(PsiClass parentClass, PsiType[] mappings) {
    final PsiTypeParameter[] params = parentClass.getTypeParameters();

    for(int i = 0; i < params.length; i++){
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
