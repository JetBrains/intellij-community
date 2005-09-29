package com.intellij.psi.impl.meta;

import com.intellij.ant.impl.dom.impl.RegisterInPsi;
import com.intellij.jsp.impl.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.RootTagFilter;
import com.intellij.psi.filters.position.TargetNamespaceFilter;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlMarkupDecl;
import com.intellij.reference.SoftReference;
import com.intellij.xml.util.XmlUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.05.2003
 * Time: 3:31:09
 * To change this template use Options | File Templates.
 */
public class MetaRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.meta.MetaRegistry");
  private static final List<MyBinding> ourBindings = new ArrayList<MyBinding>();
  public static final String[] TAGLIB_URIS = new String[]{
    XmlUtil.TAGLIB_1_1_URI, 
    XmlUtil.TAGLIB_1_2_a_URI, 
    XmlUtil.TAGLIB_1_2_URI, 
    XmlUtil.TAGLIB_2_0_URI, 
    XmlUtil.TAGLIB_1_2_b_URI,
    XmlUtil.TAGLIB_2_1_URI
  };
  
  public static final String[] SCHEMA_URIS = { XmlUtil.XML_SCHEMA_URI, XmlUtil.XML_SCHEMA_URI2, XmlUtil.XML_SCHEMA_URI3 };
  private static final String[] JSP_URIS = {
    XmlUtil.JSP_URI,
    "http://java.sun.com/products/jsp/dtd/jsp_1_0.dtd",
    "http://java.sun.com/xml/ns/j2ee/jsp_2_0.xsd",
    "http://java.sun.com/dtd/jspxml.xsd",
    "http://java.sun.com/dtd/jspxml.dtd"
  };

  static {
    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(SCHEMA_URIS),
              new ClassFilter(XmlDocument.class)
          ),
          com.intellij.xml.impl.schema.XmlNSDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(
          new OrFilter(
              new AndFilter(
                  new ContentFilter(new ClassFilter(XmlElementDecl.class)),
                  new ClassFilter(XmlDocument.class)
              ),
              new ClassFilter(XmlMarkupDecl.class)
          ),
          com.intellij.xml.impl.dtd.XmlNSDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(new AndFilter(
          new NamespaceFilter(SCHEMA_URIS),
          new TextFilter("element")
      ),
          com.intellij.xml.impl.schema.XmlElementDescriptorImpl.class);
    }

    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(SCHEMA_URIS),
              new TextFilter("attribute")
          ),
          com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(
          new ClassFilter(XmlElementDecl.class),
          com.intellij.xml.impl.dtd.XmlElementDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(
          new ClassFilter(XmlAttributeDecl.class),
          com.intellij.xml.impl.dtd.XmlAttributeDescriptorImpl.class
      );
    }
    
    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(TAGLIB_URIS),
              new TextFilter("taglib")
          ),
          TldDescriptor.class);
    }

    {
      addMetadataBinding(
        new RootTagFilter(
          new AndFilter(
            new NamespaceFilter(TAGLIB_URIS),
            new TextFilter("taglib")
          )),
        TldDescriptor.class);
    }

    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(TAGLIB_URIS),
              new TextFilter("tag")
          ),
          TldTagDescriptor.class);
    }

    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(TAGLIB_URIS),
              new TextFilter("tag-file")
          ),
          TldTagFileDescriptor.class);
    }

    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(TAGLIB_URIS),
              new TextFilter("function")
          ),
          FunctionDescriptor.class);
    }

    {
      addMetadataBinding(
        new OrFilter(
          new AndFilter(
              new NamespaceFilter(TAGLIB_URIS),
              new TextFilter("attribute")
          ),
          new OrFilter(
            new TextFilter("directive.attribute"),
            new AndFilter(
              new ClassFilter(JspDirective.class),
              new TextFilter("attribute")
            )
          ) 
         ),
        TldAttributeDescriptor.class
      );
    }
    
    {
      addMetadataBinding(
        new OrFilter(
          new AndFilter(
              new NamespaceFilter(TAGLIB_URIS),
              new TextFilter("variable")
          ),
          new OrFilter(
            new TextFilter("directive.variable"),
            new AndFilter(
              new ClassFilter(JspDirective.class),
              new TextFilter("variable")
            )
          )
        ),
        TldVariableDescriptor.class
      );
    }

    {
      addMetadataBinding(
          new AndFilter(
              new ClassFilter(XmlDocument.class),
              new TargetNamespaceFilter(JSP_URIS)
          ),
          JspNsDescriptor.class
      );
    }

    {
      addMetadataBinding(
          new AndFilter(
              new ElementFilter[] {
                new ClassFilter(XmlDocument.class),
                new TargetNamespaceFilter(XmlUtil.XHTML_URI),
                new NamespaceFilter(SCHEMA_URIS),
              }
          ),
          RelaxedNsXmlNSDescriptor.class
      );
    }

    {
      addMetadataBinding(new AndFilter(
          new NamespaceFilter(SCHEMA_URIS),
          new TextFilter(new String[] {"complexType","simpleType", "group","attributeGroup" })
      ),
          com.intellij.xml.impl.schema.NamedObjectDescriptor.class);
    }
    
    RegisterInPsi.metaData();
  }

  public static final Key<SoftReference<CachedValue<PsiMetaData>>> META_DATA_KEY = Key.create("META DATA KEY");

  public static final void bindDataToElement(final PsiElement element, final PsiMetaData data){
    SoftReference<CachedValue<PsiMetaData>> value = new SoftReference<CachedValue<PsiMetaData>>(
      element.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<PsiMetaData>() {
      public CachedValueProvider.Result<PsiMetaData> compute() {
        data.init(element);
        return new Result<PsiMetaData>(data, data.getDependences());
      }
    }));
    element.putUserData(META_DATA_KEY, value);
  }

  private final static SoftReference<CachedValue<PsiMetaData>> NULL = new SoftReference<CachedValue<PsiMetaData>>(null);

  public static final PsiMetaData getMeta(final PsiElement element) {
    ProgressManager.getInstance().checkCanceled();
    PsiMetaData ret = null;
    SoftReference<CachedValue<PsiMetaData>> value = element.getUserData(META_DATA_KEY);
    if (value == null || (value != NULL && value.get() == null)) {
      for (final MyBinding binding : ourBindings) {
        try {
          if (binding.myFilter.isClassAcceptable(element.getClass()) && binding.myFilter.isAcceptable(element, element.getParent())) {
            final PsiMetaData data = binding.myDataClass.newInstance();
            final CachedValue<PsiMetaData> cachedValue = element.getManager().getCachedValuesManager()
              .createCachedValue(new CachedValueProvider<PsiMetaData>() {
                public Result<PsiMetaData> compute() {
                  data.init(element);
                  return new Result<PsiMetaData>(data, data.getDependences());
                }
              }, false);
            value = new SoftReference<CachedValue<PsiMetaData>>(cachedValue);
            ret = cachedValue.getValue();
            break;
          }
        }
        catch (IllegalAccessException iae) {
          value = null;
        }
        catch (InstantiationException ie) {
          value = null;
        }
      }
      element.putUserData(META_DATA_KEY, value != null ? value : NULL);
    }
    else if(value != NULL){
      ret = value.get().getValue();
    }

    return ret;
  }

  public static <T extends PsiMetaData> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass) {
    LOG.assertTrue(filter != null);
    LOG.assertTrue(aMetadataClass != null);
    ourBindings.add(0, new MyBinding(filter, aMetadataClass));
  }

  public static void clearMetaForElement(PsiElement element) {
    element.putUserData(META_DATA_KEY, null);
  }

  private static class MyBinding {
    ElementFilter myFilter;
    Class<PsiMetaData> myDataClass;

    public <T extends PsiMetaData> MyBinding(ElementFilter filter, Class<T> dataClass) {
      myFilter = filter;
      myDataClass = (Class)dataClass;
    }
  }
}
