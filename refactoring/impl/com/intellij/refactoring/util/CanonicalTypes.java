package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.Iterator;
import java.util.Map;
import java.util.Collection;

import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public class CanonicalTypes {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.CanonicalTypes");
  public static abstract class Type {
    public abstract PsiType getType(PsiElement context, final PsiManager manager) throws IncorrectOperationException;

    @NonNls
    public abstract String getTypeText();

    public abstract void addImportsTo(final PsiCodeFragment codeFragment);
  }

  private static class Primitive extends Type {
    private PsiPrimitiveType myType;
    private Primitive(PsiPrimitiveType type) {
      myType = type;
    }

    public PsiType getType(PsiElement context, final PsiManager manager) {
      return myType;
    }

    public String getTypeText() {
      return myType.getPresentableText();
    }

    public void addImportsTo(final PsiCodeFragment codeFragment) {}
  }

  private static class Array extends Type {
    private Type myComponentType;

    private Array(Type componentType) {
      myComponentType = componentType;
    }

    public PsiType getType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
      return myComponentType.getType(context, manager).createArrayType();
    }

    public String getTypeText() {
      return myComponentType.getTypeText() + "[]";
    }

    public void addImportsTo(final PsiCodeFragment codeFragment) {
      myComponentType.addImportsTo(codeFragment);
    }
  }

  private static class Ellipsis extends Type {
    private Type myComponentType;

    private Ellipsis(Type componentType) {
      myComponentType = componentType;
    }

    public PsiType getType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
      return new PsiEllipsisType(myComponentType.getType(context, manager));
    }

    public String getTypeText() {
      return myComponentType.getTypeText() + "...";
    }

    public void addImportsTo(final PsiCodeFragment codeFragment) {
      myComponentType.addImportsTo(codeFragment);
    }
  }

  private static class WildcardType extends Type {
    private final boolean myIsExtending;
    private final Type myBound;

    private WildcardType(boolean isExtending, Type bound) {
      myIsExtending = isExtending;
      myBound = bound;
    }

    public PsiType getType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
      if(myBound == null) return PsiWildcardType.createUnbounded(context.getManager());
      if (myIsExtending) {
        return PsiWildcardType.createExtends(context.getManager(), myBound.getType(context, manager));
      }
      else {
        return PsiWildcardType.createSuper(context.getManager(), myBound.getType(context, manager));
      }
    }

    public String getTypeText() {
      if (myBound == null) return "?";
      return "? " + (myIsExtending ? "extends " : "super ") + myBound.getTypeText();
    }

    public void addImportsTo(final PsiCodeFragment codeFragment) {
      if (myBound != null) myBound.addImportsTo(codeFragment);
    }
  }

  private static class WrongType extends Type {
    private String myText;

    private WrongType(String text) {
      myText = text;
    }

    public PsiType getType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
      return context.getManager().getElementFactory().createTypeFromText(myText, context);
    }

    public String getTypeText() {
      return myText;
    }

    public void addImportsTo(final PsiCodeFragment codeFragment) {}
  }


  private static class Class extends Type {
    private final String myOriginalText;
    private String myClassQName;
    private Map<String,Type> mySubstitutor;

    private Class(String originalText, String classQName, Map<String, Type> substitutor) {
      myOriginalText = originalText;
      myClassQName = classQName;
      mySubstitutor = substitutor;
    }

    public PsiType getType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
      final PsiElementFactory factory = manager.getElementFactory();
      final PsiResolveHelper resolveHelper = manager.getResolveHelper();
      final PsiClass aClass = resolveHelper.resolveReferencedClass(myClassQName, context);
      if (aClass == null) {
        return factory.createTypeFromText(myClassQName, context);
      }
      Map<PsiTypeParameter, PsiType> substMap = new HashMap<PsiTypeParameter,PsiType>();
      final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
      while (iterator.hasNext()) {
        PsiTypeParameter typeParameter = iterator.next();
        final String name = typeParameter.getName();
        final Type type = mySubstitutor.get(name);
        if (type != null) {
          substMap.put(typeParameter, type.getType(context, manager));
        } else {
          substMap.put(typeParameter, null);
        }
      }
      return factory.createType(aClass, factory.createSubstitutor(substMap));
    }

    public String getTypeText() {
      return myOriginalText;
    }

    public void addImportsTo(final PsiCodeFragment codeFragment) {
      codeFragment.addImportsFromString(myClassQName);
      final Collection<Type> types = mySubstitutor.values();
      for (Type type : types) {
        if (type != null) {
          type.addImportsTo(codeFragment);
        }
      }
    }
  }

  private static class Creator extends PsiTypeVisitor<Type> {
    public static final Creator INSTANCE = new Creator();
    public Type visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return new Primitive(primitiveType);
    }

    public Type visitEllipsisType(PsiEllipsisType ellipsisType) {
      return new Ellipsis(ellipsisType.getComponentType().accept(this));
    }

    public Type visitArrayType(PsiArrayType arrayType) {
      return new Array(arrayType.getComponentType().accept(this));
    }

    public Type visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType wildcardBound = wildcardType.getBound();
      final Type bound = wildcardBound == null ? null : wildcardBound.accept(this);
      return new WildcardType(wildcardType.isExtends(), bound);
    }

    public Type visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass instanceof PsiAnonymousClass) {
        return visitClassType(((PsiAnonymousClass)aClass).getBaseClassType());
      }
      final String originalText = classType.getPresentableText();
      if (aClass == null) {
        return new WrongType(originalText);
      } else {
        Map<String,Type> substMap = new HashMap<String,Type>();
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
        while (iterator.hasNext()) {
          PsiTypeParameter typeParameter = iterator.next();
          final PsiType substType = substitutor.substitute(typeParameter);
          final String name = typeParameter.getName();
          if (substType == null) {
            substMap.put(name, null);
          } else {
            substMap.put(name, substType.accept(this));
          }
        }
        final String qualifiedName = aClass.getQualifiedName();
        LOG.assertTrue(aClass.getName() != null);
        return new Class(originalText, qualifiedName != null ? qualifiedName : aClass.getName(), substMap);
      }
    }
  }

  public static Type createTypeWrapper(PsiType type) {
    return type.accept(Creator.INSTANCE);
  }
}
