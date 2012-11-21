/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.uiDesigner.compiler;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/20/12
 */
public abstract class FormsBuilder extends ModuleLevelBuilder {
  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.uiDesigner.compiler.FormsInstrumenter");
  protected static final String JAVA_EXTENSION = ".java";
  protected static final String FORM_EXTENSION = ".form";
  protected static final FileFilter JAVA_SOURCES_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        return file.getPath().endsWith(JAVA_EXTENSION);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        return StringUtil.endsWithIgnoreCase(file.getPath(), JAVA_EXTENSION);
      }
    };

  protected static final FileFilter FORM_SOURCES_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        return file.getPath().endsWith(FORM_EXTENSION);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        return StringUtil.endsWithIgnoreCase(file.getPath(), FORM_EXTENSION);
      }
    }
    ;
  protected static final Key<Set<File>> ADDITIONAL_FORMS_TO_REBUILD = Key.create("_forced_forms_to_rebuild_");

  private final String myBuilderName;

  public FormsBuilder(BuilderCategory category, String name) {
    super(category);
    myBuilderName = name;
  }

  @Override
  public boolean shouldHonorFileEncodingForCompilation(File file) {
    return FORM_SOURCES_FILTER.accept(file);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return myBuilderName;
  }

}
