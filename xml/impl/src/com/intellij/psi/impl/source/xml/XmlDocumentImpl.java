/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.XmlDocumentChangedImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiCachedValueImpl;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlNSDescriptorSequence;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Mike
 */
public class XmlDocumentImpl extends XmlElementImpl implements XmlDocument {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlDocumentImpl");
  private volatile XmlProlog myProlog;
  private volatile XmlTag myRootTag;

  public XmlDocumentImpl() {
    this(XmlElementType.XML_DOCUMENT);
  }

  protected XmlDocumentImpl(IElementType type) {
    super(type);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlDocument(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XmlElementType.XML_PROLOG) {
      return XmlChildRole.XML_PROLOG;
    }
    else if (i == XmlElementType.XML_TAG) {
      return XmlChildRole.XML_TAG;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public XmlProlog getProlog() {
    XmlProlog prolog = myProlog;

    if (prolog == null) {
      synchronized (this) {
        prolog = myProlog;
        if (prolog == null) {
          prolog = (XmlProlog)findElementByTokenType(XmlElementType.XML_PROLOG);
          myProlog = prolog;
        }
      }
    }

    return myProlog;
  }

  public XmlTag getRootTag() {
    XmlTag rootTag = myRootTag;

    if (rootTag == null) {
      synchronized (this) {
        rootTag = myRootTag;
        if (rootTag == null) {
          rootTag = (XmlTag)findElementByTokenType(XmlElementType.XML_TAG);
          myRootTag = rootTag;
        }
      }
    }

    return myRootTag;
  }

  public XmlNSDescriptor getRootTagNSDescriptor() {
    XmlTag rootTag = getRootTag();
    return rootTag != null ? rootTag.getNSDescriptor(rootTag.getNamespace(), false) : null;
  }

  private ConcurrentHashMap<String, CachedValue<XmlNSDescriptor>> myDefaultDescriptorsCacheStrict = new ConcurrentHashMap<String, CachedValue<XmlNSDescriptor>>();
  private ConcurrentHashMap<String, CachedValue<XmlNSDescriptor>> myDefaultDescriptorsCacheNotStrict = new ConcurrentHashMap<String, CachedValue<XmlNSDescriptor>>();

  public void clearCaches() {
    myDefaultDescriptorsCacheStrict.clear();
    myDefaultDescriptorsCacheNotStrict.clear();
    myProlog = null;
    myRootTag = null;
    super.clearCaches();
  }

  public XmlNSDescriptor getDefaultNSDescriptor(final String namespace, final boolean strict) {
    final ConcurrentHashMap<String, CachedValue<XmlNSDescriptor>> defaultDescriptorsCache;
    if (strict) {
      defaultDescriptorsCache = myDefaultDescriptorsCacheStrict;
    }
    else {
      defaultDescriptorsCache = myDefaultDescriptorsCacheNotStrict;
    }

    CachedValue<XmlNSDescriptor> cachedValue = defaultDescriptorsCache.get(namespace);
    if (cachedValue == null) {
      defaultDescriptorsCache.put(namespace, cachedValue = new PsiCachedValueImpl<XmlNSDescriptor>(getManager(), new CachedValueProvider<XmlNSDescriptor>() {
        public Result<XmlNSDescriptor> compute() {
          final XmlNSDescriptor defaultNSDescriptorInner = getDefaultNSDescriptorInner(namespace, strict);

          if (isGeneratedFromDtd(defaultNSDescriptorInner)) {
            return new Result<XmlNSDescriptor>(defaultNSDescriptorInner, XmlDocumentImpl.this, ExternalResourceManager.getInstance());
          }

          return new Result<XmlNSDescriptor>(defaultNSDescriptorInner, defaultNSDescriptorInner != null
                                                                       ? defaultNSDescriptorInner.getDependences()
                                                                       : ExternalResourceManager.getInstance());
        }
      }));
    }
    return cachedValue.getValue();
  }

  private boolean isGeneratedFromDtd(XmlNSDescriptor defaultNSDescriptorInner) {
    if (defaultNSDescriptorInner == null) {
      return false;
    }
    XmlFile descriptorFile = defaultNSDescriptorInner.getDescriptorFile();
    if (descriptorFile == null) {
        return false;
    }
    @NonNls String otherName = XmlUtil.getContainingFile(this).getName() + ".dtd";
    return descriptorFile.getName().equals(otherName);
  }

  private XmlNSDescriptor getDefaultNSDescriptorInner(final String namespace, final boolean strict) {
    final XmlFile containingFile = XmlUtil.getContainingFile(this);
    final XmlProlog prolog = getProlog();
    final XmlDoctype doctype = prolog != null ? prolog.getDoctype() : null;
    boolean dtdUriFromDocTypeIsNamespace = false;

    if (XmlUtil.HTML_URI.equals(namespace)) {
      XmlNSDescriptor nsDescriptor = doctype != null ? getNsDescriptorFormDocType(doctype, containingFile) : null;
      if (nsDescriptor == null) nsDescriptor = getDefaultNSDescriptor(XmlUtil.XHTML_URI, false);
      return new HtmlNSDescriptorImpl(nsDescriptor);
    }
    else if (namespace != null && namespace != XmlUtil.EMPTY_URI) {
      if (doctype == null || !namespace.equals(XmlUtil.getDtdUri(doctype))) {
        boolean documentIsSchemaThatDefinesNs = namespace.equals(XmlUtil.getTargetSchemaNsFromTag(getRootTag()));

        final XmlFile xmlFile = documentIsSchemaThatDefinesNs
                                ? containingFile
                                : XmlUtil.findNamespace(containingFile, namespace);
        if (xmlFile != null) {
          final XmlDocument document = xmlFile.getDocument();
          if (document != null) {
            return (XmlNSDescriptor)document.getMetaData();
          }
        }
      } else {
        dtdUriFromDocTypeIsNamespace = true;
      }
    }

    if (strict && !dtdUriFromDocTypeIsNamespace) return null;

    if (doctype != null) {
      XmlNSDescriptor descr = getNsDescriptorFormDocType(doctype, containingFile);

      if (descr != null) {
        return XmlExtension.getExtension(containingFile).getDescriptorFromDoctype(containingFile, descr);
      }
    }

    if (strict) return null;
    if (namespace == XmlUtil.EMPTY_URI) {
      final XmlFile xmlFile = XmlUtil.findNamespace(containingFile, namespace);
      if (xmlFile != null) {
        return (XmlNSDescriptor)xmlFile.getDocument().getMetaData();
      }
    }
    try {
      final PsiFile fileFromText = PsiFileFactory.getInstance(getProject())
        .createFileFromText(containingFile.getName() + ".dtd", XmlUtil.generateDocumentDTD(this, false));
      if (fileFromText instanceof XmlFile) {
        return (XmlNSDescriptor)((XmlFile)fileFromText).getDocument().getMetaData();
      }
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException ignored) {
    } // e.g. dtd isn't mapped to xml type

    return null;
  }

  private XmlNSDescriptor getNsDescriptorFormDocType(final XmlDoctype doctype, final XmlFile containingFile) {
    XmlNSDescriptor descr = null;
    if (doctype.getMarkupDecl() != null){
      descr = (XmlNSDescriptor)doctype.getMarkupDecl().getMetaData();
      final XmlElementDescriptor[] rootElementsDescriptors = descr.getRootElementsDescriptors(this);
      if (rootElementsDescriptors.length == 0) descr = null;
    }

    final String dtdUri = XmlUtil.getDtdUri(doctype);
    if (dtdUri != null && dtdUri.length() > 0){
      final XmlFile xmlFile = XmlUtil.findNamespace(containingFile, dtdUri);
      final XmlNSDescriptor descr1 = xmlFile == null ? null : (XmlNSDescriptor)xmlFile.getDocument().getMetaData();
      if (descr != null && descr1 != null){
        descr = new XmlNSDescriptorSequence(new XmlNSDescriptor[]{descr, descr1});
      }
      else if (descr1 != null) {
        descr = descr1;
      }
    }
    return descr;
  }

  public Object clone() {
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheStrict = new HashMap<String, CachedValue<XmlNSDescriptor>>(
      myDefaultDescriptorsCacheStrict
    );
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheNotStrict = new HashMap<String, CachedValue<XmlNSDescriptor>>(
      myDefaultDescriptorsCacheNotStrict
    );
    final XmlDocumentImpl copy = (XmlDocumentImpl) super.clone();
    updateSelfDependentDtdDescriptors(copy, cacheStrict, cacheNotStrict);
    return copy;
  }

  public PsiElement copy() {
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheStrict = new HashMap<String, CachedValue<XmlNSDescriptor>>(
      myDefaultDescriptorsCacheStrict
    );
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheNotStrict = new HashMap<String, CachedValue<XmlNSDescriptor>>(
      myDefaultDescriptorsCacheNotStrict
    );
    final XmlDocumentImpl copy = (XmlDocumentImpl)super.copy();
    updateSelfDependentDtdDescriptors(copy, cacheStrict, cacheNotStrict);
    return copy;
  }

  private void updateSelfDependentDtdDescriptors(XmlDocumentImpl copy, HashMap<String,
    CachedValue<XmlNSDescriptor>> cacheStrict, HashMap<String, CachedValue<XmlNSDescriptor>> cacheNotStrict) {
    copy.myDefaultDescriptorsCacheNotStrict = new ConcurrentHashMap<String, CachedValue<XmlNSDescriptor>>();
    copy.myDefaultDescriptorsCacheStrict = new ConcurrentHashMap<String, CachedValue<XmlNSDescriptor>>();

    for(Map.Entry<String, CachedValue<XmlNSDescriptor>> e:cacheStrict.entrySet()) {
      if (e.getValue().hasUpToDateValue()) {
        final XmlNSDescriptor nsDescriptor = e.getValue().getValue();
        if (!isGeneratedFromDtd(nsDescriptor)) copy.myDefaultDescriptorsCacheStrict.put(e.getKey(), e.getValue());
      }
    }

    for(Map.Entry<String, CachedValue<XmlNSDescriptor>> e:cacheNotStrict.entrySet()) {
      if (e.getValue().hasUpToDateValue()) {
        final XmlNSDescriptor nsDescriptor = e.getValue().getValue();
        if (!isGeneratedFromDtd(nsDescriptor)) copy.myDefaultDescriptorsCacheNotStrict.put(e.getKey(), e.getValue());
      }
    }
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void dumpStatistics(){
    System.out.println("Statistics:");
    final TObjectIntHashMap<Object> map = new TObjectIntHashMap<Object>();

    final PsiElementVisitor psiRecursiveElementVisitor = new XmlRecursiveElementVisitor(){
      @NonNls private static final String TOKENS_KEY = "Tokens";
      @NonNls private static final String ELEMENTS_KEY = "Elements";

      @Override public void visitXmlToken(XmlToken token) {
        inc(TOKENS_KEY);
      }

      @Override public void visitElement(PsiElement element) {
        inc(ELEMENTS_KEY);
        super.visitElement(element);
      }

      private void inc(final String key) {
        map.put(key, map.get(key) + 1);
      }
    };

    accept(psiRecursiveElementVisitor);

    final Object[] keys = map.keys();
    for (final Object key : keys) {
      System.out.println(key + ": " + map.get(key));
    }
  }

  public TreeElement addInternal(final TreeElement first, final ASTNode last, final ASTNode anchor, final Boolean before) {
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    final TreeElement[] holder = new TreeElement[1];
    try{
      model.runTransaction(new PomTransactionBase(this, aspect) {
        public PomModelEvent runInner() {
          holder[0] = XmlDocumentImpl.super.addInternal(first, last, anchor, before);
          return XmlDocumentChangedImpl.createXmlDocumentChanged(model, XmlDocumentImpl.this);
        }
      });
    }
    catch(IncorrectOperationException ignored){}
    return holder[0];
  }

  public void deleteChildInternal(@NotNull final ASTNode child) {
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    try{
      model.runTransaction(new PomTransactionBase(this, aspect) {
        public PomModelEvent runInner() {
          XmlDocumentImpl.super.deleteChildInternal(child);
          return XmlDocumentChangedImpl.createXmlDocumentChanged(model, XmlDocumentImpl.this);
        }
      });
    }
    catch(IncorrectOperationException ignored){}
  }

  public void replaceChildInternal(@NotNull final ASTNode child, @NotNull final TreeElement newElement) {
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    try{
      model.runTransaction(new PomTransactionBase(this, aspect) {
        public PomModelEvent runInner() {
          XmlDocumentImpl.super.replaceChildInternal(child, newElement);
          return XmlDocumentChangedImpl.createXmlDocumentChanged(model, XmlDocumentImpl.this);
        }
      });
    }
    catch(IncorrectOperationException ignored){}
  }
}
