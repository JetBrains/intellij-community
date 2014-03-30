/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.templateLanguages;

import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * User: catherine
 */
public abstract class TemplatesService {
  public static final String NONE = "None";
  public static final String DJANGO = "Django";
  public static final String MAKO = "Mako";
  public static final String JINJA2 = "Jinja2";
  public static final String WEB2PY = "Web2Py";
  public static final String CHAMELEON = "Chameleon";

  private static List<String> ALL_TEMPLATE_LANGUAGES = ContainerUtil.immutableList(NONE,
                                                                                   DJANGO,
                                                                                   MAKO,
                                                                                  JINJA2,
                                                                                  WEB2PY,
                                                                                  CHAMELEON);

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

