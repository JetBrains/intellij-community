package com.intellij.usageView;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.ant.PsiAntElement;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import gnu.trove.THashSet;

import java.util.Arrays;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 *
 */
public class UsageViewUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usageView.UsageViewUtil");
  public static final String DEFAULT_PACKAGE_NAME = UsageViewBundle.message("default.package.presentable.name");

  private UsageViewUtil() { }

  public static String createNodeText(PsiElement element, boolean useFullName) {
    if (element instanceof PsiMetaBaseOwner) {
      final PsiMetaBaseOwner psiMetaBaseOwner = (PsiMetaBaseOwner)element;
      final PsiMetaDataBase metaData = psiMetaBaseOwner.getMetaData();
      if (metaData instanceof PsiPresentableMetaData) {
        return ((PsiPresentableMetaData)metaData).getTypeName() + " " + metaData.getName();
      }
    }

    if (element instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)element;
      final PsiMetaDataBase metaData = xmlTag.getMetaData();
      final String name = metaData != null ? metaData.getName() : xmlTag.getName();
      return UsageViewBundle.message("usage.target.xml.tag.of.file", ((metaData == null) ? "<" + name + ">" : name), xmlTag.getContainingFile().getName());
    }
    else if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    else if (element != null) {
      FindUsagesProvider provider = element.getLanguage().getFindUsagesProvider();
      return provider.getNodeText(element, useFullName);
    }

    return "";
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
          return UsageViewBundle.message("usage.target.package.in.directory", packageName, rootDir);
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

  public static String getPackageName(PsiPackage psiPackage) {
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
      return String.valueOf(c);
    }
    else {
      return String.valueOf(c) + s.substring(1);
    }
  }

  public static String getShortName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret = "";
    if (psiElement instanceof PsiMetaBaseOwner) {
      PsiMetaDataBase metaData = ((PsiMetaBaseOwner)psiElement).getMetaData();
      if (metaData!=null) return metaData.getName();
    }

    if (psiElement instanceof PsiNamedElement) {
      ret = ((PsiNamedElement)psiElement).getName();
    }
    else if (psiElement instanceof PsiThrowStatement) {
      ret = UsageViewBundle.message("usage.target.exception");
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
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
        ret = LangBundle.message("java.terms.anonymous.class");
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
      ret = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY,
                                 PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    }
    else {
      ret = "";
    }
    return ret;
  }

  public static String getType(PsiElement psiElement) {
    if (psiElement instanceof PsiMetaBaseOwner) {
      final PsiMetaDataBase metaData = ((PsiMetaBaseOwner)psiElement).getMetaData();
      if (metaData instanceof PsiPresentableMetaData) {
        return ((PsiPresentableMetaData)metaData).getTypeName();
      }
    }
    if (psiElement instanceof XmlTag) {
      final PsiMetaDataBase metaData = ((XmlTag)psiElement).getMetaData();
      if (metaData != null && metaData.getDeclaration() instanceof XmlTag) {
        return ((XmlTag)metaData.getDeclaration()).getName();
      }
      return LangBundle.message("xml.terms.xml.tag");
    }

    if (psiElement instanceof PsiAntElement) {
      return ((PsiAntElement)psiElement).getRole().getName();
    }
    if (psiElement instanceof PsiFile) {
      return LangBundle.message("terms.file");
    }
    if (psiElement instanceof PsiDirectory) {
      return LangBundle.message("terms.directory");
    }
    if (psiElement instanceof WebDirectoryElement) {
      return LangBundle.message("terms.web.directory");
    }

    final Language lang = psiElement.getLanguage();
    FindUsagesProvider provider = lang.getFindUsagesProvider();
    return provider.getType(psiElement);
  }

  public static String getDescriptiveName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());

    if (psiElement instanceof PsiMetaBaseOwner) {
      final PsiMetaBaseOwner psiMetaBaseOwner = (PsiMetaBaseOwner)psiElement;
      final PsiMetaDataBase metaData = psiMetaBaseOwner.getMetaData();
      if (metaData != null) return metaData.getName();
    }

    if (psiElement instanceof XmlTag) {
      return ((XmlTag)psiElement).getName();
    }

    if (psiElement instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)psiElement).getValue();
    }

    final Language lang = psiElement.getLanguage();
    FindUsagesProvider provider = lang.getFindUsagesProvider();
    return provider.getDescriptiveName(psiElement);
  }

  public static boolean hasNonCodeUsages(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) return true;
    }
    return false;
  }

  public static boolean hasReadOnlyUsages(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (!usage.isWritable()) return true;
    }
    return false;
  }

  public static UsageInfo[] removeDuplicatedUsages(UsageInfo[] usages) {
    Set<UsageInfo> set = new LinkedHashSet<UsageInfo>(Arrays.asList(usages));
    return set.toArray(new UsageInfo[set.size()]);
  }
}