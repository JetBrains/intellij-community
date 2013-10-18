package com.jetbrains.rest;

import com.intellij.psi.templateLanguages.TemplateDataElementType;

/**
 * User : catherine
 */
public interface RestPythonElementTypes {
  TemplateDataElementType PYTHON_BLOCK_DATA = new RestPythonTemplateType("PYTHON_BLOCK_DATA", RestLanguage.INSTANCE,
                                RestTokenTypes.PYTHON_LINE, RestTokenTypes.REST_INJECTION);

  TemplateDataElementType DJANGO_BLOCK_DATA = new RestPythonTemplateType("DJANGO_BLOCK_DATA", RestLanguage.INSTANCE,
                                RestTokenTypes.DJANGO_LINE, RestTokenTypes.REST_DJANGO_INJECTION);
}

