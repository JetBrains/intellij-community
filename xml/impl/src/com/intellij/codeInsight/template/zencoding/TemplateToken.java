/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.codeInsight.template.impl.TemplateImpl;

/**
 * @author Eugene.Kudelevsky
 */
public class TemplateToken extends Token {
  private final String myKey;
  private TemplateImpl myTemplate;

  public TemplateToken(String key) {
    myKey = key;
  }

  public String getKey() {
    return myKey;
  }

  public void setTemplate(TemplateImpl template) {
    myTemplate = template;
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  @Override
  public String toString() {
    return "TEMPLATE";
  }
}
