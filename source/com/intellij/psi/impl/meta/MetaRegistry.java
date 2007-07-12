package com.intellij.psi.impl.meta;

import com.intellij.jsp.impl.RelaxedHtmlFromSchemaNSDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.TargetNamespaceFilter;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.xml.impl.schema.NamedObjectDescriptor;
import com.intellij.xml.impl.schema.SchemaNSDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.05.2003
 * Time: 3:31:09
 * To change this template use Options | File Templates.
 */
public class MetaRegistry extends MetaDataRegistrar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.meta.MetaRegistry");
  private static final List<MyBinding> ourBindings = new ArrayList<MyBinding>();

  public static final String[] SCHEMA_URIS = { XmlUtil.XML_SCHEMA_URI, XmlUtil.XML_SCHEMA_URI2, XmlUtil.XML_SCHEMA_URI3 };

  static {
    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(SCHEMA_URIS),
              new ClassFilter(XmlDocument.class)
          ),
          SchemaNSDescriptor.class
      );

      addMetadataBinding(
          new AndFilter(
            new ClassFilter(XmlTag.class),
            new NamespaceFilter(SCHEMA_URIS),
           new TextFilter("schema")
          ),
          SchemaNSDescriptor.class
      );
    }
    {
      addMetadataBinding(
          new OrFilter(
              new AndFilter(
                  new ContentFilter(
                    new OrFilter(
                      new ClassFilter(XmlElementDecl.class),
                      new ClassFilter(XmlConditionalSection.class)
                    )
                  ),
                  new ClassFilter(XmlDocument.class)
              ),
              new ClassFilter(XmlMarkupDecl.class)
          ),
          com.intellij.xml.impl.dtd.XmlNSDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(new AndFilter(
          new ClassFilter(XmlTag.class),
          new NamespaceFilter(SCHEMA_URIS),
          new TextFilter("element")
      ),
                         XmlElementDescriptorImpl.class);
    }

    {
      addMetadataBinding(
          new AndFilter(
              new ClassFilter(XmlTag.class),
              new NamespaceFilter(SCHEMA_URIS),
              new TextFilter("attribute")
          ),
          XmlAttributeDescriptorImpl.class
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
              new ClassFilter(XmlDocument.class),
              new TargetNamespaceFilter(XmlUtil.XHTML_URI),
              new NamespaceFilter(SCHEMA_URIS)),
          RelaxedHtmlFromSchemaNSDescriptor.class
      );
    }

    {
      addMetadataBinding(new AndFilter(
          new ClassFilter(XmlTag.class),
          new NamespaceFilter(SCHEMA_URIS),
          new TextFilter("complexType","simpleType", "group","attributeGroup")
      ),
                         NamedObjectDescriptor.class);
    }

  }

  private static final Key<CachedValue<PsiMetaDataBase>> META_DATA_KEY = Key.create("META DATA KEY");

  public static void bindDataToElement(final PsiElement element, final PsiMetaDataBase data){
    CachedValue<PsiMetaDataBase> value =
      element.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<PsiMetaDataBase>() {
      public CachedValueProvider.Result<PsiMetaDataBase> compute() {
        data.init(element);
        return new Result<PsiMetaDataBase>(data, data.getDependences());
      }
    });
    element.putUserData(META_DATA_KEY, value);
  }

  public static PsiMetaData getMeta(final PsiElement element) {
    final PsiMetaDataBase base = getMetaBase(element);
    return base instanceof PsiMetaData ? (PsiMetaData)base : null;
  }

  private static UserDataCache<CachedValue<PsiMetaDataBase>, PsiElement, Object> ourCachedMetaCache =
    new UserDataCache<CachedValue<PsiMetaDataBase>, PsiElement, Object>() {
      protected CachedValue<PsiMetaDataBase> compute(final PsiElement element, Object p) {
        return element.getManager().getCachedValuesManager()
        .createCachedValue(new CachedValueProvider<PsiMetaDataBase>() {
          public Result<PsiMetaDataBase> compute() {
            try {
              for (final MyBinding binding : ourBindings) {
                if (binding.myFilter.isClassAcceptable(element.getClass()) && binding.myFilter.isAcceptable(element, element.getParent())) {
                  final PsiMetaDataBase data = binding.myDataClass.newInstance();
                  data.init(element);
                  return new Result<PsiMetaDataBase>(data, data.getDependences());
                }
              }
            } catch (IllegalAccessException iae) {
              throw new RuntimeException(iae);
            }
            catch (InstantiationException ie) {
              throw new RuntimeException(ie);
            }

            return new Result<PsiMetaDataBase>(null, element);
          }
        }, false);
      }
    };
  
  @Nullable
  public static PsiMetaDataBase getMetaBase(final PsiElement element) {
    ProgressManager.getInstance().checkCanceled();
    return ourCachedMetaCache.get(META_DATA_KEY, element, null).getValue();
  }

  public static <T extends PsiMetaDataBase> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass, Disposable parentDisposable) {
    final MyBinding binding = new MyBinding(filter, aMetadataClass);
    addBinding(binding);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        ourBindings.remove(binding);
      }
    });
  }

  public static <T extends PsiMetaDataBase> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass) {
    addBinding(new MyBinding(filter, aMetadataClass));
  }

  private static <T extends PsiMetaDataBase> void addBinding(final MyBinding binding) {
    ourBindings.add(0, binding);
  }

  public <T extends PsiMetaDataBase> void registerMetaData(ElementFilter filter, Class<T> metadataDescriptorClass) {
    addMetadataBinding(filter, metadataDescriptorClass);
  }

  private static class MyBinding {
    ElementFilter myFilter;
    Class<PsiMetaDataBase> myDataClass;

    public <T extends PsiMetaDataBase> MyBinding(ElementFilter filter, Class<T> dataClass) {
      LOG.assertTrue(filter != null);
      LOG.assertTrue(dataClass != null);
      myFilter = filter;
      myDataClass = (Class)dataClass;
    }
  }
}
