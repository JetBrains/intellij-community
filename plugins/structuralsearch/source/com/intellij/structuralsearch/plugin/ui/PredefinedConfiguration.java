package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NonNls;

/**
 * Template info
 */
public class PredefinedConfiguration extends Configuration {
  private static final String EXPRESSION_TYPE = SSRBundle.message("expressions.category");
  private static final String INTERESTING_TYPE = SSRBundle.message("interesting.category");
  private static final String J2EE_TYPE = SSRBundle.message("j2ee.category");
  private static final String OPERATOR_TYPE = SSRBundle.message("operators.category");
  private static final String CLASS_TYPE = SSRBundle.message("class.category");
  private static final String METADATA_TYPE = SSRBundle.message("metadata.category");
  private static final String MISC_TYPE = SSRBundle.message("misc.category");
  private static final String GENERICS_TYPE = SSRBundle.message("generics.category");
  private static final String HTML_XML = SSRBundle.message("xml_html.category");

  private static PredefinedConfiguration[] infos;

  private Configuration configuration;
  private String category;
  static final Object USER_DEFINED_TYPE = SSRBundle.message("user.defined.category");

  private PredefinedConfiguration(Configuration configuration, String category) {
    this.configuration = configuration;
    this.category = category;
  }

  private static PredefinedConfiguration createSearchTemplateInfo(String name, @NonNls String criteria, String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }
  private static PredefinedConfiguration createSearchTemplateInfo(String name, @NonNls String criteria, String category, FileType fileType) {
    final SearchConfiguration config = new SearchConfiguration();
    config.setPredefined(true);
    config.setName(name);
    config.getMatchOptions().setSearchPattern(criteria);
    config.getMatchOptions().setFileType(fileType);
    MatcherImplUtil.transform( config.getMatchOptions() );

    return new PredefinedConfiguration(config,category);
  }

  private static PredefinedConfiguration createSearchTemplateInfoSimple(String name, @NonNls String criteria, String category) {
    final PredefinedConfiguration info = createSearchTemplateInfo(name,criteria,category);
    info.configuration.getMatchOptions().setRecursiveSearch(false);

    return info;
  }

  String getCategory() {
    return category;
  }

  Configuration getConfiguration() {
    return configuration;
  }

  static PredefinedConfiguration[] getPredefinedTemplates() {
    if (infos == null) {
      infos = createPredefinedTemplates();
    }

    return infos;
  }

  private static PredefinedConfiguration[] createPredefinedTemplates() {
    return new PredefinedConfiguration[] {
      // Expression patterns
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.method.calls"),"'_Instance?.'MethodCall('_Parameter*)",EXPRESSION_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.field.selections"),"'_Instance?.'Field",EXPRESSION_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.array.access"),"'_Field['_Index]",EXPRESSION_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.assignments"),"'_Inst = '_Expr",EXPRESSION_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.casts"),"('_Type)'_Expr",EXPRESSION_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.instanceof"),"'_Expr instanceof '_Type",EXPRESSION_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.string.literals"),"\"'_String\"",EXPRESSION_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.all.expressions.of.some.type"),"'_Expression:[exprtype( SomeType )]",EXPRESSION_TYPE),

      // Operators
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.block.dcls"),"{\n  '_Type+ 'Var+ = '_Init*;\n  '_BlockStatements*;\n}",OPERATOR_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.trys"),"try {\n  '_TryStatement+;\n} catch('_ExceptionType '_ExceptionDcl) {\n  '_CatchStatement*;\n}",OPERATOR_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.ifs"),"if ('_Condition) {\n  '_ThenStatement*;\n} else {\n  '_ElseStatement*;\n}",OPERATOR_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.switches"),"switch('_Condition) {\n  '_Statement*;\n}",OPERATOR_TYPE),

      // Class based
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.methods.of.the.class"),
        "class '_Class { \n  '_ReturnType+ 'MethodName+('_ParameterType* '_Parameter*);\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.fields.of.the.class"),
        "class '_Class { \n  '_FieldType+ 'FieldName+ = '_Init*;\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.all.methods.of.the.class.within.hierarchy"),
        "class '_ { \n  '_ReturnType+ 'MethodName+:* ('_ParameterType* '_Parameter*);\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.all.fields.of.the.class"),
        "class '_Class { \n  '_FieldType+ 'FieldName+:* = '_Init*;\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.instance.fields.of.the.class"),
        "class '_Class { \n  @Modifier(\"Instance\") '_FieldType+ 'FieldName+ = '_Init*;\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.packagelocal.fields.of.the.class"),
        "class '_Class { \n @Modifier(\"packageLocal\") '_FieldType+ 'FieldName+ = '_Init*;\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.constructors.of.the.class"),
        "class 'Class {\n  'Class+('_ParameterType* '_ParameterName*) {\n    '_Statement*;\n  }\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.classes"),
        "class 'Class {}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.direct.subclasses"),
        "class 'Class extends '_Parent: {}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.implementors.of.interface.within.hierarchy"),
        "class 'Class implements 'Interface:* {}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.interfaces"),
        "interface 'Interface {}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.inner.classes"),
        "class '_ {\n  class 'InnerClass+ {}\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.all.inner.classes.within.hierarchy"),
        "class '_Class {\n  class 'InnerClass+:* {}\n}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.anonymous.classes"),
        "new 'AnonymousClass() {}",
        CLASS_TYPE
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.class.implements.two.interfaces"),
        "class 'A implements '_Interface1:[regex( *java\\.lang\\.Cloneable )], '_Interface2:*java\\.io\\.Serializable {\n" +"}",
        CLASS_TYPE
      ),

      // Generics
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.generic.classes"),"class 'GenericClass<'_TypeParameter+> {} ", GENERICS_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.generic.methods"),"class '_Class {\n  <'_TypeParameter+> '_Type+ 'Method+('_ParameterType* '_ParameterDcl*);\n}", GENERICS_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.typed.symbol"),"'Symbol <'_GenericArgument+>", GENERICS_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.generic.casts"),"( '_Type <'_GenericArgument+> ) '_Expr", GENERICS_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.type.var.substitutions.in.intanceof.with.generic.types"),"'_Expr instanceof '_Type <'Substitutions+> ", GENERICS_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.variables.of.generic.types"),"'_Type <'_GenericArgument+>  'Var = 'Init?;", GENERICS_TYPE),

      // Add comments and metadata
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.comments"),"/* 'CommentContent */", METADATA_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.annotated.class"),"/** @'_Tag+ '_TagValue* */\nclass '_Class {\n}", METADATA_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.annotated.methods"),"class '_Class {\n  /** @'_Tag+ '_TagValue* */\n  '_Type+ 'Method+('_ParameterType* '_ParameterDcl*);\n}", METADATA_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.annotated.fields"),"class '_Class {\n  /** @'_Tag+ '_TagValue* */\n  '_Type+ 'Field+ = '_Init*;\n}", METADATA_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.tags"),"/** @'Tag+ '_TagValue* */", METADATA_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.xdoclet.metadata"),"/** @'Tag \n  '_Property+\n*/", METADATA_TYPE),

      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotated.class"),
                               "@'_Annotation( )\n" +
                               "class 'Class {}", METADATA_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotated.fields"),
                               "class '_Class {\n" +
                               "  @'_Annotation+( )\n" +
                               "  '_FieldType+ 'FieldName+ = '_FieldInitial*;\n" +
                               "}", METADATA_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotated.methods"),
                               "class '_Class {\n" +
                               "  @'_Annotation+( )\n" +
                               "  '_MethodType+ 'MethodName+('_ParameterType* '_ParameterName*);\n" +
                               "}", METADATA_TYPE),

      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.not.annotated.methods"),
                                     "class '_Class {\n" +
                                     "  @'_Annotation{0,0}\n" +
                                     "  '_MethodType+ 'MethodName+('_ParameterType* '_ParameterName*);\n" +
                                     "}", METADATA_TYPE),
      
      // J2EE templates
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.struts.1.1.actions"),"public class 'StrutsActionClass extends '_ParentClass*:Action {\n" +
                                                                                                      "  public ActionForward 'AnActionMethod:*execute (ActionMapping '_action,\n" +
                                                                                                      "                                 ActionForm '_form,\n" +
                                                                                                      "                                 HttpServletRequest '_request,\n" +
                                                                                                      "                                 HttpServletResponse '_response);\n" +
                                                                                                      "}",J2EE_TYPE),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.entity.ejb"),"class 'EntityBean implements EntityBean {\n" +
                                                                                              "  EntityContext '_Context?;\n\n" +
                                                                                              "  public void setEntityContext(EntityContext '_Context2);\n\n" +
                                                                                              "  public '_RetType ejbCreate('_CreateType* '_CreateDcl*);\n" +
                                                                                              "  public void ejbActivate();\n\n" +
                                                                                              "  public void ejbLoad();\n\n" +
                                                                                              "  public void ejbPassivate();\n\n" +
                                                                                              "  public void ejbRemove();\n\n" +
                                                                                              "  public void ejbStore();\n" +
                                                                                              "}", J2EE_TYPE),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.session.ejb"),"class 'SessionBean implements SessionBean {\n" +
                                                                                               "  SessionContext '_Context?;\n\n" +
                                                                                               "  public void '_setSessionContext(SessionContext '_Context2);\n\n" +
                                                                                               "  public '_RetType ejbCreate('_CreateParameterType* '_CreateParameterDcl*);\n" +
                                                                                               "  public void ejbActivate();\n\n" +
                                                                                               "  public void ejbPassivate();\n\n" +
                                                                                               "  public void ejbRemove();\n" +
                                                                                               "}", J2EE_TYPE),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.ejb.interface"),"interface 'EjbInterface extends EJBObject {\n" +
                                                                                                 "  'Type+ 'Method+('ParamType* 'ParamName*);\n" +
                                                                                                 "}", J2EE_TYPE),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.servlets"),"public class 'Servlet extends '_ParentClass:*HttpServlet {\n" +
                                                                                            "  public void '_InitServletMethod?:init ();\n" +
                                                                                            "  public void '_DestroyServletMethod?:destroy ();\n" +
                                                                                            "  void '_ServiceMethod?:*service (HttpServletRequest '_request, HttpServletResponse '_response);\n" +
                                                                                            "  void '_SpecificServiceMethod*:do.* (HttpServletRequest '_request2, HttpServletResponse '_response2); \n" +
                                                                                            "}", J2EE_TYPE),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.filters"),"public class 'Filter implements Filter {\n" +
                                                                                           "  public void '_DestroyFilterMethod?:*destroy ();\n" +
                                                                                           "  public void '_InitFilterMethod?:*init ();\n" +
                                                                                           "  public void '_FilteringMethod:*doFilter (ServletRequest '_request,\n" +
                                                                                           "    ServletResponse '_response,FilterChain '_chain);\n" +
                                                                                           "}", J2EE_TYPE),

      // Misc types
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.serializable.classes.and.their.serialization.implementation"),
                               "class '_Class implements '_Serializable:*Serializable {\n" +
                               "  static final long 'VersionField?:serialVersionUID = '_VersionFieldInit?;\n" +
                               "  private static final ObjectStreamField[] '_persistentFields?:serialPersistentFields = '_persistentFieldInitial?; \n" +
                               "  private void 'SerializationWriteHandler?:writeObject (ObjectOutputStream '_stream) throws IOException;\n" +
                               "  private void 'SerializationReadHandler?:readObject (ObjectInputStream '_stream2) throws IOException, ClassNotFoundException;\n" +
                               "  Object 'SpecialSerializationReadHandler?:readResolve () throws ObjectStreamException;\n" +
                               "  Object 'SpecialSerializationWriteHandler?:writeReplace () throws ObjectStreamException;\n" +
                               "}",MISC_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.cloneable.implementations"),
                               "class '_Class implements '_Interface:*Cloneable {\n" +
                               "  Object 'CloningMethod:*clone ();\n" +
                               "}",MISC_TYPE),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.]junit.test.cases"),"public class 'TestCase extends 'TestCaseClazz:*TestCase {\n" +
                                                                                                     "  public void '_testMethod+:test.* ();\n" +
                                                                                                     "}", MISC_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.singletons"),"class 'Class {\n" +
                                                                                        "  private 'Class('_ParameterType* '_ParameterDcl*) {\n" +
                                                                                        "   '_ConstructorStatement*;\n" +
                                                                                        "  }\n"+
                                                                                        "  private static '_Class:* '_Instance;\n" +
                                                                                        "  static '_Class:* '_GetInstance() {\n" +
                                                                                        "    '_SomeStatement*;\n" +
                                                                                        "    return '_Instance;\n" +
                                                                                        "  }\n"+
                                                                                        "}",MISC_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.similar.methods.structure"),"class '_Class {\n" +
                                                                                                       "  '_RetType 'Method+('_ParameterType* '_Parameter) throws 'ExceptionType {\n" +
                                                                                                       "    try {\n" +
                                                                                                       "      '_OtherStatements+;\n" +
                                                                                                       "    } catch('_SomeException '_ExceptionDcl) {\n" +
                                                                                                       "      '_CatchStatement*;\n" +
                                                                                                       "      throw new 'ExceptionType('_ExceptionConstructorArgs*);\n" +
                                                                                                       "    }\n" +
                                                                                                       "  }\n" +
                                                                                                       "}",MISC_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.bean.info.classes"),"class 'A implements '_:*java\\.beans\\.BeanInfo {\n" +
                                                                                               "}",MISC_TYPE),

      // interesting types
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.symbol"),"'Symbol",INTERESTING_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.fields.variables.read"),"'Symbol:[read]",INTERESTING_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.fields_variables.with.given.name.pattern.updated"),"'Symbol:[regex( name ) && write]",INTERESTING_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.usage.of.derived.type.in.cast"),"('CastType:*Base ) 'Expr",INTERESTING_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.boxing.in.declarations"),"'_Type:Integer|Boolean|Long|Character|Short|Byte 'Var = '_Value:[formal( int|boolean|long|char|short|byte )]",INTERESTING_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.unboxing.in.declarations"),"'_Type:int|boolean|long|char|short|byte 'Var = '_Value:[formal( Integer|Boolean|Long|Character|Short|Byte )]",INTERESTING_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.boxing.in.method.calls"),"'_Instance?.'Call('_BeforeParam*,'_Param:[ exprtype( int|boolean|long|char|short|byte ) && formal( Integer|Boolean|Long|Character|Short|Byte )],'_AfterParam*)",INTERESTING_TYPE),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.unboxing.in.method.calls"), "'_Instance?.'Call('_BeforeParam*,'_Param:[ formal( int|boolean|long|char|short|byte ) && exprtype( Integer|Boolean|Long|Character|Short|Byte )],'_AfterParam*)\"",INTERESTING_TYPE),
      //createSearchTemplateInfo("methods called","'_?.'_:[ref('Method)] ('_*)", INTERESTING_TYPE),
      //createSearchTemplateInfo("fields selected","'_?.'_:[ref('Field)] ", INTERESTING_TYPE),
      //createSearchTemplateInfo("symbols used","'_:[ref('Symbol)] ", INTERESTING_TYPE),
      //createSearchTemplateInfo("types used","'_:[ref('Type)] '_;", INTERESTING_TYPE),

      createSearchTemplateInfo("xml tag", "<'a/>",HTML_XML, StdFileTypes.XML),
      createSearchTemplateInfo("xml attribute", "<'_tag 'attribute='_value/>",HTML_XML, StdFileTypes.XML),
      createSearchTemplateInfo("xml attribute value", "<'_tag '_attribute='value/>",HTML_XML, StdFileTypes.XML),
      createSearchTemplateInfo("html tables", "<table>'_content*</table>",HTML_XML, StdFileTypes.HTML),
    };
  }

  public String toString() {
    return configuration.getName();
  }

  public MatchOptions getMatchOptions() {
    return configuration.getMatchOptions();
  }

  public String getName() {
    return configuration.getName();
  }
}
