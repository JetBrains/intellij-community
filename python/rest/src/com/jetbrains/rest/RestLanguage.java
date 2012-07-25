package com.jetbrains.rest;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.jetbrains.rest.validation.RestAnnotator;
import com.jetbrains.rest.validation.RestHyperlinksAnnotator;
import com.jetbrains.rest.validation.RestInlineBlockAnnotator;
import com.jetbrains.rest.validation.RestReferenceTargetAnnotator;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * User : catherine
 */
public class RestLanguage extends Language implements TemplateLanguage  {
  public static final RestLanguage INSTANCE = new RestLanguage();
  private final Set<Class<? extends RestAnnotator>> _annotators = new CopyOnWriteArraySet<Class<? extends RestAnnotator>>();
  private RestLanguage() {
    super("ReST");
  }

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
  }

  public Set<Class<? extends RestAnnotator>> getAnnotators() {
    return _annotators;
  }
}
