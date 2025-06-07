// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.restructuredtext.validation.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * User : catherine
 */
public final class RestLanguage extends Language implements TemplateLanguage  {
  public static final RestLanguage INSTANCE = new RestLanguage();
  private final Set<Class<? extends RestAnnotator>> _annotators = new CopyOnWriteArraySet<>();
  private RestLanguage() {
    super("ReST");
  }

  @Override
  public @NotNull String getDisplayName() {
    return RestBundle.message("rest.language");
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
