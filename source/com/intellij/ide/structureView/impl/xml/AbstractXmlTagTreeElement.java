package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;

public abstract class AbstractXmlTagTreeElement<T extends XmlElement> extends PsiTreeElementBase<T> {
  protected AbstractXmlTagTreeElement(final T psiElement) {
    super(psiElement);
  }

  protected static Collection<StructureViewTreeElement> getStructureViewTreeElements(XmlTag[] subTags) {
    final XmlStructureViewElementProvider[] providers =
      (XmlStructureViewElementProvider[])Extensions.getExtensions(XmlStructureViewElementProvider.EXTENSION_POINT_NAME);

    return ContainerUtil.map2List(subTags, new Function<XmlTag, StructureViewTreeElement>() {
      public StructureViewTreeElement fun(final XmlTag xmlTag) {
        for (final XmlStructureViewElementProvider provider : providers) {
          final StructureViewTreeElement element = provider.createCustomXmlTagTreeElement(xmlTag);
          if (element != null) {
            return element;
          }
        }
        return new XmlTagTreeElement(xmlTag);
      }
    });
  }
}
