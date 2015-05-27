/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Dmitry Batkovich
 */
public interface ResourceBundlePropertiesUpdateManager {
  void insertNewProperty(String key, String value);

  void insertOrUpdateTranslation(String key, String value, PropertiesFile propertiesFile) throws IncorrectOperationException;

  void reload();

  class Stub implements ResourceBundlePropertiesUpdateManager {
    public static final ResourceBundlePropertiesUpdateManager INSTANCE = new Stub();

    @Override
    public void insertNewProperty(String key, String value) {
    }

    @Override
    public void insertOrUpdateTranslation(String key, String value, PropertiesFile propertiesFile) {
    }

    @Override
    public void reload() {
    }
  }
}
