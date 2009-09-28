package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomConfigurationParameter;

public class MavenPluginConfigurationParameterDomExtender extends DomExtender<MavenDomConfigurationParameter> {
  @Override
  public void registerExtensions(@NotNull MavenDomConfigurationParameter param, @NotNull DomExtensionsRegistrar r) {
    for (XmlAttribute each : param.getXmlTag().getAttributes()) {
      String name = each.getName();
      if (CompletionUtil.DUMMY_IDENTIFIER_TRIMMED.equals(name)) continue;
      r.registerGenericAttributeValueChildExtension(new XmlName(name), String.class);
    }
  }
}
