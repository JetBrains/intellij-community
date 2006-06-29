package com.intellij.uiDesigner.wizard;

import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormProperty2BeanProperty {
  public final FormProperty myFormProperty;
  /**
   * This field can be <code>null</code> if nothing is bound.
   */
  public BeanProperty myBeanProperty;

  public FormProperty2BeanProperty(@NotNull final FormProperty formProperty) {
    myFormProperty = formProperty;
  }
}
