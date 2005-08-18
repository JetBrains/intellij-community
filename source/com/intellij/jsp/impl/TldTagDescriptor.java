package com.intellij.jsp.impl;

import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.psi.xml.*;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.impl.source.jsp.tagLibrary.TeiClassLoader;
import com.intellij.psi.impl.source.jsp.tagLibrary.TldUtil;
import com.intellij.psi.impl.source.jsp.JspImplUtil;
import com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl;
import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.j2ee.jsp.MyTEI;
import com.intellij.openapi.vfs.VirtualFile;

import javax.servlet.jsp.tagext.*;
import java.net.URL;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 11, 2005
 * Time: 8:55:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class TldTagDescriptor extends CustomTagDescriptorBase  {
  private String myTagClass;
  private String myTeiClass;

  public TldTagDescriptor() {}

  public TldTagDescriptor(XmlTag tag) {
    init(tag);
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors() {
    if (myAttributeDescriptors==null) {
      final XmlTag[] subTags = myTag.findSubTags("attribute", null);
      final XmlAttributeDescriptor[] xmlAttributeDescriptors = new XmlAttributeDescriptor[subTags.length];
      
      for (int i = 0; i < subTags.length; i++) {
        xmlAttributeDescriptors[i] = new TldAttributeDescriptor(
          subTags[i],
          CustomTagSupportUtil.ValueAccessor.SUB_TAG_ACCESSOR
        );
      }
      
      myAttributeDescriptors = xmlAttributeDescriptors;
    }
    return myAttributeDescriptors;
  }

  public String getName() {
    if (myName == null) {
      final XmlTag firstSubTag = myTag.findFirstSubTag("name");
      myName = (firstSubTag!=null)?firstSubTag.getValue().getText():null;
    }
    return myName;
  }

  public void init(PsiElement element) {
    if (myTag!=element && myTag!=null) {
      myNsDescriptor = null;
    }
    myTag = (XmlTag)element;
    final XmlTag bodyContent = myTag.findFirstSubTag("bodycontent");
    if (bodyContent!=null) myEmpty = bodyContent.getValue().getText().equals("empty");

    XmlTag tei = myTag.findFirstSubTag("teiclass");
    if (tei == null) tei = myTag.findFirstSubTag("tei-class");
    if (tei != null) {
      myTeiClass = tei.getValue().getTrimmedText();
    }
    else{
      myTeiClass = null;
    }

    tei = myTag.findFirstSubTag("tagclass");
    if (tei == null) tei = myTag.findFirstSubTag("tag-class");
    if (tei != null) {
      myTagClass = tei.getValue().getTrimmedText();
    }
    else{
      myTagClass = null;
    }

    XmlTag dynamicAttributes = myTag.findFirstSubTag("dynamic-attributes");
    if (dynamicAttributes!=null) {
      myDynamicAttributes = CustomTagSupportUtil.isTrue(dynamicAttributes.getValue().getTrimmedText());
    }

    final XmlTag[] vars = myTag.findSubTags("variable");

    CustomTagSupportUtil.configureVariables(
      myTLDVars,
      vars,
      CustomTagSupportUtil.ValueAccessor.SUB_TAG_ACCESSOR
    );

    final XmlTag[] attributes = myTag.findSubTags("attribute");
    CustomTagSupportUtil.configureAttributes(
      myTLDAttributes,
      attributes,
      CustomTagSupportUtil.ValueAccessor.SUB_TAG_ACCESSOR
    );
  }

  public Object[] getDependences() {
    return new Object[]{myTag, ExternalResourceManagerImpl.getInstance()};
  }

  public void validate(PsiElement context,ValidationHost host) {
    super.validate(context, host);

    final PsiFile containingFile = context.getContainingFile();
    if (!(containingFile instanceof JspFile)) return;
    final WebModuleProperties properties = ((JspFile)containingFile).getWebModuleProperties();

    if(properties != null) {
      final TagExtraInfo info = getExtraInfo(properties);
      if (info == null) return;

      try {
        if (info.isValid(JspImplUtil.getTagData((XmlTag)context))) return;
      }
      catch (Throwable e) {
        host.addMessage(context,"Exception during TEI processing occured: " + e.getMessage(),ValidationHost.ERROR);
      }

      host.addMessage(context,"Wrong Tag Data",ValidationHost.ERROR);
    }
  }

  public TagExtraInfo getExtraInfo(WebModuleProperties moduleProperties) {
    Object tei;
    TagExtraInfo castedTei = null;

    if (myTeiClass != null) {
      final VirtualFile virtualFile = myTag.getContainingFile().getVirtualFile();

      List<URL> urls = TldUtil.buildUrls(virtualFile, moduleProperties.getModule());
      TeiClassLoader classLoader = new TeiClassLoader(urls, getClass().getClassLoader());

      try {
        Class aClass = classLoader.loadClass(myTeiClass);
        tei = aClass.newInstance();
        castedTei = (TagExtraInfo)tei;
        castedTei.setTagInfo(new TagInfo(
          myName,
          myTagClass,
          "",
          "",
          new TagLibraryInfo("", ""){},
          castedTei,
          myTLDAttributes.toArray(new TagAttributeInfo[0]),
          myName, "", "",
          myTLDVars.toArray(new TagVariableInfo[myTLDVars.size()])));
      }
      catch (Exception e) {
      }
    }

    if (castedTei != null) {
      return castedTei;
    }

    return new MyTEI(myTLDVars,this);
  }

  public XmlTag findVariableWithName(String name) {
    return findVariableWithName(
      myTag.findSubTags("variable"),
      name, 
      CustomTagSupportUtil.ValueAccessor.SUB_TAG_ACCESSOR
    );
  }

  public PsiElement getDeclaration() {
    return myTag;
  }
}
