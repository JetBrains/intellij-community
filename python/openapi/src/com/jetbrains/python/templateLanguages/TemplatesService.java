/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.packaging.PyPackageManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: catherine
 */
public abstract class TemplatesService {
  public static final String NONE = "None";
  /**
   * @deprecated Use {@link #getKnownTemplateLanguages()}
   * or {@link com.jetbrains.python.templateLanguages.PythonTemplateLanguage#getTemplateLanguageName()}
   */
  @Deprecated
  public static final String DJANGO = "Django";
  /**
   * @deprecated Use {@link #getKnownTemplateLanguages()}
   * or {@link com.jetbrains.python.templateLanguages.PythonTemplateLanguage#getTemplateLanguageName()}
   */
  @Deprecated
  public static final String MAKO = "Mako";
  /**
   * @deprecated Use {@link #getKnownTemplateLanguages()}
   * or {@link com.jetbrains.python.templateLanguages.PythonTemplateLanguage#getTemplateLanguageName()}
   */
  @Deprecated
  public static final String JINJA2 = "Jinja2";
  /**
   * @deprecated Use {@link #getKnownTemplateLanguages()}
   * or {@link com.jetbrains.python.templateLanguages.PythonTemplateLanguage#getTemplateLanguageName()}
   */
  @Deprecated
  public static final String WEB2PY = "Web2Py";
  /**
   * @deprecated Use {@link #getKnownTemplateLanguages()}
   * or {@link com.jetbrains.python.templateLanguages.PythonTemplateLanguage#getTemplateLanguageName()}
   */
  @Deprecated
  public static final String CHAMELEON = "Chameleon";

  /**
   * @deprecated Use {@link #getKnownTemplateLanguages()}
   * or {@link com.jetbrains.python.templateLanguages.PythonTemplateLanguage#getTemplateLanguageName()}
   */
  @Deprecated
  private static List<String> ALL_TEMPLATE_LANGUAGES = ContainerUtil.immutableList(NONE,
                                                                                   DJANGO,
                                                                                   MAKO,
                                                                                  JINJA2,
                                                                                  WEB2PY,
                                                                                  CHAMELEON);

  public static List<String> ALL_TEMPLATE_BINDINGS = ContainerUtil.immutableList("django-mako", "django-jinja", "django-chameleon",
                                                                                  "flask-mako", "pyramid_jinja2");

  @Nullable
  public abstract Language getSelectedTemplateLanguage();

  public static TemplatesService getInstance(Module module) {
    return ModuleServiceManager.getService(module, TemplatesService.class);
  }

  /**
   * @deprecated Use {@link #getKnownTemplateLanguages()}
   * or {@link com.jetbrains.python.templateLanguages.PythonTemplateLanguage#getTemplateLanguageName()}
   */
  @Deprecated
  public static List<String> getAllTemplateLanguages() {
    return ALL_TEMPLATE_LANGUAGES;
  }

  public abstract List<PythonTemplateLanguage> getKnownTemplateLanguages();

  public abstract void setTemplateLanguage(String templateLanguage);

  @NotNull
  public abstract List<VirtualFile> getTemplateFolders();

  public abstract void setTemplateFolders(VirtualFile... roots);

  public abstract void setTemplateFolderPaths(String... paths);

  public abstract String getTemplateLanguage();

  public abstract List<String> getTemplateFileTypes();
  public abstract void setTemplateFileTypes(List<String> fileTypes);

  public abstract void generateTemplates(@NotNull final TemplateSettingsHolder settings, VirtualFile baseDir);
  public abstract void installTemplateEngine(@NotNull final TemplateSettingsHolder settings, @NotNull final PyPackageManager packageManager,
                                             @NotNull final Project project, @NotNull final String prefix);

  public abstract void addLanguageSelectedListener(Runnable listener);
  public abstract void removeLanguageSelectedListener(Runnable listener);
}

