package com.intellij.psi.impl;

import com.intellij.ant.PsiAntElement;
import com.intellij.aspects.psi.PsiAdvice;
import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.aspects.psi.gen.PsiErrorIntroduction;
import com.intellij.aspects.psi.gen.PsiParentsIntroduction;
import com.intellij.aspects.psi.gen.PsiSofteningIntroduction;
import com.intellij.aspects.psi.gen.PsiWarningIntroduction;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.IconUtilEx;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;

import javax.swing.*;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  public Icon getIcon(int flags) {
    if (!(this instanceof PsiElement)) return null;
    return getIcon((PsiElement) this, flags, ((PsiElement) this).isWritable());
  }

  private static Icon getIcon(PsiElement element, int flags, boolean elementWritable) {
    RowIcon baseIcon;
    boolean showReadStatus = (flags & ICON_FLAG_READ_STATUS) != 0;
    if (element instanceof PsiDirectory) {
      Icon symbolIcon;
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      if (psiDirectory.getPackage() != null) {
        symbolIcon = Icons.PACKAGE_ICON;
      }
      else {
        symbolIcon = Icons.DIRECTORY_CLOSED_ICON;
      }
      boolean isExcluded = false;
      final VirtualFile vFile = psiDirectory.getVirtualFile();
      if (vFile != null) {
        final Project project = psiDirectory.getProject();
        if (ProjectRootManager.getInstance(project).getFileIndex().isInSource(vFile) && CompilerConfiguration.getInstance(project).isExcludedFromCompilation(vFile)) {
          isExcluded = true;
        }
      }
      baseIcon = createLockableExcludableIcon(symbolIcon, showReadStatus && !elementWritable, isExcluded);
    }
    else if (element instanceof PsiPackage) {
      baseIcon = createLockableIcon(Icons.PACKAGE_ICON, showReadStatus && !elementWritable);
    }
    else if (element instanceof PsiFile) {
      PsiFile file = (PsiFile)element;
      Icon symbolIcon = IconUtilEx.getIcon(file.getVirtualFile(), flags & ~ICON_FLAG_READ_STATUS, file.getProject());
      baseIcon = createLockableIcon(symbolIcon, showReadStatus && !elementWritable);
    }
    else if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      Icon symbolIcon = getClassIcon(aClass);
      boolean isExcluded = false;
      final PsiFile containingFile = aClass.getContainingFile();
      if (containingFile != null) {
        final VirtualFile vFile = containingFile.getVirtualFile();
        if (vFile != null) {
          final Project project = aClass.getProject();
          if (ProjectRootManager.getInstance(project).getFileIndex().isInSource(vFile) && CompilerConfiguration.getInstance(project).isExcludedFromCompilation(vFile)) {
            isExcluded = true;
          }
        }
      }
      baseIcon = createLockableExcludableIcon(symbolIcon, showReadStatus && !elementWritable, isExcluded);
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
      Icon methodIcon = role == null
                        ? method.hasModifierProperty(PsiModifier.STATIC) ? Icons.STATIC_METHOD_ICON : Icons.METHOD_ICON
                        : role.getIcon();
      baseIcon = createLockableIcon(methodIcon, false);
    }
    else if (element instanceof PsiField) {
      Icon fieldIcon = ((PsiField)element).hasModifierProperty(PsiModifier.STATIC) ? Icons.STATIC_FIELD_ICON : Icons.FIELD_ICON;
      baseIcon = createLockableIcon(fieldIcon, false);
    }
    else if (element instanceof PsiParameter) {
      baseIcon = createLockableIcon(Icons.PARAMETER_ICON, false);
    }
    else if (element instanceof PsiVariable) {
      baseIcon = createLockableIcon(Icons.VARIABLE_ICON, false);
    }
    else if (element instanceof PsiPointcutDef) {
      baseIcon = createLockableIcon(Icons.POINTCUT_ICON, false);
    }
    else if (element instanceof PsiParentsIntroduction) {
      baseIcon = createLockableIcon(Icons.PARENTS_INTRODUCTION_ICON, false);
    }
    else if (element instanceof PsiErrorIntroduction) {
      baseIcon = createLockableIcon(Icons.ERROR_INTRODUCTION_ICON, false);
    }
    else if (element instanceof PsiWarningIntroduction) {
      baseIcon = createLockableIcon(Icons.WARNING_INTRODUCTION_ICON, false);
    }
    else if (element instanceof PsiSofteningIntroduction) {
      baseIcon = createLockableIcon(Icons.SOFTENING_INTRODUCTION_ICON, false);
    }
    else if (element instanceof PsiAdvice) {
      baseIcon = createLockableIcon(Icons.ADVICE_ICON, false);
    }
    else if (element instanceof XmlTag) {
      return Icons.XML_TAG_ICON;
    }
    else if (element instanceof PsiAntElement) {
      return ((PsiAntElement)element).getRole().getIcon();
    }
    else {
      return null;
    }
    if ((flags & ICON_FLAG_VISIBILITY) != 0) {
      PsiModifierList modifierList = element instanceof PsiModifierListOwner ? ((PsiModifierListOwner)element).getModifierList() : null;
      IconUtilEx.setVisibilityIcon(modifierList, baseIcon);
    }
    return baseIcon;
  }

  private static Icon getClassIcon(final PsiClass aClass) {
    final EjbClassRole role = J2EERolesUtil.getEjbRole(aClass);
    if (role != null) return role.getIcon();
    if (aClass instanceof PsiAspect) {
      return Icons.ASPECT_ICON;
    }
    if (aClass.isAnnotationType()) {
      return Icons.ANNOTATION_TYPE_ICON;
    }
    if (aClass.isEnum()) {
      return Icons.ENUM_ICON;
    }
    if (aClass.isInterface()) {
      return aClass.hasModifierProperty(PsiModifier.STATIC)
      ? Icons.STATIC_INTERFACE_ICON
      : Icons.INTERFACE_ICON;
    }
    if (aClass instanceof JspClass) {
      return Icons.JSP_ICON;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
      return Icons.STATIC_CLASS_ICON;
    }

    if (aClass instanceof PsiAnonymousClass) {
      return Icons.ANONYMOUS_CLASS_ICON;
    }
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return Icons.ABSTRACT_CLASS_ICON;
    }

    final PsiManager manager = aClass.getManager();
    final PsiClass javaLangTrowable = manager.findClass("java.lang.Throwable", aClass.getResolveScope());
    final boolean isException = javaLangTrowable != null && InheritanceUtil.isInheritorOrSelf(aClass, javaLangTrowable, true);
    if (isException) {
      return Icons.EXCEPTION_CLASS_ICON;
    }

    final PsiClass testClass = manager.findClass("junit.framework.TestCase", aClass.getResolveScope());
    if (testClass != null && InheritanceUtil.isInheritorOrSelf(aClass, testClass, true)) return Icons.JUNIT_TEST_CLASS_ICON;

    return Icons.CLASS_ICON;
  }

  private static RowIcon createLockableIcon(Icon icon, boolean isLocked) {
    return createLockableExcludableIcon(icon, isLocked, false);
  }
  private static RowIcon createLockableExcludableIcon(Icon icon, boolean isLocked, boolean isExcluded) {
    if (isExcluded || isLocked) {
      LayeredIcon layeredIcon = new LayeredIcon(1 + (isLocked? 1 : 0) + (isExcluded? 1 : 0));
      int layer = 0;
      layeredIcon.setIcon(icon, layer++);
      if (isLocked) {
        layeredIcon.setIcon(Icons.LOCKED_ICON, layer++);
      }
      if (isExcluded) {
        layeredIcon.setIcon(Icons.EXCLUDED_FROM_COMPILE_ICON, layer);
      }
      icon = layeredIcon;
    }
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(icon, 0);
    return baseIcon;
  }
}