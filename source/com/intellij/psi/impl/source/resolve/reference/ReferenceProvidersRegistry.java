package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.encoding.HtmlHttpEquivEncodingReferenceProvider;
import com.intellij.codeInsight.daemon.impl.analysis.encoding.JspEncodingInAttributeReferenceProvider;
import com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider;
import com.intellij.codeInspection.i18n.I18nUtil;
import com.intellij.lang.properties.PropertiesReferenceProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.filters.position.TokenTypeFilter;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.xml.*;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.xml.util.HtmlReferenceProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public class ReferenceProvidersRegistry implements ElementManipulatorsRegistry {
  private final List<Class> myTempScopes = new ArrayList<Class>();
  private final ConcurrentMap<Class,ProviderBinding> myBindingsMap = new ConcurrentWeakHashMap<Class, ProviderBinding>();
  private final List<Pair<Class<?>, ElementManipulator<?>>> myManipulators = new CopyOnWriteArrayList<Pair<Class<?>, ElementManipulator<?>>>();
  private final Map<ReferenceProviderType,PsiReferenceProvider> myReferenceTypeToProviderMap = new ConcurrentHashMap<ReferenceProviderType, PsiReferenceProvider>(5);

  private static final Logger LOG = Logger.getInstance("ReferenceProvidersRegistry");

  public static class ReferenceProviderType {
    private final String myId;
    public ReferenceProviderType(@NonNls String id) { myId = id; }
    public String toString() { return myId; }
  }

  public static ReferenceProviderType PROPERTIES_FILE_KEY_PROVIDER = new ReferenceProviderType("Properties File Key Provider");
  public static ReferenceProviderType CLASS_REFERENCE_PROVIDER = new ReferenceProviderType("Class Reference Provider");
  public static ReferenceProviderType CSS_CLASS_OR_ID_KEY_PROVIDER = new ReferenceProviderType("Css Class or ID Provider");
  public static ReferenceProviderType URI_PROVIDER = new ReferenceProviderType("Uri references provider");
  public static ReferenceProviderType SCHEMA_PROVIDER = new ReferenceProviderType("Schema references provider");

  public static ReferenceProvidersRegistry getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ReferenceProvidersRegistry.class);
  }

  public void registerTypeWithProvider(@NotNull ReferenceProviderType type, @NotNull PsiReferenceProvider provider) {
    myReferenceTypeToProviderMap.put(type, provider);
  }

  private ReferenceProvidersRegistry() {
    // Temp scopes declarations
    myTempScopes.add(PsiIdentifier.class);

    // Manipulators mapping
    registerManipulator(XmlAttributeValue.class, new XmlAttributeValueManipulator());
    registerManipulator(PsiPlainTextFile.class, new PlainFileManipulator());
    registerManipulator(XmlToken.class, new XmlTokenManipulator());
    registerManipulator(PsiLiteralExpression.class, new StringLiteralManipulator());
    registerManipulator(XmlTag.class, new XmlTagValueManipulator());
    registerManipulator(PsiDocTag.class, new PsiDocTagValueManipulator());

    // Binding declarations

    myReferenceTypeToProviderMap.put(CLASS_REFERENCE_PROVIDER, new JavaClassReferenceProvider());

    PsiReferenceProvider propertiesReferenceProvider = new PropertiesReferenceProvider(false);
    myReferenceTypeToProviderMap.put(PROPERTIES_FILE_KEY_PROVIDER, propertiesReferenceProvider);

    registerXmlAttributeValueReferenceProvider(
      new String[]{"code"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.SPRING_URI),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("message", "theme")
            )
          ), 2
        )
      ), propertiesReferenceProvider
    );

    final JavaClassListReferenceProvider classListProvider = new JavaClassListReferenceProvider();
    registerXmlAttributeValueReferenceProvider(null,
    new AndFilter(
      new NotFilter(
        new ParentElementFilter(
          new NamespaceFilter(XmlUtil.ANT_URI), 2)),
      new NotFilter(
      new ScopeFilter(new ParentElementFilter(
        new AndFilter(
          new OrFilter(
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("directive.page")),
            new AndFilter(
              new ClassFilter(JspDirective.class),
              new TextFilter("page"))),
          new NamespaceFilter(XmlUtil.JSP_URI)), 2)))), classListProvider);

    registerReferenceProvider(new TokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS), XmlToken.class,
                              classListProvider);

    final IdReferenceProvider idReferenceProvider = new IdReferenceProvider();

    registerXmlAttributeValueReferenceProvider(
      idReferenceProvider.getIdForAttributeNames(),
      idReferenceProvider.getIdForFilter(),
      true,
      idReferenceProvider
    );

    final DtdReferencesProvider dtdReferencesProvider = new DtdReferencesProvider();
    //registerReferenceProvider(null, XmlEntityDecl.class,dtdReferencesProvider);
    registerReferenceProvider(null, XmlEntityRef.class,dtdReferencesProvider);
    registerReferenceProvider(null, XmlDoctype.class,dtdReferencesProvider);
    registerReferenceProvider(null, XmlElementDecl.class,dtdReferencesProvider);
    registerReferenceProvider(null, XmlAttlistDecl.class,dtdReferencesProvider);
    registerReferenceProvider(null, XmlElementContentSpec.class,dtdReferencesProvider);
    registerReferenceProvider(null, XmlToken.class,dtdReferencesProvider);

    URIReferenceProvider uriProvider = new URIReferenceProvider();

    registerTypeWithProvider(URI_PROVIDER,uriProvider);
    registerXmlAttributeValueReferenceProvider(
      null,
      dtdReferencesProvider.getSystemReferenceFilter(),
      uriProvider
    );

    //registerReferenceProvider(PsiPlainTextFile.class, new JavaClassListReferenceProvider());

    HtmlReferenceProvider provider = new HtmlReferenceProvider();
    registerXmlAttributeValueReferenceProvider(
      provider.getAttributeValues(),
      provider.getFilter(),
      false,
      provider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] { "href" },
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("include")
            ),
            new NamespaceFilter(XmlUtil.XINCLUDE_URI)
          ),
          2
        )
      ),
      true,
      new JspxIncludePathReferenceProvider()
    );

    final PsiReferenceProvider filePathReferenceProvider = new FilePathReferenceProvider();
    registerReferenceProvider(
      new ElementFilter() {
        public boolean isAcceptable(Object element, PsiElement context) {
          if (context instanceof PsiLiteralExpression) {
            PsiLiteralExpression literalExpression = (PsiLiteralExpression) context;
            final Map<String, Object> annotationParams = new HashMap<String, Object>();
            annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
            if (I18nUtil.mustBePropertyKey(literalExpression, annotationParams)) {
              return false;
            }
          }
          return true;
        }

        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      }, PsiLiteralExpression.class, filePathReferenceProvider);

    final SchemaReferencesProvider schemaReferencesProvider = new SchemaReferencesProvider();
    registerTypeWithProvider(SCHEMA_PROVIDER, schemaReferencesProvider);

    registerXmlAttributeValueReferenceProvider(
      schemaReferencesProvider.getCandidateAttributeNamesForSchemaReferences(),
      new ScopeFilter(
        new ParentElementFilter(
          new NamespaceFilter(MetaRegistry.SCHEMA_URIS), 2
        )
      ),
      schemaReferencesProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"xsi:type"},
      null,
      schemaReferencesProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"xsi:noNamespaceSchemaLocation","xsi:schemaLocation"},
      null,
      uriProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"schemaLocation","namespace"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(MetaRegistry.SCHEMA_URIS),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("import","include","redefine")
            )
          ), 2
        )
      ),
      uriProvider
    );

    registerXmlAttributeValueReferenceProvider(
      null,
      uriProvider.getNamespaceAttributeFilter(),
      uriProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"content"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new ClassFilter(XmlTag.class),
            new TextFilter("meta")
          ), 2
        )
      ),
      new HtmlHttpEquivEncodingReferenceProvider()
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"encoding"},
      new ScopeFilter(new ParentElementFilter(new ClassFilter(XmlProcessingInstruction.class))),
      new XmlEncodingReferenceProvider()
    );
    registerXmlAttributeValueReferenceProvider(new String[]{"contentType", "pageEncoding",},
                                               new ScopeFilter(new ParentElementFilter(new NamespaceFilter(XmlUtil.JSP_URI), 2)),
                                               new JspEncodingInAttributeReferenceProvider());
  }

  public void registerReferenceProvider(@Nullable ElementFilter elementFilter,
                                                     @NotNull Class scope,
                                                     @NotNull PsiReferenceProvider provider) {
    if (scope == XmlAttributeValue.class) {
      registerXmlAttributeValueReferenceProvider(null, elementFilter, provider);
      return;
    }
    else if (scope == XmlTag.class) {
      registerXmlTagReferenceProvider(null, elementFilter, false, provider);
      return;
    }

    for (;;) {
      final ProviderBinding providerBinding = myBindingsMap.get(scope);
      if (providerBinding != null) {
        ((SimpleProviderBinding)providerBinding).registerProvider(provider, elementFilter);
        return;
      }

      final SimpleProviderBinding binding = new SimpleProviderBinding(scope);
      binding.registerProvider(provider, elementFilter);
      if (myBindingsMap.putIfAbsent(scope, binding) == null) break;
    }
  }

  public void unregisterReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    final ProviderBinding providerBinding = myBindingsMap.get(scope);
    ((SimpleProviderBinding)providerBinding).unregisterProvider(provider, null);
  }

  public void registerXmlTagReferenceProvider(@NonNls String[] names, @Nullable ElementFilter elementFilter,
                                              boolean caseSensitive, @NotNull PsiReferenceProvider provider) {
    registerNamedReferenceProvider(names, elementFilter, XmlTagProviderBinding.class,XmlTag.class,caseSensitive, provider);
  }

  public void registerDocTagReferenceProvider(@NonNls String[] names, @Nullable ElementFilter elementFilter,
                                              boolean caseSensitive, @NotNull PsiReferenceProvider provider) {
    registerNamedReferenceProvider(names, elementFilter, PsiDocTagProviderBinding.class, PsiDocTag.class, caseSensitive, provider);
  }

  private void registerNamedReferenceProvider(
    @Nullable @NonNls String[] names,
    @Nullable ElementFilter elementFilter,
    @NotNull Class<? extends NamedObjectProviderBinding> bindingClass,
    @NotNull Class scopeClass,
    boolean caseSensitive,
    @NotNull PsiReferenceProvider provider
  ) {
    NamedObjectProviderBinding providerBinding = (NamedObjectProviderBinding)myBindingsMap.get(scopeClass);

    if (providerBinding == null) {
      try {
        providerBinding = (NamedObjectProviderBinding)ConcurrencyUtil.cacheOrGet(myBindingsMap, scopeClass, bindingClass.newInstance());
      }
      catch (Exception e) {
        LOG.error(e);
        return;
      }
    }

    providerBinding.registerProvider(
      names,
      elementFilter,
      caseSensitive,
      provider
    );
  }

  public void registerXmlAttributeValueReferenceProvider(@Nullable @NonNls String[] attributeNames,
                                                         @Nullable ElementFilter elementFilter,
                                                         boolean caseSensitive,
                                                         @NotNull PsiReferenceProvider provider) {
    registerNamedReferenceProvider(
      attributeNames,
      elementFilter,
      XmlAttributeValueProviderBinding.class,
      XmlAttributeValue.class,
      caseSensitive,
      provider
    );
  }

  public void registerXmlAttributeValueReferenceProvider(@Nullable @NonNls String[] attributeNames,
                                                         @Nullable ElementFilter elementFilter,
                                                         @NotNull PsiReferenceProvider provider) {
    registerXmlAttributeValueReferenceProvider(attributeNames, elementFilter, true, provider);
  }

  @Nullable
  public PsiReferenceProvider getProviderByType(@NotNull ReferenceProviderType type) {
    return myReferenceTypeToProviderMap.get(type);
  }

  public void registerReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(null, scope, provider);
  }

  @NotNull
  public PsiReferenceProvider[] getProvidersByElement(@NotNull PsiElement element, @NotNull Class clazz) {
    assert ReflectionCache.isInstance(element, clazz);

    List<PsiReferenceProvider> ret = new ArrayList<PsiReferenceProvider>(1);
    PsiElement current;
    do {
      current = element;

      final ProviderBinding providerBinding = myBindingsMap.get(clazz);
      if (providerBinding != null) providerBinding.addAcceptableReferenceProviders(current, ret);

      element = ResolveUtil.getContext(element);
    }
    while (!isScopeFinal(current.getClass()));

    return ret.isEmpty() ? PsiReferenceProvider.EMPTY_ARRAY : ret.toArray(new PsiReferenceProvider[ret.size()]);
  }

  @Nullable
  public <T extends PsiElement> ElementManipulator<T> getManipulator(@NotNull T element) {
    return (ElementManipulator<T>)getManipulator(element.getClass());
  }

  @Nullable
  public <T extends PsiElement> ElementManipulator<T> getManipulator(@NotNull final Class<T> elementClass) {
    for (final Pair<Class<?>,ElementManipulator<?>> pair : myManipulators) {
      if (ReflectionCache.isAssignable(pair.getFirst(), elementClass)) {
        return (ElementManipulator<T>)pair.getSecond();
      }
    }

    return null;
  }

  public int getOffsetInElement(final PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = getManipulator(element);
    assert manipulator != null;
    return manipulator.getRangeInElement(element).getStartOffset();
  }

  public <T extends PsiElement> void registerManipulator(@NotNull Class<T> elementClass, @NotNull ElementManipulator<T> manipulator) {
    myManipulators.add(new Pair<Class<?>, ElementManipulator<?>>(elementClass, manipulator));
  }

  private boolean isScopeFinal(Class scopeClass) {
    for (final Class aClass : myTempScopes) {
      if (ReflectionCache.isAssignable(aClass, scopeClass)) {
        return false;
      }
    }
    return true;
  }

}
