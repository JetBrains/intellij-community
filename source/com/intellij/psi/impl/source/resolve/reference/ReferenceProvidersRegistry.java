package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.encoding.HtmlHttpEquivEncodingReferenceProvider;
import com.intellij.codeInsight.daemon.impl.analysis.encoding.JspEncodingInAttributeReferenceProvider;
import com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider;
import com.intellij.codeInspection.i18n.I18nUtil;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.properties.PropertiesReferenceProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.*;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.XmlPatterns.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.filters.position.TokenTypeFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlReferenceProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public class ReferenceProvidersRegistry implements ElementManipulatorsRegistry, PsiReferenceRegistrar {

  private final ConcurrentMap<Class,SimpleProviderBinding> myBindingsMap = new ConcurrentWeakHashMap<Class, SimpleProviderBinding>();
  private final ConcurrentMap<Class,NamedObjectProviderBinding> myNamedBindingsMap = new ConcurrentWeakHashMap<Class, NamedObjectProviderBinding>();
  private final List<Pair<Class<?>, ElementManipulator<?>>> myManipulators = new CopyOnWriteArrayList<Pair<Class<?>, ElementManipulator<?>>>();
  private final Map<ReferenceProviderType,PsiReferenceProvider> myReferenceTypeToProviderMap = new ConcurrentHashMap<ReferenceProviderType, PsiReferenceProvider>(5);

  private static final Comparator<Trinity<PsiReferenceProvider,MatchingContext,Double>> PRIORITY_COMPARATOR = new Comparator<Trinity<PsiReferenceProvider, MatchingContext, Double>>() {
    public int compare(final Trinity<PsiReferenceProvider, MatchingContext, Double> o1,
                       final Trinity<PsiReferenceProvider, MatchingContext, Double> o2) {
      return o2.getThird().compareTo(o1.getThird());
    }
  };

  public static class ReferenceProviderType {
    private final String myId;
    public ReferenceProviderType(@NonNls String id) { myId = id; }
    public String toString() { return myId; }
  }

  public static final ReferenceProviderType PROPERTIES_FILE_KEY_PROVIDER = new ReferenceProviderType("Properties File Key Provider");
  public static final ReferenceProviderType CLASS_REFERENCE_PROVIDER = new ReferenceProviderType("Class Reference Provider");
  public static final ReferenceProviderType CSS_CLASS_OR_ID_KEY_PROVIDER = new ReferenceProviderType("Css Class or ID Provider");
  private static final ReferenceProviderType URI_PROVIDER = new ReferenceProviderType("Uri references provider");
  private static final ReferenceProviderType SCHEMA_PROVIDER = new ReferenceProviderType("Schema references provider");

  public static ReferenceProvidersRegistry getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ReferenceProvidersRegistry.class);
  }

  public void registerTypeWithProvider(@NotNull ReferenceProviderType type, @NotNull PsiReferenceProvider provider) {
    myReferenceTypeToProviderMap.put(type, provider);
  }

  private ReferenceProvidersRegistry() {
    // Manipulators mapping
    registerManipulator(XmlAttributeValue.class, new XmlAttributeValueManipulator());
    registerManipulator(XmlAttribute.class, new XmlAttributeManipulator());
    registerManipulator(PsiPlainTextFile.class, new PlainFileManipulator());
    registerManipulator(XmlToken.class, new XmlTokenManipulator());
    registerManipulator(XmlComment.class, new XmlCommentManipulator());

    registerManipulator(PsiLiteralExpression.class, new StringLiteralManipulator());
    registerManipulator(XmlTag.class, new XmlTagManipulator());
    registerManipulator(PsiDocTag.class, new PsiDocTagValueManipulator());

    // Binding declarations

    myReferenceTypeToProviderMap.put(CLASS_REFERENCE_PROVIDER, new JavaClassReferenceProvider());

    PsiReferenceProvider propertiesReferenceProvider = new PropertiesReferenceProvider(false);
    myReferenceTypeToProviderMap.put(PROPERTIES_FILE_KEY_PROVIDER, propertiesReferenceProvider);

    XmlUtil.registerXmlAttributeValueReferenceProvider(this,
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
    registerReferenceProvider(xmlAttributeValue(), classListProvider, LOWER_PRIORITY);

    registerReferenceProvider(new TokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS) {
      public boolean isAcceptable(final Object element, final PsiElement context) {
        final boolean acceptable = super.isAcceptable(element, context);
        if (acceptable) {
          final Language language = ((XmlToken)element).getContainingFile().getLanguage();
          return language != StdLanguages.JSP && language != StdLanguages.JSPX;
        }
        return false;
      }
    }, XmlToken.class, classListProvider, LOWER_PRIORITY);

    final IdReferenceProvider idReferenceProvider = new IdReferenceProvider();

    final ReferenceProvidersRegistry referenceProvidersRegistry3 = ReferenceProvidersRegistry.this;
    String[] attributeNames3 = idReferenceProvider.getIdForAttributeNames();
    ElementFilter elementFilter3 = idReferenceProvider.getIdForFilter();
    boolean caseSensitive2 = true;
    PsiReferenceProvider provider4 = idReferenceProvider;
    XmlUtil.registerXmlAttributeValueReferenceProvider(this, attributeNames3, elementFilter3, caseSensitive2, provider4);

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
    XmlUtil.registerXmlAttributeValueReferenceProvider(this,
      null,
      dtdReferencesProvider.getSystemReferenceFilter(),
      uriProvider
    );

    //registerReferenceProvider(PsiPlainTextFile.class, new JavaClassListReferenceProvider());

    HtmlReferenceProvider provider = new HtmlReferenceProvider();
    final ReferenceProvidersRegistry referenceProvidersRegistry2 = ReferenceProvidersRegistry.this;
    String[] attributeNames2 = HtmlReferenceProvider.getAttributeValues();
    ElementFilter elementFilter2 = HtmlReferenceProvider.getFilter();
    boolean caseSensitive1 = false;
    PsiReferenceProvider provider3 = provider;
    XmlUtil.registerXmlAttributeValueReferenceProvider(this, attributeNames2, elementFilter2, caseSensitive1, provider3);

    XmlUtil.registerXmlAttributeValueReferenceProvider(this, new String[] { "href" }, new ScopeFilter(
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
    ), true, uriProvider);

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

    XmlUtil.registerXmlAttributeValueReferenceProvider(this,
      schemaReferencesProvider.getCandidateAttributeNamesForSchemaReferences(),
      new ScopeFilter(
        new ParentElementFilter(
          new NamespaceFilter(XmlUtil.SCHEMA_URIS), 2
        )
      ),
      schemaReferencesProvider
    );

    registerReferenceProvider(xmlAttributeValue(xmlAttribute().withNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI)).
      withLocalName("type"), schemaReferencesProvider);

    registerReferenceProvider(xmlAttributeValue(xmlAttribute().withNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI)).
      withLocalName("noNamespaceSchemaLocation", "schemaLocation"), uriProvider);

    registerReferenceProvider(
      xmlAttributeValue().withLocalName("schemaLocation","namespace").
        withSuperParent(2,
                        xmlTag().withNamespace(XmlUtil.SCHEMA_URIS).withLocalName(string().oneOf("import", "include","redefine"))),
      uriProvider);

    String[] attributeNames6 = null;
    ElementFilter elementFilter6 = URIReferenceProvider.ELEMENT_FILTER;
    PsiReferenceProvider provider7 = uriProvider;
    XmlUtil.registerXmlAttributeValueReferenceProvider(this, attributeNames6, elementFilter6, true, provider7);

    String[] attributeNames5 = new String[] {"content"};
    ElementFilter elementFilter5 = new ScopeFilter(
      new ParentElementFilter(
        new AndFilter(
          new ClassFilter(XmlTag.class),
          new TextFilter("meta")
        ), 2
      )
    );
    XmlUtil.registerXmlAttributeValueReferenceProvider(this, attributeNames5, elementFilter5, true, new HtmlHttpEquivEncodingReferenceProvider());

    PsiReferenceProvider provider5 = new XmlEncodingReferenceProvider();
    XmlUtil.registerXmlAttributeValueReferenceProvider(this, new String[] {"encoding"}, new ScopeFilter(new ParentElementFilter(new ClassFilter(XmlProcessingInstruction.class))), true, provider5);
    String[] attributeNames = new String[]{"contentType", "pageEncoding",};
    ElementFilter elementFilter = new ScopeFilter(new ParentElementFilter(new NamespaceFilter(XmlUtil.JSP_URI), 2));
    PsiReferenceProvider provider1 = new JspEncodingInAttributeReferenceProvider();
    XmlUtil.registerXmlAttributeValueReferenceProvider(this, attributeNames, elementFilter, true, provider1);
  }

  public void registerReferenceProvider(@Nullable ElementFilter elementFilter,
                                        @NotNull Class scope,
                                        @NotNull PsiReferenceProvider provider,
                                        double priority) {
    registerReferenceProvider(PlatformPatterns.psiElement(scope).and(new FilterPattern(elementFilter)), provider, priority);
  }


  public void registerReferenceProvider(@NotNull ElementPattern<? extends PsiElement> pattern, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(pattern, provider, DEFAULT_PRIORITY);
  }
  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PsiReferenceProvider provider, double priority) {
    final Class scope = pattern.getCondition().getInitialCondition().getAcceptedClass();
    final PsiNamePatternCondition<?> nameCondition = ContainerUtil.findInstance(pattern.getCondition().getConditions(), PsiNamePatternCondition.class);
    if (nameCondition != null) {
      final ValuePatternCondition<String> valueCondition =
        ContainerUtil.findInstance(nameCondition.getNamePattern().getCondition().getConditions(), ValuePatternCondition.class);
      if (valueCondition != null) {
        final Collection<String> strings = valueCondition.getValues();
        registerNamedReferenceProvider(strings.toArray(new String[strings.size()]), new NamedObjectProviderBinding(scope) {
          protected String getName(final PsiElement position) {
            return nameCondition.getPropertyValue(position);
          }
        }, scope, true, provider, priority, pattern);
        return;
      }

      final CaseInsensitiveValuePatternCondition ciCondition =
        ContainerUtil.findInstance(nameCondition.getNamePattern().getCondition().getConditions(), CaseInsensitiveValuePatternCondition.class);
      if (ciCondition != null) {
        registerNamedReferenceProvider(ciCondition.getValues(), new NamedObjectProviderBinding(scope) {
          @Nullable
          protected String getName(final PsiElement position) {
            return nameCondition.getPropertyValue(position);
          }
        }, scope, false, provider, priority, pattern);
        return;
      }
    }


    while (true) {
      final SimpleProviderBinding providerBinding = myBindingsMap.get(scope);
      if (providerBinding != null) {
        providerBinding.registerProvider(provider, pattern, priority);
        return;
      }

      final SimpleProviderBinding binding = new SimpleProviderBinding(scope);
      binding.registerProvider(provider, pattern, priority);
      if (myBindingsMap.putIfAbsent(scope, binding) == null) break;
    }
  }

  public void registerReferenceProvider(@Nullable ElementFilter elementFilter,
                                                     @NotNull Class scope,
                                                     @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(elementFilter, scope, provider, DEFAULT_PRIORITY);
  }

  public void unregisterReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    final ProviderBinding providerBinding = myBindingsMap.get(scope);
    providerBinding.unregisterProvider(provider);
  }


  private void registerNamedReferenceProvider(final String[] names, final NamedObjectProviderBinding binding,
                                              final Class scopeClass,
                                              final boolean caseSensitive,
                                              final PsiReferenceProvider provider, final double priority, final ElementPattern pattern) {
    NamedObjectProviderBinding providerBinding = myNamedBindingsMap.get(scopeClass);

    if (providerBinding == null) {
      providerBinding = ConcurrencyUtil.cacheOrGet(myNamedBindingsMap, scopeClass, binding);
    }

    providerBinding.registerProvider(names, pattern, caseSensitive, provider, priority);
  }

  @Nullable
  public PsiReferenceProvider getProviderByType(@NotNull ReferenceProviderType type) {
    return myReferenceTypeToProviderMap.get(type);
  }

  public void registerReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(null, scope, provider);
  }

  @Deprecated
  public List<PsiReferenceProvider> getProvidersByElement(@NotNull PsiElement element, @NotNull Class clazz) {
    final List<Trinity<PsiReferenceProvider, MatchingContext, Double>> list = getPairsByElement(element, clazz);
    final ArrayList<PsiReferenceProvider> providers = new ArrayList<PsiReferenceProvider>(list.size());
    for (Trinity<PsiReferenceProvider, MatchingContext, Double> trinity : list) {
      providers.add(trinity.getFirst());
    }
    return providers;
  }

  @NotNull
  private List<Trinity<PsiReferenceProvider,MatchingContext,Double>> getPairsByElement(@NotNull PsiElement element, @NotNull Class clazz) {
    assert ReflectionCache.isInstance(element, clazz);

    final SimpleProviderBinding simpleBinding = myBindingsMap.get(clazz);
    final NamedObjectProviderBinding namedBinding = myNamedBindingsMap.get(clazz);
    if (simpleBinding == null && namedBinding == null) return Collections.emptyList();

    List<Trinity<PsiReferenceProvider,MatchingContext,Double>> ret = new SmartList<Trinity<PsiReferenceProvider,MatchingContext,Double>>();
    if (simpleBinding != null) {
      simpleBinding.addAcceptableReferenceProviders(element, ret);
    }
    if (namedBinding != null) {
      namedBinding.addAcceptableReferenceProviders(element, ret);
    }
    return ret;
  }

  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull Class clazz){
    assert context.isValid() : "Invalid context: " + context;

    PsiReference[] result = PsiReference.EMPTY_ARRAY;
    final List<Trinity<PsiReferenceProvider, MatchingContext, Double>> providers = getInstance(context.getProject()).getPairsByElement(context, clazz);
    if (providers.isEmpty()) {
      return result;
    }
    Collections.sort(providers, PRIORITY_COMPARATOR);
    final Double maxPriority = providers.get(0).getThird();
    next: for (Trinity<PsiReferenceProvider, MatchingContext, Double> trinity : providers) {
      final PsiReference[] refs = trinity.getFirst().getReferencesByElement(context, trinity.getSecond());
      if (trinity.getThird().equals(maxPriority)) {
        result = ArrayUtil.mergeArrays(
          result, refs,
          PsiReference.class
        );
      } else {
        for (PsiReference ref : refs) {
          for (PsiReference reference : result) {
            if (reference.getRangeInElement().contains(ref.getRangeInElement())) {
              continue next;
            }
          }
        }
        result = ArrayUtil.mergeArrays(
          result, refs,
          PsiReference.class
        );
      }
    }
    return result;
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  public <T extends PsiElement> ElementManipulator<T> getManipulator(@NotNull T element) {
    return (ElementManipulator<T>)getManipulator(element.getClass());
  }

  @SuppressWarnings({"unchecked"})
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
    assert manipulator != null: element.getClass().getName();
    return manipulator.getRangeInElement(element).getStartOffset();
  }

  public <T extends PsiElement> void registerManipulator(@NotNull Class<T> elementClass, @NotNull ElementManipulator<T> manipulator) {
    myManipulators.add(new Pair<Class<?>, ElementManipulator<?>>(elementClass, manipulator));
  }

}
