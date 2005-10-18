package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormProperty2BeanProperty {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.FormProperty2BeanProperty");

  public final FormProperty myFormProperty;
  /**
   * This field can be <code>null</code> if nothing is bound.
   */
  public BeanProperty myBeanProperty;

  public FormProperty2BeanProperty(final FormProperty formProperty) {
    LOG.assertTrue(formProperty != null);
    myFormProperty = formProperty;
  }
}
