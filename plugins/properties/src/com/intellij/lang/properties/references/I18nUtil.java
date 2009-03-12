package com.intellij.lang.properties.references;

import com.intellij.lang.properties.PropertiesFilesManager;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class I18nUtil {
  @NotNull
  public static List<PropertiesFile> propertiesFilesByBundleName(final String resourceBundleName, final PsiElement context) {
    PsiFile containingFile = context.getContainingFile();
    PsiElement containingFileContext = containingFile.getContext();
    if (containingFileContext != null) containingFile = containingFileContext.getContainingFile();
    
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) {
      final PsiFile originalFile = containingFile.getOriginalFile();
      if (originalFile != null) {
        virtualFile = originalFile.getVirtualFile();
      }
    }
    if (virtualFile != null) {
      final Module module = ProjectRootManager.getInstance(context.getProject()).getFileIndex().getModuleForFile(virtualFile);
      if (module != null) {
        PropertiesReferenceManager refManager = context.getProject().getComponent(PropertiesReferenceManager.class);
        return refManager.findPropertiesFiles(module, resourceBundleName);
      }
    }
    return Collections.emptyList();
  }

  public static void createProperty(final Project project,
                                    final Collection<PropertiesFile> propertiesFiles,
                                    final String key,
                                    final String value) throws IncorrectOperationException {
    Property property = PropertiesElementFactory.createProperty(project, key, value);
    for (PropertiesFile file : propertiesFiles) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      documentManager.commitDocument(documentManager.getDocument(file));

      Property existingProperty = file.findPropertyByKey(property.getUnescapedKey());
      if (existingProperty == null) {
        file.addProperty(property);
      }
    }
  }

  public static List<String> defaultGetPropertyFiles(Project project) {
    Collection<VirtualFile> allPropertiesFiles = PropertiesFilesManager.getInstance().getAllPropertiesFiles();
    List<String> paths = new ArrayList<String>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (VirtualFile virtualFile : allPropertiesFiles) {
      if (projectFileIndex.isInContent(virtualFile)) {
        String path = FileUtil.toSystemDependentName(virtualFile.getPath());
        paths.add(path);
      }
    }
    return paths;
  }

  /**
   * Returns number of different parameters in i18n message. For example, for string
   * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i>
   * number of parameters is 3.
   *
   * @param value i18n literal
   * @return number of parameters
   */
  public static int getPropertyValueParamsMaxCount(final PsiLiteralExpression expression) {
    int maxCount = -1;
    for (PsiReference reference : expression.getReferences()) {
      if (reference instanceof PsiPolyVariantReference) {
        for (ResolveResult result : ((PsiPolyVariantReference)reference).multiResolve(false)) {
          if (result.isValidResult() && result.getElement() instanceof Property) {
            String value = ((Property)result.getElement()).getValue();
            try {
              int count = new MessageFormat(value, null).getFormatsByArgumentIndex().length;
              maxCount = Math.max(maxCount, count);
            }
            catch (IllegalArgumentException ignored) {
            }
          }
        }
      }
    }
    return maxCount;
  }
}
