package com.jetbrains.python.templateLanguages;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 */
@State(name = "TemplatesService",
      storages = {
      @Storage( file = "$MODULE_FILE$")
      }
)
public class TemplatesService implements PersistentStateComponent<TemplatesService> {
  public static final String NONE = "None";
  public static final String DJANGO = "Django";
  public static final String MAKO = "Mako";
  public static final String JINJA2 = "Jinja2";

  private static List<String> ALL_TEMPLATE_LANGUAGES = ContainerUtil.immutableList(NONE,
                                                                                   DJANGO,
                                                                                   MAKO,
                                                                                   JINJA2);

  public String TEMPLATE_CONFIGURATION = NONE;
  public boolean TEMPLATES_IN_JAVASCRIPT = false;
  public List<String> TEMPLATE_FOLDERS = new ArrayList<String>();
  private final List<VirtualFile> myTemplateFolders = new ArrayList<VirtualFile>();

  public static TemplatesService getInstance(Module module) {
    return ModuleServiceManager.getService(module, TemplatesService.class);
  }

  public static List<String> getAllTemplateLanguages() {
    return ALL_TEMPLATE_LANGUAGES;
  }

  @Override
  public TemplatesService getState() {
    return this;
  }

  @Override
  public void loadState(TemplatesService state) {
    XmlSerializerUtil.copyBean(state, this);
    locateTemplateFolders();
  }

  private void locateTemplateFolders() {
    myTemplateFolders.clear();
    for (String path : TEMPLATE_FOLDERS) {
      final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      if (virtualFile != null) {
        myTemplateFolders.add(virtualFile);
      }
    }
  }

  public void setTemplateLanguage(String templateLanguage) {
    TEMPLATE_CONFIGURATION = templateLanguage;
  }

  public List<VirtualFile> getTemplateFolders() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile templateFolder : myTemplateFolders) {
      if (templateFolder.isValid()) {
        result.add(templateFolder);
      }
    }
    return result;
  }

  public void setTemplateFolders(VirtualFile... roots) {
    myTemplateFolders.clear();
    Collections.addAll(myTemplateFolders, roots);
    TEMPLATE_FOLDERS.clear();
    for (VirtualFile root : roots) {
      TEMPLATE_FOLDERS.add(root.getPath());
    }
  }

  public void setTemplateFolderPaths(String... paths) {
    TEMPLATE_FOLDERS.clear();
    Collections.addAll(TEMPLATE_FOLDERS, paths);
    locateTemplateFolders();
  }

  public String getTemplateLanguage() {
    return TEMPLATE_CONFIGURATION;
  }
}
