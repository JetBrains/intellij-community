package com.intellij.usageView;

import com.intellij.ant.PsiAntElement;
import com.intellij.aspects.psi.PsiAdvice;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.aspects.psi.gen.PsiErrorIntroduction;
import com.intellij.aspects.psi.gen.PsiVerificationIntroduction;
import com.intellij.aspects.psi.gen.PsiWarningIntroduction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import gnu.trove.THashSet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 *
 */
public class UsageViewUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usageView.UsageViewUtil");
  public static final String DEFAULT_PACKAGE_NAME = "<default>";

  public interface UsageViewHandler {
    String getType(PsiElement element);

    String getDescriptiveName(PsiElement element);

    String getNodeText(PsiElement element, boolean useFullName);
  }

  private static final HashMap<FileType, UsageViewHandler> usageViewHandlers = new HashMap<FileType, UsageViewHandler>();

  public static String createNodeText(PsiElement element, boolean useFullName) {
    if (element instanceof PsiDirectory) {
      return getPackageName((PsiDirectory)element, false);
    }
    if (element instanceof PsiPackage) {
      return getPackageName((PsiPackage)element);
    }
    if (element instanceof PsiFile) {
      return useFullName ? ((PsiFile)element).getVirtualFile().getPresentableUrl() : ((PsiFile)element).getName();
    }
    if (element instanceof PsiFile) {
      return useFullName ? ((PsiFile)element).getVirtualFile().getPresentableUrl() : ((PsiFile)element).getName();
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return ThrowSearchUtil.getSearchableTypeName(element);
    }

    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getQualifiedName();
      if (name == null || !useFullName) {
        name = ((PsiClass)element).getName();
      }
      return name;
    }
    if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (useFullName) {
        String s = PsiFormatUtil.formatMethod((PsiMethod)element,
                                              PsiSubstitutor.EMPTY, PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE |
                                                                    PsiFormatUtil.SHOW_NAME |
                                                                    PsiFormatUtil.SHOW_PARAMETERS,
                                              PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME);
        final PsiClass psiClass = psiMethod.getContainingClass();
        if (psiClass != null) {
          final String qName = psiClass.getQualifiedName();
          if (qName != null) {
            s = s + " of " + (psiClass.isInterface() ? "interface " : "class ") + qName;
          }
        }
        return s;
      }
      else {
        return PsiFormatUtil.formatMethod(psiMethod,
                                          PsiSubstitutor.EMPTY,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                          PsiFormatUtil.SHOW_TYPE);
      }
    }
    else if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)((PsiParameter)element).getDeclarationScope();
      String s = PsiFormatUtil.formatVariable((PsiVariable)element,
                                              PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME,
                                              PsiSubstitutor.EMPTY) + " of " +
                 PsiFormatUtil.formatMethod(method,
                                            PsiSubstitutor.EMPTY,
                                            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                            PsiFormatUtil.SHOW_TYPE);

      final PsiClass psiClass = method.getContainingClass();
      if (psiClass != null && psiClass.getQualifiedName() != null) {
        s += " of " + (psiClass.isInterface() ? "interface " : "class ") + psiClass.getQualifiedName();
      }
      return s;
    }
    else if (element instanceof PsiPointcutDef) {
      final PsiPointcutDef psiPointcutDef = (PsiPointcutDef)element;
      String s;
      if (useFullName) {
        s = PsiFormatUtil.formatPointcut(psiPointcutDef,
                                         PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                         PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME);
        PsiClass psiAspect = psiPointcutDef.getContainingClass();
        if (psiAspect != null) {
          String qName = psiAspect.getQualifiedName();
          if (qName != null) {
            s = s + " of aspect " + qName;
          }
        }
      }
      else {
        s = PsiFormatUtil.formatPointcut(psiPointcutDef, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
      }
      return s;
    }
    else if (element instanceof PsiField) {
      PsiField psiField = (PsiField)element;
      String s = PsiFormatUtil.formatVariable(psiField,
                                              PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME,
                                              PsiSubstitutor.EMPTY);
      PsiClass psiClass = psiField.getContainingClass();
      if (psiClass != null) {
        String qName = psiClass.getQualifiedName();
        if (qName != null) {
          s = s + " of " + (psiClass.isInterface() ? "interface " : "class ") + qName;
        }
      }
      return s;
    }
    else if (element instanceof PsiVariable) {
      return PsiFormatUtil.formatVariable((PsiVariable)element,
                                          PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME,
                                          PsiSubstitutor.EMPTY);
    }
    else if (element instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)element;
      return "<" + xmlTag.getName() + "> of file " + xmlTag.getContainingFile().getName();
    }
    else if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    else if (element != null) {
      PsiFile containingFile = element.getContainingFile();
      UsageViewHandler handler = (containingFile != null) ? usageViewHandlers.get(containingFile.getFileType()) : null;
      if (handler != null) {
        return handler.getNodeText(element, useFullName);
      }
    }

    return "";
  }

  public static void registerUsageViewHandler(FileType fileType, UsageViewHandler handler) {
    usageViewHandlers.put(fileType, handler);
  }

  public static String getPackageName(PsiDirectory directory, boolean includeRootDir) {
    PsiPackage aPackage = directory.getPackage();
    if (aPackage == null) {
      return directory.getVirtualFile().getPresentableUrl();
    }
    else {
      String packageName = getPackageName(aPackage);
      if (includeRootDir) {
        String rootDir = getRootDirectoryForPackage(directory);
        if (rootDir != null) {
          return packageName + " (in " + rootDir + ")";
        }
      }
      return packageName;
    }
  }

  public static String getRootDirectoryForPackage(PsiDirectory directory) {
    PsiManager manager = directory.getManager();
    final VirtualFile virtualFile = directory.getVirtualFile();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(manager.getProject()).getFileIndex();
    VirtualFile root = fileIndex.getSourceRootForFile(virtualFile);

    if (root == null) {
      root = fileIndex.getClassRootForFile(virtualFile);
    }
    if (root != null) {
      return root.getPresentableUrl();
    }
    else {
      return null;
    }
  }

  private static String getPackageName(PsiPackage psiPackage) {
    if (psiPackage == null) {
      return null;
    }
    String name = psiPackage.getQualifiedName();
    if (name.length() > 0) {
      return name;
    }
    else {
      return DEFAULT_PACKAGE_NAME;
    }
  }

  public static String capitalize(String s) {
    if (s == null || s.length() == 0) {
      return s;
    }
    char c = Character.toUpperCase(s.charAt(0));
    if (s.length() == 1) {
      return "" + c;
    }
    else {
      return "" + c + s.substring(1);
    }
  }

  public static String getShortName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret = "";
    if (psiElement instanceof PsiNamedElement) {
      ret = ((PsiNamedElement)psiElement).getName();
    }
    else if (psiElement instanceof PsiThrowStatement) {
      ret = "Exception";
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    else if (psiElement instanceof PsiVerificationIntroduction) {
      PsiLiteralExpression message = ((PsiVerificationIntroduction)psiElement).getMessage();
      ret = message == null ? "<no message>" : (String)message.getValue();
    }
    else if (psiElement instanceof PsiAdvice) {
      ret = ((PsiAdvice)psiElement).getPointcut().getText();
    }
    return ret;
  }

  public static String getLongName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret;
    if (psiElement instanceof PsiDirectory) {
      PsiPackage aPackage = ((PsiDirectory)psiElement).getPackage();
      if (aPackage != null) {
        ret = aPackage.getQualifiedName();
      }
      else {
        ret = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
      }
    }
    else if (psiElement instanceof PsiPackage) {
      ret = ((PsiPackage)psiElement).getQualifiedName();
    }
    else if (psiElement instanceof PsiClass) {
      if (psiElement instanceof PsiAnonymousClass) {
        ret = "anonymous class";
      }
      else {
        ret = ((PsiClass)psiElement).getQualifiedName(); // It happens for local classes
        if (ret == null) {
          ret = ((PsiClass)psiElement).getName();
        }
      }
    }
    else if (psiElement instanceof PsiVariable) {
      ret = ((PsiVariable)psiElement).getName();
    }
    else if (psiElement instanceof XmlTag) {
      ret = ((XmlTag)psiElement).getName();
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    else if (psiElement instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)psiElement;
      ret =
      PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY,
                                 PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    }
    else if (psiElement instanceof PsiPointcutDef) {
      PsiPointcutDef pointcutDef = (PsiPointcutDef)psiElement;
      ret =
      PsiFormatUtil.formatPointcut(pointcutDef, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                   PsiFormatUtil.SHOW_TYPE);
    }
    else if (psiElement instanceof PsiVerificationIntroduction) {
      PsiErrorIntroduction introduction = (PsiErrorIntroduction)psiElement;
      PsiLiteralExpression message = introduction.getMessage();
      ret = message == null ? "<no message>" : (String)message.getValue();
    }
    else if (psiElement instanceof PsiAdvice) {
      ret = ((PsiAdvice)psiElement).getPointcut().getText();
    }
    else {
      ret = "";
    }
    return ret;
  }

  public static String getType(PsiElement psiElement) {
    if (psiElement instanceof PsiDirectory) {
      return "directory";
    }
    if (psiElement instanceof PsiFile) {
      return "file";
    }
    if (ThrowSearchUtil.isSearchable(psiElement)) {
      return "exception";
    }
    if (psiElement instanceof PsiPackage) {
      return "package";
    }
    if (psiElement instanceof PsiClass) {
      if (((PsiClass)psiElement).isAnnotationType()) {
        return "@interface";
      }
      else if (((PsiClass)psiElement).isInterface()) {
        return "interface";
      }
      return "class";
    }
    if (psiElement instanceof PsiField) {
      return "field";
    }
    if (psiElement instanceof PsiParameter) {
      return "parameter";
    }
    if (psiElement instanceof PsiLocalVariable) {
      return "variable";
    }
    if (psiElement instanceof XmlTag) {
      return "XML tag";
    }
    if (psiElement instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)psiElement;
      final boolean isConstructor = psiMethod.isConstructor();
      if (isConstructor) {
        return "constructor";
      }
      else {
        return "method";
      }
    }
    if (psiElement instanceof PsiPointcutDef) {
      return "pointcut";
    }
    if (psiElement instanceof PsiErrorIntroduction) {
      return "error declaration";
    }
    if (psiElement instanceof PsiWarningIntroduction) {
      return "warning declaration";
    }
    if (psiElement instanceof PsiAdvice) {
      return "advice";
    }
    if (psiElement instanceof PsiLabeledStatement) {
      return "label";
    }
    if (psiElement instanceof PsiAntElement) {
      return ((PsiAntElement)psiElement).getRole().getName();
    }

    PsiFile containingFile = psiElement.getContainingFile();
    UsageViewHandler handler = (containingFile!=null)?usageViewHandlers.get(containingFile.getFileType()):null;
    if (handler != null) {
      return handler.getType(psiElement);
    }

    return "";
  }

  public static String getDescriptiveName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret = "";
    if (ThrowSearchUtil.isSearchable(psiElement)) {
      ret = ThrowSearchUtil.getSearchableTypeName(psiElement);
    }
    if (psiElement instanceof PsiDirectory) {
      ret = getPackageName((PsiDirectory)psiElement, false);
    }
    else if (psiElement instanceof PsiPackage) {
      ret = getPackageName((PsiPackage)psiElement);
    }
    else if (psiElement instanceof PsiFile) {
      ret = ((PsiFile)psiElement).getVirtualFile().getPresentableUrl();
    }
    else if (psiElement instanceof PsiClass) {
      if (psiElement instanceof PsiAnonymousClass) {
        ret = "anonymous class";
      }
      else {
        ret = ((PsiClass)psiElement).getQualifiedName();
        if (ret == null) {
          ret = ((PsiClass)psiElement).getName();
        }
      }
    }
    else if (psiElement instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)psiElement;
      ret = PsiFormatUtil.formatMethod(psiMethod,
                                       PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                       PsiFormatUtil.SHOW_TYPE);
      PsiClass psiClass = psiMethod.getContainingClass();
      if (psiClass != null) {
        if (psiClass instanceof PsiAnonymousClass) {
          ret = ret + " of anonymous class";
        }
        else {
          String className = psiClass.getName();
          if (!psiClass.isInterface()) {
            ret = ret + " of class " + className;
          }
          else if (psiClass.isInterface()) {
            ret = ret + " of interface " + className;
          }
        }
      }
    }
    else if (psiElement instanceof PsiField) {
      PsiField psiField = (PsiField)psiElement;
      ret = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY);
      PsiClass psiClass = psiField.getContainingClass();
      if (psiClass != null) {
        if (psiClass instanceof PsiAnonymousClass) {
          ret = ret + " of anonymous class";
        }
        else {
          String className = psiClass.getName();
          if (!psiClass.isInterface()) {
            ret = ret + " of class " + className;
          }
          else if (psiClass.isInterface()) {
            ret = ret + " of interface " + className;
          }
        }
      }
    }
    else if (psiElement instanceof PsiVariable) {
      PsiVariable psiVariable = (PsiVariable)psiElement;
      ret = PsiFormatUtil.formatVariable(psiVariable, PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY);
    }
    else if (psiElement instanceof PsiPointcutDef) {
      PsiPointcutDef psiPointcut = (PsiPointcutDef)psiElement;
      ret = PsiFormatUtil.formatPointcut(psiPointcut,
                                         PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                         PsiFormatUtil.SHOW_TYPE);
      PsiClass psiClass = psiPointcut.getContainingClass();
      if (psiClass != null) {
        ret = ret + " of aspect " + psiClass.getName();
      }
    }
    else if (psiElement instanceof PsiErrorIntroduction) {
      PsiErrorIntroduction introduction = (PsiErrorIntroduction)psiElement;
      PsiLiteralExpression message = introduction.getMessage();
      ret = "error \"" + (message == null ? "<no message>" : message.getValue()) + "\"";
      PsiFile psiFile = introduction.getContainingFile();
      if (psiFile != null) {
        ret = ret + " in file " + psiFile.getName();
      }
    }
    else if (psiElement instanceof PsiWarningIntroduction) {
      PsiWarningIntroduction introduction = (PsiWarningIntroduction)psiElement;
      PsiLiteralExpression message = introduction.getMessage();
      ret = "warning \"" + (message == null ? "<no message>" : message.getValue()) + "\"";
      PsiFile psiFile = introduction.getContainingFile();
      if (psiFile != null) {
        ret = ret + " in file " + psiFile.getName();
      }
    }
    else if (psiElement instanceof XmlTag) {
      ret = ((XmlTag)psiElement).getName();
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    else if (psiElement instanceof PsiLabeledStatement) {
      ret = ((PsiLabeledStatement)psiElement).getName();
    }
    else {
      PsiFile containingFile = psiElement.getContainingFile();
      UsageViewHandler handler = (containingFile != null) ? usageViewHandlers.get(containingFile.getFileType()) : null;
      if (handler != null) {
        return handler.getDescriptiveName(psiElement);
      }
    }

    return ret;
  }

  public static String getUsageCountInfo(int usagesCount, int filesCount, String referenceWord) {
    String info;
    if (filesCount > 0) {
      String files = filesCount != 1 ? " files " : " file ";
      if (usagesCount > 1) {
        referenceWord += "s";
      }
      info = "( " + usagesCount + " " + referenceWord + " in " + filesCount + files + ")";
    }
    else {
      info = "( Not found )";
    }
    return info;
  }

  public static boolean hasNonCodeUsages(UsageInfo[] usages) {
    for (int i = 0; i < usages.length; i++) {
      if (usages[i].isNonCodeUsage) return true;
    }
    return false;
  }

  public static boolean hasReadOnlyUsages(UsageInfo[] usages) {
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (!usage.isWritable()) return true;
    }
    return false;
  }

  public static UsageInfo[] removeDuplicatedUsages(UsageInfo usages[]) {
    Set<UsageInfo> set = new THashSet<UsageInfo>(Arrays.asList(usages));
    return set.toArray(new UsageInfo[set.size()]);
  }
}