package com.intellij.psi.impl.source.html;

import com.intellij.psi.impl.source.xml.XmlDocumentImpl;
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.PsiManager;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 8:02:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlDocumentImpl extends XmlDocumentImpl {
  private CachedValue<XmlNSDescriptor> myDescriptor;

  public HtmlDocumentImpl() {
    super(HTML_DOCUMENT);
  }

  public XmlTag getRootTag() {
    return (XmlTag)findElementByTokenType(HTML_TAG);
  }

  XmlNSDescriptor getDocumentDescriptor() {
    if (myDescriptor==null) {
      XmlDoctype doctype = getProlog().getDoctype();
      final XmlNSDescriptor xhtmlDescr;

      if (doctype == null || doctype.getDtdUri()==null) {
        xhtmlDescr = PsiManager.getInstance(getProject()).getJspElementFactory().getXHTMLDescriptor();
      } else {
        final XmlFile xmlFile = XmlUtil.findXmlFile(XmlUtil.getContainingFile(this), doctype.getDtdUri());
        if (xmlFile==null) return null;
        xhtmlDescr = (XmlNSDescriptor)xmlFile.getDocument().getMetaData();
      }
      myDescriptor = getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlNSDescriptor>() {
        public CachedValueProvider.Result<XmlNSDescriptor> compute() {
          final HtmlNSDescriptorImpl value = new HtmlNSDescriptorImpl(xhtmlDescr);

          return new Result<XmlNSDescriptor>(
            value,
            value.getDependences()
          );
         }
      }, false);
    }
    return myDescriptor.getValue();
  }
}
