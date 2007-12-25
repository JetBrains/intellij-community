package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author cdr
 */
public class PropertiesUtil {
  private PropertiesUtil() {
  }

  @NotNull
  public static List<Property> findPropertiesByKey(Project project, final String key) {
    return PropertiesReferenceManager.getInstance(project).findPropertiesByKey(key);
  }

  public static boolean isPropertyComplete(final Project project, ResourceBundle resourceBundle, String propertyName) {
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(project);
    for (PropertiesFile propertiesFile : propertiesFiles) {
      if (propertiesFile.findPropertyByKey(propertyName) == null) return false;
    }
    return true;
  }

  @NotNull
  public static String getBaseName(@NotNull VirtualFile virtualFile) {
    String name = virtualFile.getNameWithoutExtension();

    String[] parts = name.split("_");
    if (parts.length == 1) return parts[0];

    String baseName = parts[0];
    for (int i = 1; i<parts.length; i++) {
      String part = parts[i];
      if (part.length() == 2) {
        break;
      }
      baseName += "_";
      baseName += part;
    }

    return baseName;
  }

  /**
   * messages_en.properties is a parent of the messages_en_US.properties
   */
  @Nullable
  public static PropertiesFile getParent(PropertiesFile file, List<PropertiesFile> candidates) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    String name = virtualFile.getNameWithoutExtension();
    String[] parts = name.split("_");
    if (parts.length == 1) return null;
    List<String> partsList = Arrays.asList(parts);
    for (int i=parts.length-1; i>=1;i--) {
      String parentName = StringUtil.join(partsList.subList(0, i), "_") + "." + virtualFile.getExtension();
      for (PropertiesFile candidate : candidates) {
        if (parentName.equals(candidate.getName())) return candidate;
      }
    }
    return null;
  }

  @Nullable
  public static String getFullName(final PropertiesFile psiFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        PsiDirectory directory = psiFile.getParent();
        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
        if (pkg == null) {
          return null;
        }
        StringBuilder qName = new StringBuilder(pkg.getQualifiedName());
          if (qName.length() > 0) {
            qName.append(".");
          }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        assert virtualFile != null;
        qName.append(getBaseName(virtualFile));
        return qName.toString();
      }
    });
  }

  @NotNull
  public static Locale getLocale(VirtualFile propertiesFile) {
    String name = propertiesFile.getNameWithoutExtension();
    String tail = StringUtil.trimStart(name, getBaseName(propertiesFile));
    tail = StringUtil.trimStart(tail, "_");
    String[] parts = tail.split("_");
    String language = parts.length == 0 ? "" : parts[0];
    String country = "";
    String variant = "";
    if (parts.length >= 2 && parts[1].length() == 2) {
      country = parts[1];
      for (int i = 2; i < parts.length; i++) {
        String part = parts[i];
        if (variant.length() != 0) variant += "_";
        variant += part;
      }
    }

    return new Locale(language,country,variant);
  }

  @NotNull
  public static List<Property> findAllProperties(Project project, ResourceBundle resourceBundle, String key) {
    List<Property> result = new SmartList<Property>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(project);
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public static boolean isUnescapedBackSlashAtTheEnd (String text) {
    boolean result = false;
    for (int i = text.length()-1; i>=0; i--) {
      if (text.charAt(i) == '\\') {
        result = !result;
      }
      else {
        break;
      }
    }
    return result;
  }

  /**
   * @deprecated Use getPropertiesFile() with specified locale instead
   * @param bundleName
   * @param searchFromModule
   * @return
   */
  @Nullable public static PropertiesFile getPropertiesFile(final String bundleName, final Module searchFromModule) {
    @NonNls final String fileName = bundleName + ".properties";
    VirtualFile vFile = ResourceFileUtil.findResourceFileInDependents(searchFromModule, fileName);
    if (vFile != null) {
      PsiFile psiFile = PsiManager.getInstance(searchFromModule.getProject()).findFile(vFile);
      if (psiFile instanceof PropertiesFile) return (PropertiesFile) psiFile;
    }
    return null;    
  }

  @Nullable
  public static PropertiesFile getPropertiesFile(@NotNull String bundleName,
                                                 @NotNull Module searchFromModule,
                                                 @Nullable Locale locale) {
    PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(searchFromModule.getProject());
    return manager.findPropertiesFile(searchFromModule, bundleName, locale);
  }
}
