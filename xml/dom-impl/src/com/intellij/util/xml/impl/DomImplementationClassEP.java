package com.intellij.util.xml.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomImplementationClassEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<DomImplementationClassEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.implementation");

  @Attribute("interfaceClass")
  public String interfaceName;

  @Attribute("implementationClass")
  public String implementationName;


  @Nullable
  public Class getInterfaceClass() {
    return findClassNoExceptions(interfaceName);
  }

  @Nullable
  public Class getImplementationClass() {
    return findClassNoExceptions(implementationName);
  }
}
