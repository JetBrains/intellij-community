package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.HashSet;

import java.text.MessageFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * @author ven
 */
public class AnnotationsHighlightUtil {
  private static final String UNKNOWN_METHOD = "Cannot resolve method ''{0}''";
  private static final String MISSING_METHOD = "Cannot find method ''{0}''";
  private static final String INCOMPATIBLE_TYPES = "Incompatible types. Found: ''{0}'', required: ''{1}''";
  private static final String ILLEGAL_ARRAY_INITIALIZER = "Illegal initializer for ''{0}''";
  private static final String DUPLICATE_ANNOTATION = "Duplicate annotation";
  private static final String DUPLICATE_ATTRIBUTE = "Duplicate attribute ''{0}''";
  private static final String MISSING_ATTRIBUTES = "{0} missing though required";
  private static final String NOT_APPLICABLE = "@''{0}'' not applicable to {1}";
  private static final String NONCONSTANT_EXPRESSION = "Attribute value must be constant";
  private static final String INVALID_ANNOTATION_MEMBER_TYPE = "Invalid type for annotation member";
  private static final String CYCLIC_ANNOTATION_MEMER_TYPE = "Cyclic annotation element type";

  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil");

  public static HighlightInfo checkNameValuePair (PsiNameValuePair pair) {
    PsiReference ref = pair.getReference();
    PsiMethod method = (PsiMethod)ref.resolve();
    if (method == null) {
      if (pair.getName() != null) {
        String description = MessageFormat.format(UNKNOWN_METHOD, new Object[]{ref.getCanonicalText()});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getElement(), description);
      }
      else {
        String description = MessageFormat.format(MISSING_METHOD, new Object[]{ref.getCanonicalText()});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref.getElement(), description);
      }
    }
    else {
      PsiType returnType = method.getReturnType();
      PsiAnnotationMemberValue value = pair.getValue();
      HighlightInfo info = checkMemberValueType(value, returnType);
      if (info != null) return info;

      return checkDuplicateAttribute(pair);
    }
  }

  private static HighlightInfo checkDuplicateAttribute(PsiNameValuePair pair) {
    PsiAnnotationParameterList annotation = (PsiAnnotationParameterList) pair.getParent();
    PsiNameValuePair[] attributes = annotation.getAttributes();
    for (int i = 0; i < attributes.length; i++) {
      PsiNameValuePair attribute = attributes[i];
      if (attribute == pair) break;
      if (Comparing.equal(attribute.getName(), pair.getName())) {
        String description = MessageFormat.format(DUPLICATE_ATTRIBUTE, new Object[]{pair.getName() == null ?
            PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : pair.getName()});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, pair, description);
      }
    }

    return null;
  }

  private static String formatReference (PsiJavaCodeReferenceElement ref) {
    return ref.getCanonicalText();
  }

  public static HighlightInfo checkMemberValueType (PsiAnnotationMemberValue value, PsiType expectedType) {
    if (value instanceof PsiAnnotation) {
      PsiJavaCodeReferenceElement nameRef = ((PsiAnnotation)value).getNameReferenceElement();
      if (nameRef == null) return null;
      if (expectedType instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)expectedType).resolve();
        if (nameRef.isReferenceTo(aClass)) return null;
      }

      if (expectedType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)expectedType).getComponentType();
        if (componentType instanceof PsiClassType) {
          PsiClass aClass = ((PsiClassType)componentType).resolve();
          if (nameRef.isReferenceTo(aClass)) return null;
        }
      }

      String description = MessageFormat.format(INCOMPATIBLE_TYPES, new Object[]{formatReference(nameRef), HighlightUtil.formatType(expectedType)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    } else if (value instanceof PsiArrayInitializerMemberValue) {
      if (expectedType instanceof PsiArrayType) return null;
      String description = MessageFormat.format(ILLEGAL_ARRAY_INITIALIZER, new Object[]{HighlightUtil.formatType(expectedType)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    } else if (value instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression)value;
      PsiType type = expr.getType();
      if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr) ||
          (expectedType instanceof PsiArrayType && TypeConversionUtil.areTypesAssignmentCompatible(((PsiArrayType)expectedType).getComponentType(), expr))) {
        return null;
      }

      String description = MessageFormat.format(INCOMPATIBLE_TYPES, new Object[]{HighlightUtil.formatType(type), HighlightUtil.formatType(expectedType)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    }

    LOG.error("Unknown annotation member value");
    return null;
  }

  public static HighlightInfo[] checkDuplicatedAnnotations(PsiModifierList list) {
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    Set<PsiClass> refInterfaces = new HashSet<PsiClass>();
    PsiAnnotation[] annotations = list.getAnnotations();
    for (int i = 0; i < annotations.length; i++) {
      PsiAnnotation annotation = annotations[i];
      final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) return HighlightInfo.EMPTY_ARRAY;
      PsiClass aClass = (PsiClass)nameRef.resolve();
      if (aClass != null) {
        if (refInterfaces.contains(aClass)) {
          result.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, annotation.getNameReferenceElement(), DUPLICATE_ANNOTATION));
        }

        refInterfaces.add(aClass);
      }
    }

    return result.toArray(new HighlightInfo[result.size()]);
  }

  public static HighlightInfo checkMissingAttributes(PsiAnnotation annotation) {
    final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
    PsiClass aClass = (PsiClass)nameRef.resolve();
    if (aClass !=  null && aClass.isAnnotationType()) {
      Set<String> names = new HashSet<String>();
      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for (int i = 0; i < attributes.length; i++) {
        PsiNameValuePair attribute = attributes[i];
        if (attribute.getName() != null) {
          names.add(attribute.getName());
        }
        else {
          names.add(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
        }
      }

      PsiMethod[] annotationMethods = aClass.getMethods();
      List<String> missed = new ArrayList<String>();
      for (int i = 0; i < annotationMethods.length; i++) {
        PsiMethod method = annotationMethods[i];
        if (method instanceof PsiAnnotationMethod) {
          PsiAnnotationMethod annotationMethod = (PsiAnnotationMethod)method;
          if (annotationMethod.getDefaultValue() == null) {
            if (!names.contains(annotationMethod.getName())) {
              missed.add(annotationMethod.getName());
            }
          }
        }
      }

      if (missed.size() > 0) {
        StringBuffer buff = new StringBuffer("'"+ missed.get(0) + "'");
        for (int i = 1; i < missed.size(); i++) {
          buff.append(", ");
          buff.append("'"+ missed.get(i) + "'");
        }

        String description = MessageFormat.format(MISSING_ATTRIBUTES, new Object[]{buff});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, annotation.getNameReferenceElement(), description);
      }
    }

    return null;
  }

  public static HighlightInfo checkConstantExpression(PsiExpression expression) {
    if (expression.getParent() instanceof PsiAnnotationMethod || expression.getParent() instanceof PsiNameValuePair) {
      if (expression.getType() == PsiType.NULL || !PsiUtil.isConstantExpression(expression)) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, NONCONSTANT_EXPRESSION);
      }
    }

    return null;
  }

  public static HighlightInfo checkValidAnnotationType(final PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type != null && !type.accept(new PsiTypeVisitor<Boolean> () {
      public Boolean visitType(PsiType type) {
        return Boolean.FALSE;
      }

      public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return Boolean.TRUE;
      }

      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      public Boolean visitClassType(PsiClassType classType) {
        if (classType.getParameters().length > 0) {
          PsiType javaLangClass = PsiType.getJavaLangClass(typeElement.getManager(), typeElement.getResolveScope());
          if (javaLangClass != null && javaLangClass.equals(classType.rawType())) {
            return Boolean.TRUE;
          }
          return Boolean.FALSE;
        }
        PsiClass aClass = classType.resolve();
        if (aClass != null && (aClass.isAnnotationType() || aClass.isEnum())) return Boolean.TRUE;

        PsiManager manager = typeElement.getManager();
        GlobalSearchScope resolveScope = typeElement.getResolveScope();
        return classType.equals(PsiType.getJavaLangClass(manager, resolveScope)) ||
               classType.equals(PsiType.getJavaLangString(manager, resolveScope)) ? Boolean.TRUE : Boolean.FALSE;
      }
    }).booleanValue()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, INVALID_ANNOTATION_MEMBER_TYPE);
    }

    return null;
  }

  public static HighlightInfo checkApplicability(PsiAnnotation annotation) {
    if (!(annotation.getParent() instanceof PsiModifierList)) return null;
    PsiElement owner = annotation.getParent().getParent();
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef != null) {
      PsiElement resolved = nameRef.resolve();
      if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
        PsiClass annotationType = (PsiClass)resolved;
        PsiAnnotation metaAnnotation = annotationType.getModifierList().findAnnotation("java.lang.annotation.Target");
        if (metaAnnotation != null) {
          PsiNameValuePair[] attributes = metaAnnotation.getParameterList().getAttributes();
          if (attributes.length >= 1) {
            PsiField elementType = getElementType(owner);
            if (elementType != null) {
              PsiAnnotationMemberValue value = attributes[0].getValue();
              if (value instanceof PsiArrayInitializerMemberValue) {
                PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
                for (int j = 0; j < initializers.length; j++) {
                  PsiAnnotationMemberValue initializer = initializers[j];
                  if (initializer instanceof PsiReferenceExpression) {
                    PsiReferenceExpression refExpr = (PsiReferenceExpression)initializer;
                    if (refExpr.isReferenceTo(elementType)) return null;
                  }
                }
                return formatNotApplicableError(elementType, nameRef);
              }
              else if (value instanceof PsiReferenceExpression) {
                if (!((PsiReferenceExpression)value).isReferenceTo(elementType)) {
                  return formatNotApplicableError(elementType, nameRef);
                }
              }
            }
          }
        }
      }
    }

    return null;
  }

  private static HighlightInfo formatNotApplicableError(PsiField elementType, PsiJavaCodeReferenceElement nameRef) {
    String name = elementType.getName().replace('_', ' ').toLowerCase();
    String description = MessageFormat.format(NOT_APPLICABLE, new Object[]{nameRef.getText(), name + "s"});
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameRef, description);
  }

  private static PsiField getElementType(PsiElement owner) {
    PsiManager manager = owner.getManager();
    PsiClass elementTypeClass = manager.findClass("java.lang.annotation.ElementType", owner.getResolveScope());
    if (elementTypeClass == null) return null;

    if (owner instanceof PsiClass) {
      if (((PsiClass)owner).isAnnotationType()) {
        return elementTypeClass.findFieldByName("ANNOTATION_TYPE", false);
      }
      else {
        return elementTypeClass.findFieldByName("TYPE", false);
      }
    } else if (owner instanceof PsiMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return elementTypeClass.findFieldByName("CONSTRUCTOR", false);
      }
      else {
        return elementTypeClass.findFieldByName("METHOD", false);
      }
    } else if (owner instanceof PsiField) {
      return elementTypeClass.findFieldByName("FIELD", false);
    } else if (owner instanceof PsiParameter) {
      return elementTypeClass.findFieldByName("PARAMETER", false);
    } else if (owner instanceof PsiLocalVariable) {
      return elementTypeClass.findFieldByName("LOCAL_VARIABLE", false);
    } else if (owner instanceof PsiPackage) {
      return elementTypeClass.findFieldByName("PACKAGE", false);
    }

    return null;
  }

  public static HighlightInfo checkAnnotationType(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement != null) {
      PsiElement resolved = nameReferenceElement.resolve();
      if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameReferenceElement, "Annotation type expected");
      }
    }
    return null;
  }

  public static HighlightInfo checkCyclicMemberType(PsiTypeElement typeElement, PsiClass aClass) {
    LOG.assertTrue(aClass.isAnnotationType());
    PsiType type = typeElement.getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, CYCLIC_ANNOTATION_MEMER_TYPE);
    }
    return null;
  }
}
