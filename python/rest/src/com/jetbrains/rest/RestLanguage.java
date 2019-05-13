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
package com.jetbrains.rest;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.jetbrains.rest.validation.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * User : catherine
 */
public class RestLanguage extends Language implements TemplateLanguage  {
  public static final RestLanguage INSTANCE = new RestLanguage();
  private final Set<Class<? extends RestAnnotator>> _annotators = new CopyOnWriteArraySet<>();
  private RestLanguage() {
    super("ReST");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Rest language";
  }

  @Override
  public RestFileType getAssociatedFileType() {
    return RestFileType.INSTANCE;
  }
  {
    _annotators.add(RestHyperlinksAnnotator.class);
    _annotators.add(RestReferenceTargetAnnotator.class);
    _annotators.add(RestInlineBlockAnnotator.class);
    _annotators.add(RestTitleAnnotator.class);
  }

  public Set<Class<? extends RestAnnotator>> getAnnotators() {
    return _annotators;
  }
}
