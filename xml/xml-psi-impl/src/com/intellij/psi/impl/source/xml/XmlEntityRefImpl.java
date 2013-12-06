/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class XmlEntityRefImpl extends XmlElementImpl implements XmlEntityRef {
  @NonNls private static final String GT_ENTITY = "&gt;";
  @NonNls private static final String QUOT_ENTITY = "&quot;";

  public XmlEntityRefImpl() {
    super(XmlElementType.XML_ENTITY_REF);
  }

  private static final Key<String> EVALUATION_IN_PROCESS = Key.create("EvalKey");

  public XmlEntityDecl resolve(PsiFile targetFile) {
    String text = getText();
    if (text.equals(GT_ENTITY) || text.equals(QUOT_ENTITY)) return null;
    return resolveEntity(this, text, targetFile);
  }

  public static XmlEntityDecl resolveEntity(final XmlElement element, final String text, PsiFile targetFile) {
    if (targetFile instanceof XmlFile) {
      XmlDocument document = ((XmlFile)targetFile).getDocument();
      if (document != null && document.getUserData(DISABLE_ENTITY_EXPAND) != null) return null;
    }
    
    final String entityName = text.substring(1, text.length() - 1);

    final PsiElement targetElement = targetFile != null ? targetFile : element;
    CachedValue<XmlEntityDecl> value;
    synchronized(PsiLock.LOCK) {
      Map<String, CachedValue<XmlEntityDecl>> map = XmlEntityCache.getCachingMap(targetElement);

      value = map.get(entityName);
      final PsiFile containingFile = element.getContainingFile();

      if (value == null) {
        final PsiManager manager = element.getManager();
        if(manager == null){
          return resolveEntity(targetElement, entityName, containingFile).getValue();
        }
        value = CachedValuesManager.getManager(manager.getProject()).createCachedValue(new CachedValueProvider<XmlEntityDecl>() {
          public Result<XmlEntityDecl> compute() {
            return resolveEntity(targetElement, entityName, containingFile);
          }
        });


        map.put(entityName, value);
      }
    }
    return value.getValue();
  }

  private static final Key<Boolean> DISABLE_ENTITY_EXPAND = Key.create("disable.entity.expand");

  private static CachedValueProvider.Result<XmlEntityDecl> resolveEntity(final PsiElement targetElement, final String entityName, PsiFile contextFile) {
    if (targetElement.getUserData(EVALUATION_IN_PROCESS) != null) {
      return new CachedValueProvider.Result<XmlEntityDecl>(null,targetElement);
    }
    try {
      targetElement.putUserData(EVALUATION_IN_PROCESS, "");
      final List<PsiElement> deps = new ArrayList<PsiElement>();
      final XmlEntityDecl[] result = {null};

      PsiElementProcessor processor = new PsiElementProcessor() {
        public boolean execute(@NotNull PsiElement element) {
          if (element instanceof XmlDoctype) {
            XmlDoctype xmlDoctype = (XmlDoctype)element;
            final String dtdUri = getDtdForEntity(xmlDoctype);
            if (dtdUri != null) {
              XmlFile file = XmlUtil.getContainingFile(element);
              if (file == null) return true;
              final XmlFile xmlFile = XmlUtil.findNamespace(file, dtdUri);
              if (xmlFile != null) {
                if (xmlFile != targetElement) {
                  deps.add(xmlFile);
                  if(!XmlUtil.processXmlElements(xmlFile, this,true)) return false;
                }
              }
            }
            final XmlMarkupDecl markupDecl = xmlDoctype.getMarkupDecl();
            if (markupDecl != null) {
              if (!XmlUtil.processXmlElements(markupDecl, this, true)) return false;
            }
          }
          else if (element instanceof XmlEntityDecl) {
            XmlEntityDecl entityDecl = (XmlEntityDecl)element;
            final String declName = entityDecl.getName();
            if (declName.equals(entityName)) {
              result[0] = entityDecl;
              return false;
            }
          }

          return true;
        }
      };
      FileViewProvider provider = targetElement.getContainingFile().getViewProvider();
      deps.add(provider.getPsi(provider.getBaseLanguage()));

      boolean notfound = PsiTreeUtil.processElements(targetElement, processor);
      if (notfound) {
        if (contextFile != targetElement && contextFile != null && contextFile.isValid()) {
          notfound = PsiTreeUtil.processElements(contextFile, processor);
        }
      }

      if (notfound &&       // no dtd ref at all
          targetElement instanceof XmlFile &&
          deps.size() == 1 &&
          ((XmlFile)targetElement).getFileType() != DTDFileType.INSTANCE
         ) {
        XmlDocument document = ((XmlFile)targetElement).getDocument();
        final XmlTag rootTag = document.getRootTag();

        if (rootTag != null && document.getUserData(DISABLE_ENTITY_EXPAND) == null) {
          final XmlElementDescriptor descriptor = rootTag.getDescriptor();

            if (descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor)) {
              PsiElement element = !HtmlUtil.isHtml5Context(rootTag) ? descriptor.getDeclaration() :
                                   XmlUtil.findXmlFile((XmlFile)targetElement, Html5SchemaProvider.getCharsDtdLocation());
              final PsiFile containingFile = element != null ? element.getContainingFile():null;
              final XmlFile descriptorFile = containingFile instanceof XmlFile ? (XmlFile)containingFile:null;

              if (descriptorFile != null &&
                  !descriptorFile.getName().equals(((XmlFile)targetElement).getName()+".dtd")) {
                deps.add(descriptorFile);
                XmlUtil.processXmlElements(
                  descriptorFile,
                  processor,
                  true
                );
              }
            }
        }
      }

      return new CachedValueProvider.Result<XmlEntityDecl>(result[0], ArrayUtil.toObjectArray(deps));
    }
    finally {
      targetElement.putUserData(EVALUATION_IN_PROCESS, null);
    }
  }

  private static String getDtdForEntity(XmlDoctype xmlDoctype) {
    return HtmlUtil.isHtml5Doctype(xmlDoctype) ? Html5SchemaProvider.getCharsDtdLocation() : XmlUtil.getDtdUri(xmlDoctype);
  }

  public XmlTag getParentTag() {
    final XmlElement parent = (XmlElement)getParent();
    if(parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    PsiElement nextSibling = getNextSibling();
    if(nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    final PsiElement prevSibling = getPrevSibling();
    if(prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this,XmlEntityRef.class);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public static void setNoEntityExpandOutOfDocument(XmlDocument doc, boolean b) {
    if (b) doc.putUserData(DISABLE_ENTITY_EXPAND, Boolean.TRUE);
    else doc.putUserData(DISABLE_ENTITY_EXPAND, null);
  }
}
