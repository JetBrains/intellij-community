package com.intellij.jsp.impl;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.psi.xml.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.impl.source.jsp.tagLibrary.TeiClassLoader;
import com.intellij.psi.impl.source.jsp.JspImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl;
import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.j2ee.jsp.MyTEI;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.impl.jar.VirtualFileImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.diagnostic.Logger;

import javax.servlet.jsp.tagext.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 11, 2005
 * Time: 8:55:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class TldTagDescriptor implements XmlElementDescriptor,Validator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.TldTagDescriptor");

  private XmlTag myTag;
  private String myName;
  private XmlAttributeDescriptor[] myAttributeDescriptors;
  private TldDescriptor myNsDescriptor;
  private boolean myEmpty;

  private String myTagClass;
  private String myTeiClass;
  private List<TagVariableInfo> myTLDVars = new ArrayList<TagVariableInfo>();
  private List<TagAttributeInfo> myTLDAttributes = new ArrayList<TagAttributeInfo>();

  public TldTagDescriptor() {}

  public TldTagDescriptor(XmlTag tag) {
    init(tag);
  }

  public String getQualifiedName() {
    return getName();
  }

  public String getDefaultName() {
    return getName();
  }

  //todo: refactor to support full DTD spec
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return EMPTY_ARRAY;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag childTag) {
    if (myEmpty) return null;
    return new AnyXmlElementDescriptor(this,getNSDescriptor());
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors() {
    if (myAttributeDescriptors==null) {
      final XmlTag[] subTags = myTag.findSubTags("attribute", null);
      myAttributeDescriptors = new XmlAttributeDescriptor[subTags.length];

      for (int i = 0; i < subTags.length; i++) {
        myAttributeDescriptors[i] = new TldAttributeDescriptor(subTags[i]);
      }
    }
    return myAttributeDescriptors;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    final XmlAttributeDescriptor[] attributesDescriptors = getAttributesDescriptors();

    for (int i = 0; i < attributesDescriptors.length; i++) {
      final XmlAttributeDescriptor attributesDescriptor = attributesDescriptors[i];

      if (attributesDescriptor.getName().equals(attributeName)) {
        return attributesDescriptor;
      }
    }
    return null;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName());
  }

  public XmlNSDescriptor getNSDescriptor() {
    if (myNsDescriptor==null) {
      final PsiFile file = myTag.getContainingFile();
      if(!(file instanceof XmlFile)) return null;
      final XmlDocument document = ((XmlFile)file).getDocument();
      myNsDescriptor = (TldDescriptor) document.getMetaData();
    }

    return myNsDescriptor;
  }

  public int getContentType() {
    if (myEmpty) return CONTENT_TYPE_EMPTY;;
    return CONTENT_TYPE_MIXED;
  }

  public PsiElement getDeclaration() {
    return myTag;
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return true;
  }

  public String getName(PsiElement context) {
    String value = getName();

    if(context instanceof XmlElement){
      final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);

      if(tag != null){
        final String namespacePrefix = tag.getPrefixByNamespace( ((TldDescriptor)getNSDescriptor()).getUri() );
        if(namespacePrefix != null && namespacePrefix.length() > 0)
          value = namespacePrefix + ":" + XmlUtil.findLocalNameByQualifiedName(value);
      }
    }

    return value;
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

    final XmlTag[] vars = myTag.findSubTags("variable");
    for (int i = 0; i < vars.length; i++) {
      XmlTag var = vars[i];

      final XmlTag nameGiven = var.findFirstSubTag("name-given");
      final XmlTag variableClass = var.findFirstSubTag("variable-class");
      final XmlTag declare = var.findFirstSubTag("declare");
      final XmlTag scope = var.findFirstSubTag("scope");
      final XmlTag nameFromAttribute = var.findFirstSubTag("name-from-attribute");
//      final XmlTag fragment = var.findSubTag("fragment"); 2.0 support

      String className = "java.lang.Object";
      if (variableClass != null && !variableClass.getValue().getTrimmedText().equals("")) className = variableClass.getValue().getTrimmedText();
      int scopeInt = VariableInfo.NESTED;
      if(scope != null){
        final String scopeStr = scope.getValue().getTrimmedText();
        if("AT_BEGIN".equals(scopeStr)){
          scopeInt = VariableInfo.AT_BEGIN;
        }
        else if("AT_END".equals(scopeStr)){
          scopeInt = VariableInfo.AT_END;
        }
      }

      myTLDVars.add(new TagVariableInfo(
        nameGiven != null ? nameGiven.getValue().getTrimmedText() : null,
        nameFromAttribute != null ? nameFromAttribute.getValue().getTrimmedText() : null,
        className,
        declare == null || ("true".equals(declare.getValue().getTrimmedText()) || "yes".equals(declare.getValue().getTrimmedText())),
        scopeInt/*, 2.0 support
        fragment != null ? fragment.getValue() : null*/
      ));
    }

    final XmlTag[] attributes = myTag.findSubTags("attribute");
    for (int i = 0; i < attributes.length; i++) {
      final XmlTag attName = attributes[i].findFirstSubTag("name");
      final XmlTag requiered = attributes[i].findFirstSubTag("requiered");
      final XmlTag rtEx = attributes[i].findFirstSubTag("rtexprvalue");
      final XmlTag type = attributes[i].findFirstSubTag("type");

      myTLDAttributes.add(new TagAttributeInfo(
        attName != null ? attName.getValue().getTrimmedText() : null,
        requiered != null && ("true".equals(requiered.getValue().getTrimmedText()) || "yes".equals(requiered.getValue().getTrimmedText())),
        type != null ? type.getValue().getTrimmedText() : null,
        rtEx != null && ("true".equals(rtEx.getValue().getTrimmedText()) || "yes".equals(rtEx.getValue().getTrimmedText()))));
    }
  }

  public Object[] getDependences() {
    return new Object[]{myTag, ExternalResourceManagerImpl.getInstance()};
  }

  public void validate(PsiElement context,ValidationHost host) {
    final WebModuleProperties properties = ((JspFile)context.getContainingFile()).getWebModuleProperties();

    if(properties != null) {
      final TagExtraInfo info = getExtraInfo(properties);
      if (info == null) return;
      //final JspToken end = getEndToken(context);
      //if (end == null) return false;

      try {
        //info.getVariableInfo(JspImplUtil.getTagData((XmlTag)context));
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
      List<URL> urls = new ArrayList<URL>();

      final VirtualFile virtualFile = myTag.getContainingFile().getVirtualFile();

      if (virtualFile != null){
        final String ext = virtualFile.getExtension();
        if(ext != null && ext.equalsIgnoreCase("jar")) {
          addUrl(urls, virtualFile);
        }
      }

      if (moduleProperties != null) {
        final VirtualFile[] files = ModuleRootManager.getInstance(moduleProperties.getModule()).getFiles(OrderRootType.CLASSES_AND_OUTPUT);

        for (int j = 0; j < files.length; j++) {
          final VirtualFile file;
          try {
            if(files[j] instanceof VirtualFileImpl){
              file = LocalFileSystem.getInstance().findFileByIoFile(new File(JarFileSystem.getInstance().getJarFile(files[j]).getName()));
            }
            else file = files[j];
            addUrl(urls, file);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }

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

    if(castedTei != null)
      return castedTei;

    return new MyTEI(myTLDVars);
  }

  private void addUrl(List<URL> urls, VirtualFile file) {
    if (file == null || !file.isValid()) return;
    final URL url = getUrl(file);
    if (url != null) {
      urls.add(url);
    }
  }

  private URL getUrl(final VirtualFile file) {
    if (file.getFileSystem() instanceof JarFileSystem && file.getParent() != null) return null;

    String path = file.getPath();
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - 2);
    }

    String url;
    if (SystemInfo.isWindows) {
      url = "file:/" + path;
    }
    else {
      url = "file://" + path;
    }

    if (file.isDirectory() && !(file.getFileSystem() instanceof JarFileSystem)) url += "/";


    try {
      return new URL(url);
    }
    catch (MalformedURLException e) {
      LOG.error(e);
      return null;
    }
  }
}
