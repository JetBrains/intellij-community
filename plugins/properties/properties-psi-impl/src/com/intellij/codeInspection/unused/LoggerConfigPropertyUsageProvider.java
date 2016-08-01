/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.unused;

import com.intellij.lang.properties.psi.Property;

public class LoggerConfigPropertyUsageProvider extends ImplicitPropertyUsageProvider {
  private final static String[] LOGGER_PROPERTIES_KEYWORDS = new String[]{"log4j", "commons-logging", "logging"};

  @Override
  protected boolean isUsed(Property property) {
    final String propertiesFileName = property.getPropertiesFile().getName();
    for (String keyword : LOGGER_PROPERTIES_KEYWORDS) {
      if (propertiesFileName.startsWith(keyword)) {
        return true;
      }
    }
    return false;
  }
}
