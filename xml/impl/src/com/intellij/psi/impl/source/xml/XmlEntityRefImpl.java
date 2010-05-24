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

import com.intellij.openapi.fileTypes.StdFileTypes;
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
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class XmlEntityRefImpl extends XmlElementImpl implements XmlEntityRef {
  private static final Key<Map<String,CachedValue<XmlEntityDecl>>> XML_ENTITY_DECL_MAP = Key.create("XML_ENTITY_DECL_MAP");
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

  public static XmlEntityDecl getCachedEntity(PsiFile file, String name) {
    CachedValue<XmlEntityDecl> cachedValue;
    synchronized(PsiLock.LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = getCachingMap(file);
      cachedValue = cachingMap.get(name);
    }
    return cachedValue != null ? cachedValue.getValue():null;
  }

  public static void cacheParticularEntity(PsiFile file, final XmlEntityDecl decl) {
    synchronized(PsiLock.LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = getCachingMap(file);
      final String name = decl.getName();
      if (cachingMap.containsKey(name)) return;
      cachingMap.put(
        name, CachedValuesManager.getManager(file.getProject()).createCachedValue(new CachedValueProvider<XmlEntityDecl>() {
          public Result<XmlEntityDecl> compute() {
            if (decl.isValid() && name.equals(decl.getName()))
              return new Result<XmlEntityDecl>(decl,decl);
            cachingMap.put(name,null);
            return new Result<XmlEntityDecl>(null,null);
          }
        },
        false
      ));
    }
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
      Map<String, CachedValue<XmlEntityDecl>> map = getCachingMap(targetElement);

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

  private static Map<String, CachedValue<XmlEntityDecl>> getCachingMap(final PsiElement targetElement) {
    Map<String, CachedValue<XmlEntityDecl>> map = targetElement.getUserData(XML_ENTITY_DECL_MAP);
    if (map == null){
      map = new HashMap<String,CachedValue<XmlEntityDecl>>();
      targetElement.putUserData(XML_ENTITY_DECL_MAP, map);
    }
    return map;
  }

  private static final Key<Boolean> DISABLE_ENTITY_EXPAND = Key.create("disable.entity.expand");

  private static CachedValueProvider.Result<XmlEntityDecl> resolveEntity(final PsiElement targetElement, final String entityName, PsiFile contextFile) {
    if (targetElement.getUserData(EVALUATION_IN_PROCESS) != null) {
      return new CachedValueProvider.Result<XmlEntityDecl>(null,targetElement);
    }
    try {
      targetElement.putUserData(EVALUATION_IN_PROCESS, "");
      final List<PsiElement> deps = new ArrayList<PsiElement>();
      final XmlEntityDecl[] result = new XmlEntityDecl[]{null};

      PsiElementProcessor processor = new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof XmlDoctype) {
            XmlDoctype xmlDoctype = (XmlDoctype)element;
            final String dtdUri = XmlUtil.getDtdUri(xmlDoctype);
            if (dtdUri != null) {
              final XmlFile xmlFile = XmlUtil.findNamespace(XmlUtil.getContainingFile(element), dtdUri);
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
      deps.add(targetElement);

      boolean notfound = PsiTreeUtil.processElements(targetElement, processor);
      if (notfound) {
        if (contextFile != targetElement && contextFile != null && contextFile.isValid()) {
          notfound = PsiTreeUtil.processElements(contextFile, processor);
        }
      }

      if (notfound &&       // no dtd ref at all
          targetElement instanceof XmlFile &&
          deps.size() == 1 &&
          ((XmlFile)targetElement).getFileType() != StdFileTypes.DTD
         ) {
        XmlDocument document = ((XmlFile)targetElement).getDocument();
        final XmlTag rootTag = document.getRootTag();

        if (rootTag != null && document.getUserData(DISABLE_ENTITY_EXPAND) == null) {
          final XmlElementDescriptor descriptor = rootTag.getDescriptor();

            if (descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor)) {
              PsiElement element = descriptor.getDeclaration();
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

  public static void copyEntityCaches(final PsiFile file, final PsiFile context) {
    synchronized (PsiLock.LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = getCachingMap(file);
      for(Map.Entry<String,CachedValue<XmlEntityDecl>> entry:getCachingMap(context).entrySet()) {
        cachingMap.put(entry.getKey(), entry.getValue());
      }
    }

  }

  public static void setNoEntityExpandOutOfDocument(XmlDocument doc, boolean b) {
    if (b) doc.putUserData(DISABLE_ENTITY_EXPAND, Boolean.TRUE);
    else doc.putUserData(DISABLE_ENTITY_EXPAND, null);
  }
}
