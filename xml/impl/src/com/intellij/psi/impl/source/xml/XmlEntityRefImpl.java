package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
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
        name,
        file.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlEntityDecl>() {
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
    final String entityName = text.substring(1, text.length() - 1);

    if (PsiUtil.isInJspFile(targetFile)) {
      targetFile = (PsiUtil.getJspFile(targetFile)).getBaseLanguageRoot();
    }

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
        value = manager.getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlEntityDecl>() {
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
            final String dtdUri = xmlDoctype.getDtdUri();
            if (dtdUri != null) {
              final XmlFile xmlFile = XmlUtil.findXmlFile(XmlUtil.getContainingFile(element), dtdUri);
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
        final XmlTag rootTag = ((XmlFile)targetElement).getDocument().getRootTag();

        if (rootTag != null) {
          final XmlElementDescriptor descriptor = rootTag.getDescriptor();

            if (descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor)) {
              final XmlFile descriptorFile = (XmlFile)descriptor.getDeclaration().getContainingFile();

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

      return new CachedValueProvider.Result<XmlEntityDecl>(result[0], deps.toArray(new Object[deps.size()]));
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
    return ResolveUtil.getReferencesFromProviders(this,XmlEntityRef.class);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitXmlElement(this);
  }

  public static void copyEntityCaches(final PsiFile file, final PsiFile context) {
    synchronized (PsiLock.LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = getCachingMap(file);
      for(Map.Entry<String,CachedValue<XmlEntityDecl>> entry:getCachingMap(context).entrySet()) {
        cachingMap.put(entry.getKey(), entry.getValue());
      }
    }

  }
}
