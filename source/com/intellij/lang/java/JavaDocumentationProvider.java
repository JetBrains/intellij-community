/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.java;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.LangBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 11, 2006
 * Time: 7:45:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaDocumentationProvider implements DocumentationProvider {
  public String getQuickNavigateInfo(PsiElement element) {
    if (element instanceof PsiClass) {
      return generateClassInfo((PsiClass) element);
    } else if (element instanceof PsiMethod) {
      return generateMethodInfo((PsiMethod) element);
    } else if (element instanceof PsiField) {
      return generateFieldInfo((PsiField) element);
    } else if (element instanceof PsiVariable) {
      return generateVariableInfo((PsiVariable) element);
    } else if (element instanceof PsiPackage) {
      return generatePackageInfo((PsiPackage) element);
    }
    return null;
  }

  private static void newLine(StringBuffer buffer) {
    // Don't know why space has to be added after newline for good text alignment...
    buffer.append("\n ");
  }

  private static void generateType(@NonNls StringBuffer buffer, PsiType type, PsiElement context) {
    if (type instanceof PsiPrimitiveType) {
      buffer.append(type.getCanonicalText());

      return;
    }

    if (type instanceof PsiWildcardType) {
      PsiWildcardType wc = ((PsiWildcardType) type);
      PsiType bound = wc.getBound();

      buffer.append("?");

      if (bound != null) {
        buffer.append(wc.isExtends() ? " extends " : " super ");
        generateType(buffer, bound, context);
      }
    }

    if (type instanceof PsiArrayType) {
      generateType(buffer, ((PsiArrayType) type).getComponentType(), context);
      buffer.append("[]");

      return;
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType) type).resolveGenerics();
      PsiClass psiClass = result.getElement();
      PsiSubstitutor psiSubst = result.getSubstitutor();

      if (psiClass == null || psiClass instanceof PsiTypeParameter) {
        buffer.append(type.getPresentableText());
        return;
      }

      buffer.append(JavaDocUtil.getShortestClassName(psiClass, context));

      if (psiClass.hasTypeParameters()) {
        StringBuffer subst = new StringBuffer();
        boolean goodSubst = true;

        PsiTypeParameter[] params = psiClass.getTypeParameters();

        subst.append("<");
        for (int i = 0; i < params.length; i++) {
          PsiType t = psiSubst.substitute(params[i]);

          if (t == null) {
            goodSubst = false;
            break;
          }

          generateType(subst, t, context);

          if (i < params.length - 1) {
            subst.append(", ");
          }
        }

        if (goodSubst) {
          subst.append(">");
          String text = subst.toString();

          buffer.append(text);
        }
      }
    }
  }

  private static void generateInitializer(StringBuffer buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      String text = initializer.getText().trim();
      int index1 = text.indexOf('\n');
      if (index1 < 0) index1 = text.length();
      int index2 = text.indexOf('\r');
      if (index2 < 0) index2 = text.length();
      int index = Math.min(index1, index2);
      boolean trunc = index < text.length();
      text = text.substring(0, index);
      buffer.append(" = ");
      buffer.append(text);
      if (trunc) {
        buffer.append("...");
      }
    }
  }

  private static void generateModifiers(StringBuffer buffer, PsiElement element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);

    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static String generatePackageInfo(PsiPackage aPackage) {
    return aPackage.getQualifiedName();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateClassInfo(PsiClass aClass) {
    StringBuffer buffer = new StringBuffer();

    if (aClass instanceof PsiAnonymousClass) return LangBundle.message("java.terms.anonymous.class");

    PsiFile file = aClass.getContainingFile();
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module != null) {
      buffer.append('[').append(module.getName()).append("] ");
    }

    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile) file).getPackageName();
      if (packageName.length() > 0) {
        buffer.append(packageName);
        newLine(buffer);
      }
    }

    generateModifiers(buffer, aClass);

    final String classString =
      aClass.isInterface() ? "java.terms.interface" :
      aClass instanceof PsiTypeParameter ? "java.terms.type.parameter" :
      aClass.isEnum() ? "java.terms.enum" : "java.terms.class";
    buffer.append(LangBundle.message(classString) + " ");

    buffer.append(JavaDocUtil.getShortestClassName(aClass, aClass));

    if (aClass.hasTypeParameters()) {
      PsiTypeParameter[] parms = aClass.getTypeParameters();

      buffer.append("<");

      for (int i = 0; i < parms.length; i++) {
        PsiTypeParameter p = parms[i];

        buffer.append(p.getName());

        PsiClassType[] refs = p.getExtendsList().getReferencedTypes();

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            generateType(buffer, refs[j], aClass);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append(">");
    }

    PsiClassType[] refs;
    if (!aClass.isEnum() && !aClass.isAnnotationType()) {
      PsiReferenceList extendsList = aClass.getExtendsList();
      refs = extendsList == null ? PsiClassType.EMPTY_ARRAY : extendsList.getReferencedTypes();
      if (refs.length > 0 || !aClass.isInterface() && !"java.lang.Object".equals(aClass.getQualifiedName())) {
        buffer.append(" extends ");
        if (refs.length == 0) {
          buffer.append("Object");
        } else {
          for (int i = 0; i < refs.length; i++) {
            generateType(buffer, refs[i], aClass);

            if (i < refs.length - 1) {
              buffer.append(", ");
            }
          }
        }
      }
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append("implements ");
      for (int i = 0; i < refs.length; i++) {
        generateType(buffer, refs[i], aClass);

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateMethodInfo(PsiMethod method) {
    StringBuffer buffer = new StringBuffer();

    PsiClass parentClass = method.getContainingClass();

    if (parentClass != null) {
      buffer.append(JavaDocUtil.getShortestClassName(parentClass, method));
      newLine(buffer);
    }

    generateModifiers(buffer, method);

    PsiTypeParameter[] params = method.getTypeParameters();

    if (params.length > 0) {
      buffer.append("<");
      for (int i = 0; i < params.length; i++) {
        PsiTypeParameter param = params[i];

        buffer.append(param.getName());

        PsiClassType[] extendees = param.getExtendsList().getReferencedTypes();

        if (extendees.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < extendees.length; j++) {
            generateType(buffer, extendees[j], method);

            if (j < extendees.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < params.length - 1) {
          buffer.append(", ");
        }
      }
      buffer.append("> ");
    }

    if (method.getReturnType() != null) {
      generateType(buffer, method.getReturnType(), method);
      buffer.append(" ");
    }

    buffer.append(method.getName());

    buffer.append(" (");
    PsiParameter[] parms = method.getParameterList().getParameters();
    for (int i = 0; i < parms.length; i++) {
      PsiParameter parm = parms[i];
      generateType(buffer, parm.getType(), method);
      buffer.append(" ");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(", ");
      }
    }

    buffer.append(")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append(" throws ");
      for (int i = 0; i < refs.length; i++) {
        PsiClass throwsClass = refs[i].resolve();

        if (throwsClass != null) {
          buffer.append(JavaDocUtil.getShortestClassName(throwsClass, method));
        } else {
          buffer.append(refs[i].getPresentableText());
        }

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  private static String generateFieldInfo(PsiField field) {
    StringBuffer buffer = new StringBuffer();
    PsiClass parentClass = field.getContainingClass();

    if (parentClass != null) {
      buffer.append(JavaDocUtil.getShortestClassName(parentClass, field));
      newLine(buffer);
    }

    generateModifiers(buffer, field);

    generateType(buffer, field.getType(), field);
    buffer.append(" ");
    buffer.append(field.getName());

    generateInitializer(buffer, field);

    return buffer.toString();
  }

  private static String generateVariableInfo(PsiVariable variable) {
    StringBuffer buffer = new StringBuffer();

    generateModifiers(buffer, variable);

    generateType(buffer, variable.getType(), variable);

    buffer.append(" ");

    buffer.append(variable.getName());
    generateInitializer(buffer, variable);

    return buffer.toString();
  }
}
