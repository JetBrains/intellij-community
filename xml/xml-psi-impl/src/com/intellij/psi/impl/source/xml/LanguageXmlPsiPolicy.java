package com.intellij.psi.impl.source.xml;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;

/**
 * @author yole
 */
public class LanguageXmlPsiPolicy extends LanguageExtension<XmlPsiPolicy> {
  public static LanguageXmlPsiPolicy INSTANCE = new LanguageXmlPsiPolicy();

  private LanguageXmlPsiPolicy() {
    super("com.intellij.xml.psiPolicy", new CDATAOnAnyEncodedPolicy());
  }
}
