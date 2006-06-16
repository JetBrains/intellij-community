package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.XmlEncodingReferenceProvider;
import com.intellij.codeInsight.i18n.I18nUtil;
import com.intellij.javaee.web.WebUtil;
import com.intellij.lang.properties.PropertiesReferenceProvider;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.filters.position.TokenTypeFilter;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.jsp.el.impl.ELLiteralManipulator;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.jsp.el.ELLiteralExpression;
import com.intellij.psi.xml.*;
import com.intellij.util.Function;
import com.intellij.xml.util.HtmlReferenceProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public class ReferenceProvidersRegistry implements ProjectComponent {
  private final List<Class> myTempScopes = new ArrayList<Class>();
  private final Map<Class,ProviderBinding> myBindingsMap = new HashMap<Class,ProviderBinding>();
  private final List<Pair<Class<?>, ElementManipulator<?>>> myManipulators = new ArrayList<Pair<Class<?>, ElementManipulator<?>>>();
  private final Map<ReferenceProviderType,PsiReferenceProvider> myReferenceTypeToProviderMap = new HashMap<ReferenceProviderType, PsiReferenceProvider>(5);

  private static final Logger LOG = Logger.getInstance("ReferenceProvidersRegistry");

  static public class ReferenceProviderType {
    private String myId;
    public ReferenceProviderType(@NonNls String id) { myId = id; }
    public String toString() { return myId; }
  }

  public static ReferenceProviderType PROPERTIES_FILE_KEY_PROVIDER = new ReferenceProviderType("Properties File Key Provider");
  public static ReferenceProviderType CLASS_REFERENCE_PROVIDER = new ReferenceProviderType("Class Reference Provider");
  public static ReferenceProviderType PATH_REFERENCES_PROVIDER = new ReferenceProviderType("Path References Provider");
  public static ReferenceProviderType DYNAMIC_PATH_REFERENCES_PROVIDER = new ReferenceProviderType("Dynamic Path References Provider");
  public static ReferenceProviderType CSS_CLASS_OR_ID_KEY_PROVIDER = new ReferenceProviderType("Css Class or ID Provider");
  public static ReferenceProviderType URI_PROVIDER = new ReferenceProviderType("Uri references provider");
  public static ReferenceProviderType SCHEMA_PROVIDER = new ReferenceProviderType("Schema references provider");

  public static ReferenceProvidersRegistry getInstance(@NotNull Project project) {
    return project.getComponent(ReferenceProvidersRegistry.class);
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
    registerManipulator(ELLiteralExpression.class, new ELLiteralManipulator());
    // Binding declarations

    myReferenceTypeToProviderMap.put(CLASS_REFERENCE_PROVIDER, new JavaClassReferenceProvider());
    myReferenceTypeToProviderMap.put(PATH_REFERENCES_PROVIDER, new JspxIncludePathReferenceProvider());
    myReferenceTypeToProviderMap.put(DYNAMIC_PATH_REFERENCES_PROVIDER, new JspxDynamicPathReferenceProvider());
    myReferenceTypeToProviderMap.put(PROPERTIES_FILE_KEY_PROVIDER, new PropertiesReferenceProvider());

    registerXmlAttributeValueReferenceProvider(
      new String[]{"class", "type"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new TextFilter("useBean"),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ), 2
        )
      ), getProviderByType(CLASS_REFERENCE_PROVIDER)
    );

    // RegisterInPsi.referenceProviders(this);

    registerXmlAttributeValueReferenceProvider(
      new String[]{"extends"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.page")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("page")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ), getProviderByType(CLASS_REFERENCE_PROVIDER)
    );

    final CustomizableReferenceProvider classReferenceProvider = (CustomizableReferenceProvider)getProviderByType(CLASS_REFERENCE_PROVIDER);
    final CustomizingReferenceProvider qualifiedClassReferenceProvider = new CustomizingReferenceProvider(classReferenceProvider);
    qualifiedClassReferenceProvider.addCustomization(JavaClassReferenceProvider.RESOLVE_QUALIFIED_CLASS_NAME, true);

    registerXmlAttributeValueReferenceProvider(
      new String[]{"type"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.attribute")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("attribute")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ),
      qualifiedClassReferenceProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"variable-class"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.variable")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("variable")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ),
      qualifiedClassReferenceProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] { "import" },
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.page")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("page")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ),
      new JspImportListReferenceProvider()
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"errorPage"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new OrFilter(
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("page")
              ),
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.page")
              ))
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"file"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new OrFilter(
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("include")
              ),
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.include")
              ))
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    final CustomizableReferenceProvider dynamicPathReferenceProvider = (CustomizableReferenceProvider)getProviderByType(DYNAMIC_PATH_REFERENCES_PROVIDER);
    final CustomizingReferenceProvider dynamicPathReferenceProviderNoEmptyFileReferencesAtEnd = new CustomizingReferenceProvider(dynamicPathReferenceProvider);
    dynamicPathReferenceProviderNoEmptyFileReferencesAtEnd.addCustomization(
      JspxDynamicPathReferenceProvider.ALLOW_REFERENCING_DIR_WITH_END_SLASH,
      true
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"value"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSTL_CORE_URIS),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("url")
            )
          ), 2
        )
      ), dynamicPathReferenceProviderNoEmptyFileReferencesAtEnd
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"url"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSTL_CORE_URIS),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new OrFilter(
                new TextFilter("import"),
                new TextFilter("redirect")
              )
            )
          ), 2
        )
      ), dynamicPathReferenceProviderNoEmptyFileReferencesAtEnd
    );

    PsiReferenceProvider propertiesReferenceProvider = getProviderByType(PROPERTIES_FILE_KEY_PROVIDER);
    registerXmlAttributeValueReferenceProvider(
      new String[]{"key"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new NamespaceFilter(XmlUtil.JSTL_FORMAT_URIS),
              new NamespaceFilter(XmlUtil.STRUTS_BEAN_URI)
            ),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("message")
            )
          ), 2
        )
      ), propertiesReferenceProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"altKey","titleKey","pageKey","srcKey"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.STRUTS_HTML_URI),
            new ClassFilter(XmlTag.class)
          ), 2
        )
      ), propertiesReferenceProvider
    );

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

    registerXmlAttributeValueReferenceProvider(
      new String[]{"page"},
      new ScopeFilter(
        new ParentElementFilter(
          new OrFilter(
            new AndFilter(
              new NamespaceFilter(XmlUtil.JSP_URI),
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("include","forward")
              )
            ),
            new AndFilter(
              new NamespaceFilter(XmlUtil.STRUTS_HTML_URI),
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("rewrite")
              )
            )
          ), 2
        )
      ), getProviderByType(DYNAMIC_PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"template", "src"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.FACELETS_URI),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter(new String[] { "decorate","composition","include"})
            )
          ), 2
        )
      ), getProviderByType(DYNAMIC_PATH_REFERENCES_PROVIDER)
    );

    final PsiReferenceProvider pathReferenceProvider = getProviderByType(PATH_REFERENCES_PROVIDER);
    final CustomizingReferenceProvider webXmlPathReferenceProvider = new CustomizingReferenceProvider(
      (CustomizableReferenceProvider)pathReferenceProvider
    );

    webXmlPathReferenceProvider.addCustomization(
      FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION,
      new Function<PsiFile, PsiElement>() {
        public PsiElement fun(final PsiFile file) {
          return FileReferenceSet.getAbsoluteTopLevelDirLocation(
            WebUtil.getWebModuleProperties(file),
            file.getProject(),
            file
          );
        }
      }
    );

    final NamespaceFilter webAppNSFilter = new NamespaceFilter(XmlUtil.WEB_XML_URIS);
    registerXmlTagReferenceProvider(
      new String[]{"welcome-file","location","taglib-location"}, webAppNSFilter,
      true,
      webXmlPathReferenceProvider
    );

    registerXmlTagReferenceProvider(
      new String[]{"small-icon","large-icon"}, webAppNSFilter,
      true,
      getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"tagdir"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new AndFilter(
              new ClassFilter(JspDirective.class),
              new TextFilter("taglib")
            )
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] { "uri" },
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new AndFilter(
              new ClassFilter(JspDirective.class),
              new TextFilter("taglib")
            )
          ), 2
        )
      ),
      new JspUriReferenceProvider()
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

    registerXmlTagReferenceProvider(
      new String[] {
        "function-class", "tag-class", "tei-class", "variable-class", "type", "path",
        "function-signature", "name", "name-given"
      },
      new NamespaceFilter(MetaRegistry.TAGLIB_URIS),
      true,
      new TaglibReferenceProvider( getProviderByType(CLASS_REFERENCE_PROVIDER) )
    );

    final NamespaceFilter jsfNsFilter = new NamespaceFilter(XmlUtil.JSF_URIS);
    /*
    registerXmlTagReferenceProvider(
      new String[] {
        "render-kit-class","renderer-class","managed-bean-class","attribute-class","component-class",
        "converter-for-class", "converter-class", "key-class", "value-class",
        "referenced-bean-class", "validator-class", "application-factory", "faces-context-factory",
        "render-kit-factory", "lifecycle-factory", "view-handler", "variable-resolver", "phase-listener",
        "property-resolver", "state-manager", "action-listener", "navigation-handler"
      },
      jsfNsFilter,
      true,
      getProviderByType(CLASS_REFERENCE_PROVIDER)
    );
    */

    final JSFReferencesProvider jsfProvider = new JSFReferencesProvider(getProviderByType(DYNAMIC_PATH_REFERENCES_PROVIDER));

    registerXmlTagReferenceProvider(
      jsfProvider.getTagNames(),
      jsfNsFilter,
      true,
      jsfProvider
    );

    registerXmlAttributeValueReferenceProvider(
      jsfProvider.getAttributeNames(),
      new ParentElementFilter( new NamespaceFilter(XmlUtil.JSF_HTML_URI), 2),
      true,
      jsfProvider
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
      new String[] {
        "src", "href", "action", "background", "width", "height", "type", "bgcolor", "color", "vlink",
        "link", "alink", "text", "name", "id", "class"
      },
      provider.getFilter(),
      false,
      provider
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
      new String[] {"ref","type","base","name","substitutionGroup","memberTypes","value"},
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
      new String[] {"xsi:noNamespaceSchemaLocation"},
      null,
      uriProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"schemaLocation"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(MetaRegistry.SCHEMA_URIS),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter(new String[] {"import","include","redefine"})
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

    final JspReferencesProvider jspReferencesProvider = new JspReferencesProvider();

    registerXmlAttributeValueReferenceProvider(
      new String[] {"fragment","name","property","id","name-given","dynamic-attributes","name-from-attribute"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new ClassFilter(XmlTag.class),
            new NamespaceFilter(
              new String[] {
                XmlUtil.JSP_URI,
                XmlUtil.STRUTS_BEAN_URI,
                XmlUtil.STRUTS_LOGIC_URI
              }
            )
          ), 2
        )
      ),
      jspReferencesProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"var"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new ClassFilter(XmlTag.class),
            new NamespaceFilter(XmlUtil.JSTL_CORE_URIS)
          ), 2
        )
      ),
      jspReferencesProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"scope"},
      null,
      jspReferencesProvider
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"name"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new ClassFilter(XmlTag.class),
            new AndFilter(
              new TextFilter("property"),
              new NamespaceFilter(XmlUtil.SPRING_CORE_URIS)
            )
          ), 2
        )
      ),
      new SpringReferencesProvider()
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"name"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new ClassFilter(XmlTag.class),
            new AndFilter(
              new NamespaceFilter(XmlUtil.HIBERNATE_URIS),
              new TextFilter(new String[] { "property","list","map","set", "array", "bag", "idbag", "primitive-array", "many-to-one", "one-to-one"} )
            )
          ), 2
        )
      ),
      new HibernateReferencesProvider()
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] {"encoding"},
      new ScopeFilter(new ParentElementFilter(new ClassFilter(XmlProcessingInstruction.class))),
      new XmlEncodingReferenceProvider()
    );
  }

  public void registerReferenceProvider(@Nullable ElementFilter elementFilter, @NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    if (scope == XmlAttributeValue.class) {
      registerXmlAttributeValueReferenceProvider(null, elementFilter, provider);
      return;
    } else if (scope == XmlTag.class) {
      registerXmlTagReferenceProvider(null, elementFilter, false, provider);
      return;
    }

    final ProviderBinding providerBinding = myBindingsMap.get(scope);
    if (providerBinding != null) {
      ((SimpleProviderBinding)providerBinding).registerProvider(provider, elementFilter);
      return;
    }

    final SimpleProviderBinding binding = new SimpleProviderBinding(scope);
    binding.registerProvider(provider,elementFilter);
    myBindingsMap.put(scope,binding);
  }

  public void registerXmlTagReferenceProvider(@NonNls String[] names, @Nullable ElementFilter elementFilter,
                                              boolean caseSensitive, @NotNull PsiReferenceProvider provider) {
    registerNamedReferenceProvider(names, elementFilter, XmlTagProviderBinding.class,XmlTag.class,caseSensitive, provider);
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
        providerBinding = bindingClass.newInstance();
        myBindingsMap.put(scopeClass, providerBinding);
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

  public @NotNull PsiReferenceProvider getProviderByType(@NotNull ReferenceProviderType type) {
    return myReferenceTypeToProviderMap.get(type);
  }

  public void registerReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(null, scope, provider);
  }

  public @NotNull PsiReferenceProvider[] getProvidersByElement(@NotNull PsiElement element, @NotNull Class clazz) {
    assert clazz.isInstance(element);

    List<PsiReferenceProvider> ret = new ArrayList<PsiReferenceProvider>(1);
    PsiElement current;
    do {
      current = element;

      final ProviderBinding providerBinding = myBindingsMap.get(clazz);
      if (providerBinding != null) providerBinding.addAcceptableReferenceProviders(current, ret);

      element = ResolveUtil.getContext(element);
    }
    while (!isScopeFinal(current.getClass()));

    return ret.size() > 0 ? ret.toArray(new PsiReferenceProvider[ret.size()]) : PsiReferenceProvider.EMPTY_ARRAY;
  }

  public @Nullable <T extends PsiElement> ElementManipulator<T> getManipulator(@NotNull T element) {
    for (final Pair<Class<?>,ElementManipulator<?>> pair : myManipulators) {
      if (pair.getFirst().isAssignableFrom(element.getClass())) {
        return (ElementManipulator<T>)pair.getSecond();
      }
    }

    return null;
  }

  public <T extends PsiElement> void registerManipulator(@NotNull Class<T> elementClass, @NotNull ElementManipulator<T> manipulator) {
    myManipulators.add(new Pair<Class<?>, ElementManipulator<?>>(elementClass, manipulator));
  }

  private boolean isScopeFinal(Class scopeClass) {

    for (final Class aClass : myTempScopes) {
      if (aClass.isAssignableFrom(scopeClass)) {
        return false;
      }
    }
    return true;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public String getComponentName() {
    return "Reference providers registry";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
