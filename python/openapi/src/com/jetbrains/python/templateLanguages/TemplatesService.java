package com.jetbrains.python.templateLanguages;

import com.google.common.collect.Lists;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 */
public abstract class TemplatesService {
  public static final String NONE = "None";
  public static final String DJANGO = "Django";
  public static final String MAKO = "Mako";
  public static final String JINJA2 = "Jinja2";

  private static List<String> ALL_TEMPLATE_LANGUAGES = ContainerUtil.immutableList(NONE,
                                                                                   DJANGO,
                                                                                   MAKO,
                                                                                  JINJA2);

  public abstract Language getSelectedTemplateLanguage();

  public static TemplatesService getInstance(Module module) {
    return ModuleServiceManager.getService(module, TemplatesService.class);
  }

  public static List<String> getAllTemplateLanguages() {
    return ALL_TEMPLATE_LANGUAGES;
  }

  public abstract void setTemplateLanguage(String templateLanguage);

  public abstract List<VirtualFile> getTemplateFolders();

  public abstract void setTemplateFolders(VirtualFile... roots);

  public abstract void setTemplateFolderPaths(String... paths);

  public abstract String getTemplateLanguage();

  public abstract List<String> getTemplateFileTypes();
  public abstract void setTemplateFileTypes(List<String> fileTypes);
}

