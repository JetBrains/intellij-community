package org.intellij.html;

import com.intellij.html.RelaxedHtmlNSDescriptor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.relaxNG.model.descriptors.RngNsDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class RelaxedHtmlFromRngNSDescriptor extends RngNsDescriptor implements RelaxedHtmlNSDescriptor {
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(tag);

    String namespace;
    if (elementDescriptor == null &&
        !((namespace = tag.getNamespace()).equals(XmlUtil.XHTML_URI))) {
      return new AnyXmlElementDescriptor(
        null,
        XmlUtil.HTML_URI.equals(namespace) ? this : tag.getNSDescriptor(tag.getNamespace(), true)
      );
    }

    return elementDescriptor;
  }

  @Override
  protected XmlElementDescriptor initDescriptor(@NotNull XmlElementDescriptor descriptor) {
    return new RelaxedHtmlFromRngElementDescriptor(descriptor);
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument doc) {
    return ArrayUtil.mergeArrays(super.getRootElementsDescriptors(doc), HtmlUtil.getCustomTagDescriptors(doc), XmlElementDescriptor.class);
  }
}
