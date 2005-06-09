package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * Template info
 */
public class PredefinedConfiguration extends Configuration {
  private static final String EXPRESSION_TYPE = "expressions";
  private static final String INTERESTING_TYPE = "interesting";
  private static final String J2EE_TYPE = "j2ee";
  private static final String OPERATOR_TYPE = "operators";
  private static final String CLASS_TYPE = "class-based";
  private static final String METADATA_TYPE = "comments, javadoc and metadata";
  private static final String MISC_TYPE = "misc";
  private static final String GENERICS_TYPE = "generics";
  private static final String HTML_XML = "xml_html";

  private static PredefinedConfiguration[] infos;

  private Configuration configuration;
  private String category;
  static final Object USER_DEFINED_TYPE = "user defined";

  private PredefinedConfiguration(Configuration _configuration, String _category) {
    configuration = _configuration;
    category = _category;
  }

  private static PredefinedConfiguration createSearchTemplateInfo(String name, String criteria, String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }
  private static PredefinedConfiguration createSearchTemplateInfo(String name, String criteria, String category, FileType fileType) {
    final SearchConfiguration config = new SearchConfiguration();
    config.setPredefined(true);
    config.setName(name);
    config.getMatchOptions().setSearchPattern(criteria);
    config.getMatchOptions().setFileType(fileType);
    MatcherImplUtil.transform( config.getMatchOptions() );

    return new PredefinedConfiguration(config,category);
  }

  private static PredefinedConfiguration createSearchTemplateInfoSimple(String name, String criteria, String category) {
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
      infos = new PredefinedConfiguration[] {
        // Expression patterns
        createSearchTemplateInfo("method calls","'_Instance?.'MethodCall('_Parameter*)",EXPRESSION_TYPE),
        createSearchTemplateInfo("field selections","'_Instance?.'Field",EXPRESSION_TYPE),
        createSearchTemplateInfo("array access","'_Field['_Index]",EXPRESSION_TYPE),
        createSearchTemplateInfo("assignments","'_Inst = '_Expr",EXPRESSION_TYPE),
        createSearchTemplateInfo("casts","('_Type)'_Expr",EXPRESSION_TYPE),
        createSearchTemplateInfo("instanceof","'_Expr instanceof '_Type",EXPRESSION_TYPE),
        createSearchTemplateInfo("string literals","\"'_String\"",EXPRESSION_TYPE),
        createSearchTemplateInfo("all expressions of some type","'_Expression:[exprtype( SomeType )]",EXPRESSION_TYPE),

        // Operators
        createSearchTemplateInfo("block dcls","{\n  '_Type+ 'Var+ = '_Init*;\n  '_BlockStatements*;\n}",OPERATOR_TYPE),
        createSearchTemplateInfo("try's","try {\n  '_TryStatement+;\n} catch('_ExceptionType '_ExceptionDcl) {\n  '_CatchStatement*;\n}",OPERATOR_TYPE),
        createSearchTemplateInfo("if's","if ('_Condition) {\n  '_ThenStatement*;\n} else {\n  '_ElseStatement*;\n}",OPERATOR_TYPE),
        createSearchTemplateInfo("switches","switch('_Condition) {\n  '_Statement*;\n}",OPERATOR_TYPE),

        // Class based
        createSearchTemplateInfo("methods of the class","class '_Class { \n  '_ReturnType+ 'MethodName+('_ParameterType* '_Parameter*);\n}",CLASS_TYPE),
        createSearchTemplateInfo("fields of the class","class '_Class { \n  '_FieldType+ 'FieldName+ = '_Init*;\n}",CLASS_TYPE),
        createSearchTemplateInfo("all methods of the class (within hierarchy)","class '_ { \n  '_ReturnType+ 'MethodName+:* ('_ParameterType* '_Parameter*);\n}",CLASS_TYPE),
        createSearchTemplateInfo("all fields of the class","class '_Class { \n  '_FieldType+ 'FieldName+:* = '_Init*;\n}",CLASS_TYPE),
        createSearchTemplateInfo("constructors of the class","class 'Class {\n  'Class+('_ParameterType* '_ParameterName*) {\n    '_Statement*;\n  }\n}",CLASS_TYPE),
        createSearchTemplateInfo("classes","class 'Class {}", CLASS_TYPE),
        createSearchTemplateInfo("direct subclasses","class 'Class extends '_Parent: {}", CLASS_TYPE),
        createSearchTemplateInfo("implementors of interface (within hierarchy)","class 'Class implements 'Interface:* {}", CLASS_TYPE),
        createSearchTemplateInfo("interfaces","interface 'Interface {}", CLASS_TYPE),
        createSearchTemplateInfo("inner classes","class '_ {\n  class 'InnerClass+ {}\n}",CLASS_TYPE),
        createSearchTemplateInfo("all inner classes (within hierarchy)","class '_Class {\n  class 'InnerClass+:* {}\n}",CLASS_TYPE),
        createSearchTemplateInfo("anonymous classes","new 'AnonymousClass() {}", CLASS_TYPE),

        // Generics
        createSearchTemplateInfo("generic classes","class 'GenericClass<'_TypeParameter+> {} ", GENERICS_TYPE),
        createSearchTemplateInfo("generic methods","class '_Class {\n  <'_TypeParameter+> '_Type+ 'Method+('_ParameterType* '_ParameterDcl*);\n}", GENERICS_TYPE),
        createSearchTemplateInfo("typed symbol","'Symbol <'_GenericArgument+>", GENERICS_TYPE),
        createSearchTemplateInfo("generic casts","( '_Type <'_GenericArgument+> ) '_Expr", GENERICS_TYPE),
        createSearchTemplateInfo("type var substitutions in intanceof with generic types","'_Expr instanceof '_Type <'Substitutions+> ", GENERICS_TYPE),
        createSearchTemplateInfo("variables of generic types","'_Type <'_GenericArgument+>  'Var = 'Init?;", GENERICS_TYPE),

        // Add comments and metadata
        createSearchTemplateInfo("comments","/* 'CommentContent */", METADATA_TYPE),
        createSearchTemplateInfo("javadoc annotated class","/** @'_Tag+ '_TagValue* */\nclass '_Class {\n}", METADATA_TYPE),
        createSearchTemplateInfo("javadoc annotated methods","class '_Class {\n  /** @'_Tag+ '_TagValue* */\n  '_Type+ 'Method+('_ParameterType* '_ParameterDcl*);\n}", METADATA_TYPE),
        createSearchTemplateInfo("javadoc annotated fields","class '_Class {\n  /** @'_Tag+ '_TagValue* */\n  '_Type+ 'Field+ = '_Init*;\n}", METADATA_TYPE),
        createSearchTemplateInfo("javadoc tags","/** @'Tag+ '_TagValue* */", METADATA_TYPE),
        createSearchTemplateInfo("XDoclet metadata","/** @'Tag \n  '_Property+\n*/", METADATA_TYPE),

        createSearchTemplateInfo("annotated class",
                                 "@'_Annotation( )\n" +
                                 "class 'Class {}", METADATA_TYPE),
        createSearchTemplateInfo("annotated fields",
                                 "class '_Class {\n" +
                                 "  @'_Annotation+( )\n" +
                                 "  '_FieldType+ 'FieldName+ = '_FieldInitial*;\n" +
                                 "}", METADATA_TYPE),
        createSearchTemplateInfo("annotated methods",
                                 "class '_Class {\n" +
                                 "  @'_Annotation+( )\n" +
                                 "  '_MethodType+ 'MethodName+('_ParameterType* '_ParameterName*);\n" +
                                 "}", METADATA_TYPE),

        // TODO: used annotation pattern

        // J2EE templates
        createSearchTemplateInfoSimple("Struts 1.1 actions","public class 'StrutsActionClass extends '_ParentClass*:Action {\n" +
        "  public ActionForward 'AnActionMethod:*execute (ActionMapping '_action,\n" +
        "                                 ActionForm '_form,\n" +
        "                                 HttpServletRequest '_request,\n" +
        "                                 HttpServletResponse '_response);\n" +
        "}",J2EE_TYPE),
        createSearchTemplateInfoSimple("entity ejb","class 'EntityBean implements EntityBean {\n" +
        "  EntityContext '_Context?;\n\n" +
        "  public void setEntityContext(EntityContext '_Context2);\n\n" +
        "  public '_RetType ejbCreate('_CreateType* '_CreateDcl*);\n" +
        "  public void ejbActivate();\n\n" +
        "  public void ejbLoad();\n\n" +
        "  public void ejbPassivate();\n\n" +
        "  public void ejbRemove();\n\n" +
        "  public void ejbStore();\n" +
        "}", J2EE_TYPE),
        createSearchTemplateInfoSimple("session ejb","class 'SessionBean implements SessionBean {\n" +
        "  SessionContext '_Context?;\n\n" +
        "  public void '_setSessionContext(SessionContext '_Context2);\n\n" +
        "  public '_RetType ejbCreate('_CreateParameterType* '_CreateParameterDcl*);\n" +
        "  public void ejbActivate();\n\n" +
        "  public void ejbPassivate();\n\n" +
        "  public void ejbRemove();\n" +
        "}", J2EE_TYPE),
        createSearchTemplateInfoSimple("ejb interface","interface 'EjbInterface extends EJBObject {\n" +
        "  'Type+ 'Method+('ParamType* 'ParamName*);\n" +
        "}", J2EE_TYPE),
        createSearchTemplateInfoSimple("servlets","public class 'Servlet extends '_ParentClass:*HttpServlet {\n" +
        "  public void '_InitServletMethod?:init ();\n" +
        "  public void '_DestroyServletMethod?:destroy ();\n" +
        "  void '_ServiceMethod?:*service (HttpServletRequest '_request, HttpServletResponse '_response);\n" +
        "  void '_SpecificServiceMethod*:do.* (HttpServletRequest '_request2, HttpServletResponse '_response2); \n" +
        "}", J2EE_TYPE),
        createSearchTemplateInfoSimple("filters","public class 'Filter implements Filter {\n" +
        "  public void '_DestroyFilterMethod?:*destroy ();\n" +
        "  public void '_InitFilterMethod?:*init ();\n" +
        "  public void '_FilteringMethod:*doFilter (ServletRequest '_request,\n" +
        "    ServletResponse '_response,FilterChain '_chain);\n" +
        "}", J2EE_TYPE),

        // Misc types
        createSearchTemplateInfo("Serializable classes and their serialization implementation",
        "class '_Class implements '_Serializable:*Serializable {\n" +
        "  static final long 'VersionField?:serialVersionUID = '_VersionFieldInit?;\n" +
        "  private static final ObjectStreamField[] '_persistentFields?:serialPersistentFields = '_persistentFieldInitial?; \n" +
        "  private void 'SerializationWriteHandler?:writeObject (ObjectOutputStream '_stream) throws IOException;\n" +
        "  private void 'SerializationReadHandler?:readObject (ObjectInputStream '_stream2) throws IOException, ClassNotFoundException;\n" +
        "  Object 'SpecialSerializationReadHandler?:readResolve () throws ObjectStreamException;\n" +
        "  Object 'SpecialSerializationWriteHandler?:writeReplace () throws ObjectStreamException;\n" +
        "}",MISC_TYPE),
        createSearchTemplateInfo("Cloneable implementations",
        "class '_Class implements '_Interface:*Cloneable {\n" +
        "  Object 'CloningMethod:*clone ();\n" +
        "}",MISC_TYPE),
        createSearchTemplateInfoSimple("junit test cases","public class 'TestCase extends 'TestCaseClazz:*TestCase {\n" +
        "  public void '_testMethod+:test.* ();\n" +
        "}", MISC_TYPE),
        createSearchTemplateInfo("singletons","class 'Class {\n" +
        "  private 'Class('_ParameterType* '_ParameterDcl*) {\n" +
        "   '_ConstructorStatement*;\n" +
        "  }\n"+
        "  private static '_Class:* '_Instance;\n" +
        "  static '_Class:* '_GetInstance() {\n" +
        "    '_SomeStatement*;\n" +
        "    return '_Instance;\n" +
        "  }\n"+
        "}",MISC_TYPE),
        createSearchTemplateInfo("similar methods structure","class '_Class {\n" +
        "  '_RetType 'Method+('_ParameterType* '_Parameter) throws 'ExceptionType {\n" +
        "    try {\n" +
        "      '_OtherStatements+;\n" +
        "    } catch('_SomeException '_ExceptionDcl) {\n" +
        "      '_CatchStatement*;\n" +
        "      throw new 'ExceptionType('_ExceptionConstructorArgs*);\n" +
        "    }\n" +
        "  }\n" +
        "}",MISC_TYPE),
        createSearchTemplateInfo("Bean info classes","class 'A implements '_:*java\\.beans\\.BeanInfo {\n" +
        "}",MISC_TYPE),

        // interesting types
        createSearchTemplateInfo("symbol","'Symbol",INTERESTING_TYPE),
        createSearchTemplateInfo("fields/variables read","'Symbol:[read]",INTERESTING_TYPE),
        createSearchTemplateInfo("fields/variables with given name pattern updated","'Symbol:[regex( name ) && write]",INTERESTING_TYPE),
        createSearchTemplateInfo("usage of derived type in cast","('CastType:*Base ) 'Expr",INTERESTING_TYPE),
        createSearchTemplateInfo("boxing in declarations","'_Type:Integer|Boolean|Long|Character|Short|Byte 'Var = '_Value:[formal( int|boolean|long|char|short|byte )]",INTERESTING_TYPE),
        createSearchTemplateInfo("unboxing in declarations","'_Type:int|boolean|long|char|short|byte 'Var = '_Value:[formal( Integer|Boolean|Long|Character|Short|Byte )]",INTERESTING_TYPE),
        createSearchTemplateInfo("boxing in method calls","'_Instance?.'Call('_BeforeParam*,'_Param:[ exprtype( int|boolean|long|char|short|byte ) && formal( Integer|Boolean|Long|Character|Short|Byte )],'_AfterParam*)",INTERESTING_TYPE),
        createSearchTemplateInfo("unboxing in method calls", "'_Instance?.'Call('_BeforeParam*,'_Param:[ formal( int|boolean|long|char|short|byte ) && exprtype( Integer|Boolean|Long|Character|Short|Byte )],'_AfterParam*)\"",INTERESTING_TYPE),
        //createSearchTemplateInfo("methods called","'_?.'_:[ref('Method)] ('_*)", INTERESTING_TYPE),
        //createSearchTemplateInfo("fields selected","'_?.'_:[ref('Field)] ", INTERESTING_TYPE),
        //createSearchTemplateInfo("symbols used","'_:[ref('Symbol)] ", INTERESTING_TYPE),
        //createSearchTemplateInfo("types used","'_:[ref('Type)] '_;", INTERESTING_TYPE),

        //createSearchTemplateInfo("xml tag", "<'a/>",HTML_XML, StdFileTypes.XML),
        //createSearchTemplateInfo("xml attribute", "<'_tag 'attribute='_value/>",HTML_XML, StdFileTypes.XML),
        //createSearchTemplateInfo("xml attribute value", "<'_tag '_attribute='value/>",HTML_XML, StdFileTypes.XML),
      };
    }

    return infos;
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
