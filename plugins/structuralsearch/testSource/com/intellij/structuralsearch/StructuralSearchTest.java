package com.intellij.structuralsearch;

import com.intellij.idea.IdeaTestUtil;

import java.util.Calendar;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 3, 2004
 * Time: 5:45:17 PM
 * To change this template use File | Settings | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class StructuralSearchTest extends StructuralSearchTestCase {
  private static final String s1 =
    "debug(\"In action performed:\"+event);"+
    "project = (Project)event.getDataContext().getData(DataConstants.PROJECT);" +
    "CodeEditorManager.getInstance(project).commitAllToPsiFile();" +
    "file = (PsiFile) event.getDataContext().getData(\"psi.File\"); " +
    "((dialog==null)?" +
    "  (dialog = new SearchDialog()):" +
    "  dialog" +
    ").show();";

  private static final String s2 = "((dialog==null)? (dialog = new SearchDialog()): dialog).show();";
  private static final String s3 = "dialog = new SearchDialog()";

  private static final String s4 =
                " do { " +
                "  pattern = pattern.getNextSibling(); " +
                " } " +
                " while (pattern!=null && filterLexicalNodes(pattern));";

  private static final String s5 =
                "{ System.out.println();" +
                "  while(false) { " +
                "    do { " +
                "       pattern = pattern.getNextSibling(); " +
                "    } " +
                "      while (pattern!=null && filterLexicalNodes(pattern)); " +
                "  } " +
                " do { " +
                "  pattern = pattern.getNextSibling(); " +
                " } while (pattern!=null && filterLexicalNodes(pattern));" +
                " { { " +
                "   do { " +
                "     pattern = pattern.getNextSibling(); " +
                "   } while (pattern!=null && filterLexicalNodes(pattern));" +
                " } }" +
                "}";

  private static final String s6 =
                " do { " +
                "  pattern.getNextSibling(); " +
                " } " +
                " while (pattern!=null && filterLexicalNodes(pattern));";

  private static final String s7 =
                " if (true) throw new UnsupportedPatternException(statement.toString());" +
                " if (true) { " +
                "   throw new UnsupportedPatternException(statement.toString());" +
                " } ";

  private static final String s8 =
                " if (true) { " +
                "   throw new UnsupportedPatternException(statement.toString());" +
                " } ";

  private static final String s9 = " if (true) throw new UnsupportedPatternException(statement.toString());";

  private static final String s10 = "listener.add(new Runnable() { public void run() {} });";
  private static final String s11 = " new XXX()";

  private static final String s12 =
                 "new Runnable() {" +
                 "  public void run() {" +
                 "   matchContext.getSink().matchingFinished();" +
                 "   } " +
                 " }";

  private static final String s13 = "new Runnable() {}";
  private static final String s14_1 = "if (true) { aaa(var); }";
  private static final String s14_2 = "if (true) { aaa(var); bbb(var2); }\n if(1==1) { system.out.println('o'); }";
  private static final String s15 = "'T;";
  private static final String s16 = "if('_T) { '_T2; }";
  private static final String s17 =
    "token.getText().equals(token2.getText());" +
    "token.getText().equals(token2.getText2());" +
    "token.a.equals(token2.b);" +
    "token.a.equals(token2.a);";
  private static final String s18_1 = "'_T1.'_T2.equals('_T3.'_T2);";
  private static final String s18_2 = "'_T1.'_T2().equals('_T3.'_T2());";
  private static final String s18_3 = "'_T1.'_T2";
  private static final String s19 = "Aaa a = (Aaa)b; Aaa c = (Bbb)d;";
  private static final String s20 = "'_T1 'T2 = ('_T1)'_T3;";
  private static final String s20_2 = "'_T1 '_T2 = ('_T1)'_T3;";
  private static final String s21_1 = "'_T1:Aa* 'T2 = ('_T1)'_T3;";
  private static final String s21_2 = "'_T1:A* 'T2 = ( '_T1:A+ )'_T3;";
  private static final String s21_3 = "'_T1:Aa* 'T2 = ( '_T1:Aa* )'_T3;";

  private static final String s22 = "Aaa a = (Aaa)b; Bbb c = (Bbb)d;";

  private static final String s23 = "a[i] = 1; b[a[i]] = f(); if (a[i]==1) return b[c[i]];";
  private static final String s24_1 = "'T['_T2:.*i.* ] = '_T3;";
  private static final String s24_2  = "'T['_T2:.*i.* ]";
  private static final String s25  = "class MatcherImpl {  void doMatch(int a) {} }\n" +
                                     "class Matcher { abstract void doMatch(int a);}\n " +
                                     "class Matcher2Impl { void doMatch(int a, int b) {} } ";
  private static final String s26  = "class 'T:.*Impl { '_T2 '_T3('_T4 '_T5) {\n\n} } ";
  private static final String s27 = "class A {} interface B {}";
  private static final String s28 = "interface 'T {}";

  private static final String s29 = "class A { void B(int C) {} } class D { void E(double e) {} }";
  private static final String s30 = "class '_ { void '_('_:int '_); } ";

  private static final String s31 = "class A extends B { } class D extends B { } class C extends C {}";
  private static final String s32 = "class '_ extends B {  } ";

  private static final String s33 = "class A implements B,C { } class D implements B,D { } class C2 implements C,B {}";
  private static final String s34 = "class '_ implements B,C {  } ";

  private static final String s35 = "class A { int b; double c; void d() {} int e() {} } " +
                                    "class A2 { int b; void d() {} }";
  private static final String s36 = "class '_ { double '_; int '_; int '_() {} void '_() {} } ";

  private static final String s37 = "class A { void d() throws B,C,D {} } class A2 { void d() throws B,C {} }";
  private static final String s38 = "class 'T { '_ '_() throws D,C {} } ";

  private static final String s39 = "class A extends B { } class A2 {  }";
  private static final String s40 = "class 'T { } ";

  private static final String s41 = "class A extends B { int a = 1; } class B { int[] c= new int[2]; } " +
                                    "class D { double e; } class E { int d; } ";
  private static final String s42_1 = "class '_ { '_T '_T2 = '_T3; } ";
  private static final String s42_2 = "class '_ { '_T '_T2; } ";

  private static final String s43 = "interface A extends B { int B = 1; } " +
                                    "interface D { public final static double e = 1; } " +
                                    "interface E { final static ind d = 2; } " +
                                    "interface F {  } ";
  private static final String s44 = "interface '_ { '_T 'T2 = '_T3; } ";
  private static final String s45 = "class A extends B { private static final int B = 1; } " +
                                    "class C extends D { int B = 1; }" +
                                    "class E { }";

  private static final String s46 = "class '_ { final static private '_T 'T2 = '_T3; } ";
  private static final String s46_2 = "class '_ { '_T 'T2 = '_T3; } ";

  private static final String s47 = "class C { java.lang.String t; } class B { BufferedString t2;} class A { String p;} ";
  private static final String s48 = "class '_ { String '_; }";

  private static final String s49 = "class C { void a() throws java.lang.RuntimeException {} } class B { BufferedString t2;}";
  private static final String s50 = "class '_ { '_ '_() thows RuntimeException; }";

  private static final String s51 = "class C extends B { } class B extends A { } class E {}";
  private static final String s52 = "class '_ extends '_ {  }";

  private static final String s53 = "class C { " +
                                    "   String a = System.getProperty(\"abcd\"); " +
                                    "  static { String s = System.getProperty(a); }" +
                                    "  static void b() { String s = System.getProperty(a); }" +
                                    " }";
  private static final String s54 = "System.getProperty('T)";

  private static final String s55 = " a = b.class; ";
  private static final String s56 = "'T.class";

  private static final String s57 = "{ /** @author Maxim */ class C { " +
                                    "} " +
                                    "class D {" +
                                    "/** @serializable */ private int value; " +
                                    "/** @since 1.4 */ void a() {} "+
                                    "}" +
                                    "class F { " +
                                    "/** @since 1.4 */ void a() {} "+
                                    "/** @serializable */ private int value2; " +
                                    "}" +
                                    "class G { /** @param a*/ void a() {} } }";
  private static final String s57_2 = "/** @author Maxim */ class C { " +
                                      "} " +
                                      "class D {" +
                                      "/** @serializable */ private int value; " +
                                      "/** @since 1.4 */ void a() {} "+
                                      "}" +
                                      "class F { " +
                                      "/** @since 1.4 */ void a() {} "+
                                      "/** @serializable */ private int value2; " +
                                      "}" +
                                      "class G { /** @param a*/ void a() {} }";
  private static final String s58 = "/** @'T '_T2 */ class '_ { }";
  private static final String s58_2 = "class '_ { /** @serializable '_ */ '_ '_; }";
  private static final String s58_3 = "class '_ { /** @'T 1.4 */ '_ '_() {} }";
  private static final String s58_4 = "/** @'T '_T2 */";
  private static final String s58_5 = "/** @'T '_T2? */";

  private static final String s59 = "interface A { void B(); }";
  private static final String s60 = "interface '_ { void '_(); }";

  private static final String s61 = "{ a=b; c=d; return; } { e=f; } {}";
  private static final String s62_1 = "{ 'T*; }";
  private static final String s62_2 = "{ 'T+; }";
  private static final String s62_3 = "{ 'T?; }";

  private static final String s62_4 = "{ '_*; }";
  private static final String s62_5 = "{ '_+; }";
  private static final String s62_6 = "{ '_?; }";

  private static final String s63 = " class A { A() {} } class B { public void run() {} }";
  private static final String s63_2 = " class A { A() {} " +
                                      "class B { public void run() {} } " +
                                      "class D { public void run() {} } " +
                                      "} " +
                                      "class C {}";
  private static final String s64 = " class 'T { public void '_T2:run () {} }";
  private static final String s64_2 = "class '_ { class 'T { public void '_T2:run () {} } }";

  private static final String s65 = " if (A instanceof B) {} else if (B instanceof C) {}";
  private static final String s66 = " '_T instanceof '_T2:B";

  private static final String s67 = " buf.append((VirtualFile)a);";
  private static final String s68 = " (VirtualFile)'T";

  private static final String s69 = " System.getProperties(); System.out.println(); ";
  private static final String s70 = " System.out ";

  private static final String s71 = " class A { " +
                                    "class D { D() { c(); } }" +
                                    "void a() { c(); new MouseListenener() { void b() { c(); } } }" +
                                    " }";
  private static final String s72 = " c(); ";

  private static final String s73 = " class A { int A; static int B=5; public abstract void a(int c); void q() { ind d=7; } }";
  private static final String s74 = " '_Type 'Var = '_Init?; ";
  private static final String s75 = "{ /** @class aClass\n @author the author */ class A {}\n" +
                                    " /** */ class B {}\n" +
                                    " /** @class aClass */ class C {} }";
  private static final String s76 = " /** @'_tag+ '_value+ */";
  private static final String s76_2 = " /** @'_tag* '_value* */";
  private static final String s76_3 = " /** @'_tag? '_value? */ class 't {}";

  private static final String s77 = " new ActionListener() {} ";
  private static final String s78 = " class 'T:.*aaa {} ";

  private static final String s79 = " class A { static { int c; } void a() { int b; b=1; }} ";
  private static final String s80 = " { '_T 'T3 = '_T2?; '_*; } ";

  private static final String s81 = "class Pair<First,Second> {" +
                                    "  <C,F> void a(B<C> b, D<F> e) throws C {" +
                                    "    P<Q> r = (S<T>)null;"+
                                    "    Q q = null; "+
                                    "    if (r instanceof S<T>) {}"+
                                    "  } " +
                                    "} class Q { void b() {} } ";

  private static final String s81_2 = "class Double<T> {} class T {} class Single<First extends A & B> {}";

  private static final String s82 = "class '_<'T+> {}";
  private static final String s82_2 = "'_Expr instanceof '_Type<'_Parameter+>";
  private static final String s82_3 = "( '_Type<'_Parameter+> ) '_Expr";
  private static final String s82_4 = "'_Type<'_Parameter+> 'a = '_Init?;";
  private static final String s82_5 = "class '_ { <'_+> '_Type 'Method('_* '_*); }";
  private static final String s82_6 = "class '_<'_+ extends 'res+> {}";
  private static final String s82_7 = "'Type";

  private static final String s83 = "/**\n" +
                                    " * @hibernate.class\n" +
                                    " *  table=\"CATS\"\n" +
                                    " */\n" +
                                    "public class Cat {\n" +
                                    "    private Long id; // identifier\n" +
                                    "    private Date birthdate;\n" +
                                    "    /**\n" +
                                    "     * @hibernate.id\n" +
                                    "     *  generator-class=\"native\"\n" +
                                    "     *  column=\"CAT_ID\"\n" +
                                    "     */\n" +
                                    "    public Long getId() {\n" +
                                    "        return id;\n" +
                                    "    }\n" +
                                    "    private void setId(Long id) {\n" +
                                    "        this.id=id;\n" +
                                    "    }\n" +
                                    "\n" +
                                    "    /**\n" +
                                    "     * @hibernate.property\n" +
                                    "     *  column=\"BIRTH_DATE\"\n" +
                                    "     */\n" +
                                    "    public Date getBirthdate() {\n" +
                                    "        return birthdate;\n" +
                                    "    }\n" +
                                    "    void setBirthdate(Date date) {\n" +
                                    "        birthdate = date;\n" +
                                    "    }\n" +
                                    "    /**\n" +
                                    "     * @hibernate.property\n" +
                                    "     *  column=\"SEX\"\n" +
                                    "     *  not-null=\"true\"\n" +
                                    "     *  update=\"false\"\n" +
                                    "     */\n" +
                                    "    public char getSex() {\n" +
                                    "        return sex;\n" +
                                    "    }\n" +
                                    "    void setSex(char sex) {\n" +
                                    "        this.sex=sex;\n" +
                                    "    }\n" +
                                    "}";

    private static final String s84 = "    /**\n" +
                                      "     * @hibernate.property\n" +
                                      "     *  'Property+\n" +
                                      "     */\n";

  private static final String s84_2 = "    /**\n" +
                                      "     * @hibernate.property\n" +
                                      "     *  update=\"fa.se\"\n" +
                                      "     */\n";

  private static final String s85 = "{ int a; a=1; a=1; return a; }";
  private static final String s86 = "'T; 'T;";

  private static final String s87 = " getSomething(\"1\"); a.call(); ";
  private static final String s88 = " '_Instance.'Call('_*); ";
  private static final String s88_2 = " 'Call('_*); ";
  private static final String s88_3 = " '_Instance?.'Call('_*); ";
  private static final String s89 = "{ a = 1; b = 2; c=3; }";
  private static final String s90 = "{ '_T*; '_T2*; }";
  private static final String s90_2 = " { '_T*; '_T2*; '_T3+; } ";
  private static final String s90_3 = " { '_T+; '_T2+; '_T3+; '_T4+; } ";
  private static final String s90_4 = " { '_T{1,3}; '_T2{2}; } ";
  private static final String s90_5 = " { '_T{1}?; '_T2*?; '_T3+?; } ";
  private static final String s90_6 = " { '_T{1}?; '_T2{1,2}?; '_T3+?; '_T4+?; } ";

  private static final String s91 = "class a {\n" +
                                    "  void b() {\n" +
                                    "    int c;\n" +
                                    "\n" +
                                    "    c = 1;\n" +
                                    "    b();\n" +
                                    "    a a1;\n" +
                                    "  }\n" +
                                    "}";
  private static final String s92 = "'T:a";
  private static final String s92_2 = "'T:b";
  private static final String s92_3 = "'T:c";

  private static final String s93 = " class A {" +
                                    "private int field;" +
                                    "public void b() {}" +
                                    "}";
  private static final String s94 = " class '_ {" +
                                    "private void b() {}" +
                                    "}";
   private static final String s94_2 = " class '_ {" +
                                       "public void b() {}" +
                                       "}";
  private static final String s94_3 = " class '_ {" +
                                      "protected int field;" +
                                      "}";
  private static final String s94_4 = " class '_ {" +
                                      "private int field;" +
                                      "}";

  private static final String s95 = " class Clazz {" +
                                    "private int field;" +
                                    "private int field2;" +
                                    "private int fiel-d2;" +
                                    "}";

  private static final String  s96 = " class '_ {" +
                                     "private int 'T+:field.* ;" +
                                     "}";

  public void testSearchExpressions() {
    assertFalse("subexpr match",findMatchesCount(s2,s3)==0);
    assertEquals("search for new ",findMatchesCount(s10,s11),0);
    assertEquals("search for anonymous classes",findMatchesCount(s12,s13),1);
    // expr in definition intiialiZer
    assertEquals(
      "expr in def initializer",
      findMatchesCount(s53,s54),
      3
    );

    // a.class expression search
    assertEquals(
      "a.class pattern",
      findMatchesCount(s55,s56),
      1
    );

    String complexCode = "interface I { void b(); } interface I2 extends I {} class I3 extends I {} " +
                         "class A implements I2 {  void b() {} } class B implements I3 { void b() {}} " +
                         "I2 a; I3 b; a.b(); b.b(); b.b(); A c; B d; c.b(); d.b(); d.b(); ";

    String exprTypePattern1 = "'t:[exprtype( I2 )].b();";
    String exprTypePattern2 = "'t:[!exprtype( I2 )].b();";

    String exprTypePattern3 = "'t:[exprtype( *I2 )].b();";
    String exprTypePattern4 = "'t:[!exprtype( *I2 )].b();";

    assertEquals(
      "expr type condition",
      findMatchesCount(complexCode,exprTypePattern1),
      1
    );

    assertEquals(
      "expr type condition 2",
      findMatchesCount(complexCode,exprTypePattern2),
      5
    );

    assertEquals(
      "expr type condition 3",
      findMatchesCount(complexCode,exprTypePattern3),
      2
    );

    assertEquals(
      "expr type condition 4",
      findMatchesCount(complexCode,exprTypePattern4),
      4
    );

    assertEquals(
      "no smart detection of search target",
      findMatchesCount("processInheritors(1,2,3,4); processInheritors(1,2,3); processInheritors(1,2,3,4,5,6);","'instance?.processInheritors('_param1{1,6});"),
      3
    );

    String arrays = "int[] a = new int[20];\n" +
                    "byte[] b = new byte[30]";
    String arrayPattern = "new int[$a$]";
    assertEquals(
      "Improper array search",
      1,
      findMatchesCount(arrays,arrayPattern)
    );

    String someCode = "a *= 2; a+=2;";
    String otherCode = "a *= 2;";

    assertEquals(
      "Improper *= 2 search",
      1,
      findMatchesCount(someCode,otherCode)
    );

    String s1 = "Thread t = new Thread(\"my thread\",\"my another thread\") {\n" +
                "    public void run() {\n" +
                "        // do stuff\n" +
                "    }\n" +
                "}";
    String s2 = "new Thread('args*) { '_Other* }";

    assertEquals(
      "Find inner class parameters",
      2,
      findMatchesCount(s1,s2)
    );

    String s3 = "Thread t = new Thread(\"my thread\") {\n" +
                "    public void run() {\n" +
                "        // do stuff\n" +
                "    }\n" +
                "};";
    String s4 = "new Thread($args$)";

    assertEquals(
      "Find inner class by new",
      1,
      findMatchesCount(s3,s4)
    );

    String s5 = "class A {\n" +
                "public static <T> T[] copy(T[] array, Class<T> aClass) {\n" +
                "    int i = (int)0;\n" +
                "    int b = (int)0;\n" +
                "    return (T[])array.clone();\n" +
                "  }\n" +
                "}";
    String s6 = "($T$[])$expr$";

    assertEquals(
      "Find cast to array",
      1,
      findMatchesCount(s5,s6)
    );

    String s7 = "import java.math.BigDecimal;\n" +
                "\n" +
                "public class Prorator {\n" +
                "        public void prorate(BigDecimal[] array) {\n" +
                "                // do nothing\n" +
                "        }\n" +
                "        public void prorate2(java.math.BigDecimal[] array) {\n" +
                "                // do nothing\n" +
                "        }\n" +
                "        public void prorate(BigDecimal bd) {\n" +
                "                // do nothing\n" +
                "        }\n" +
                "\n" +
                "        public static void main(String[] args) {\n" +
                "                BigDecimal[] something = new BigDecimal[2];\n" +
                "                java.math.BigDecimal[] something2 = new BigDecimal[2];\n" +
                "                something[0] = new BigDecimal(1.0);\n" +
                "                something[1] = new BigDecimal(1.0);\n" +
                "\n" +
                "                Prorator prorator = new Prorator();\n" +
                "\n" +
                "// ---------------------------------------------------\n" +
                "// the line below should've been found, in my opinion.\n" +
                "// --------------------------------------------------\n" +
                "                prorator.prorate(something);\n" +
                "                prorator.prorate(something2);\n" +

                "                prorator.prorate(something[0]);\n" +
                "                prorator.prorate(something[1]);\n" +
                "                prorator.prorate(something[0]);\n" +
                "        }\n" +
                "}";
    String s8 = "'_Instance.'_MethodCall:[regex( prorate )]('_Param:[exprtype( BigDecimal\\[\\] )]) ";

    assertEquals(
      "Find method call with array for parameter expr type",
      2,
      findMatchesCount(s7,s8,true)
    );

    String s13 = "try { } catch(Exception e) { e.printStackTrace(); }";
    String s14 = "'_Instance.'_MethodCall('_Parameter*)";

    assertEquals(
      "Find statement in catch",
      1,
      findMatchesCount(s13,s14)
    );

    String s9 = "int a[] = new int[] { 1,2,3,4};\n" +
                "int b[] = { 2,3,4,5 };\n" +
                "Object[] c = new Object[] { \"\", null};";
    String s10 = "new '_ []{ '_* }";
    String s10_2 = "new int []{ '_* }";

    assertEquals(
      "Find array instatiation",
      3,
      findMatchesCount(s9,s10)
    );

    assertEquals(
      "Find array instatiation, 2",
      2,
      findMatchesCount(s9,s10_2)
    );
  }

  public void testLiteral() {
    String s = "class A {\n" +
               "  static String a = 1;\n" +
               "  static String s = \"aaa\";\n" +
               "  static String s2;\n" +
               "}";
    String s2 = "static String '_FieldName = '_Init?:[!regex( \".*\" )];";
    String s2_2 = "static String '_FieldName = '_Init:[!regex( \".*\" )];";

    assertEquals(
      "Literal",
      2,
      findMatchesCount(s,s2)
    );

    assertEquals(
      "Literal, 2",
      1,
      findMatchesCount(s,s2_2)
    );
  }

  public void testCovariantArraySearch() {
    String s1 = "String[] argv;";
    String s2 = "String argv;";
    String s3 = "'T[] argv;";
    String s3_2 = "'T:*Object [] argv;";

    assertEquals(
      "Find array types",
      0,
      findMatchesCount(s1,s2)
    );

    assertEquals(
      "Find array types, 2",
      0,
      findMatchesCount(s2,s1)
    );

    assertEquals(
      "Find array types, 3",
      0,
      findMatchesCount(s2,s3)
    );

    assertEquals(
      "Find array types, 3",
      1,
      findMatchesCount(s1,s3_2)
    );

    String s11 = "class A {\n" +
                 "  void main(String[] argv);" +
                 "  void main(String argv[]);" +
                 "  void main(String argv);" +
                 "}";
    String s12 = "'_t:[regex( *Object\\[\\] ) ] '_t2";
    String s12_2 = "'_t:[regex( *Object ) ] '_t2 []";
    String s12_3 = "'_t:[regex( *Object ) ] '_t2";

    assertEquals(
      "Find array covariant types",
      2,
      findMatchesCount(s11,s12)
    );

    assertEquals(
      "Find array covariant types, 2",
      2,
      findMatchesCount(s11,s12_2)
    );

    assertEquals(
      "Find array covariant types, 3",
      1,
      findMatchesCount(s11,s12_3)
    );
  }

  // @todo support back references (\1 in another reg exp or as fild member)
  //private static final String s1002 = " setSSS( instance.getSSS() ); " +
  //                                    " setSSS( instance.SSS ); ";
  //private static final String s1003 = " 't:set(.+) ( '_.get't_1() ); ";
  //private static final String s1003_2 = " 't:set(.+) ( '_.'t_1 ); ";

  public void testSearchStatements() {
    assertEquals("statement search",findMatchesCount(s1,s2),1);
    assertEquals("several constructions match",findMatchesCount(s5,s4),3);
    assertFalse("several constructions 2",(findMatchesCount(s5,s6))!=0);

    assertEquals("several constructions 3",findMatchesCount(s7,s8),2);
    assertEquals("several constructions 4",findMatchesCount(s7,s9),2);

    final String s1000 = "{ lastTest = \"search for parameterized pattern\";\n" +
                         "      matches = testMatcher.findMatches(s14_1,s15, options);\n" +
                         "      if (matches.size()!=2 ) return false;\n" +
                         "lastTest = \"search for parameterized pattern\";\n" +
                         "      matches = testMatcher.findMatches(s14_1,s15, options);\n" +
                         "      if (matches.size()!=2 ) return false; }";
    final String s1001 = "lastTest = '_Descr; " +
                         "      matches = testMatcher.findMatches('_In,'_Pattern, options);\n" +
                         "      if (matches.size()!='_Number ) return false;";

    assertEquals("several operators 5",findMatchesCount(s1000,s1001),2);

    assertEquals(
      "two the same statements search",
      findMatchesCount(s85,s86),
      1
    );

    assertEquals(
      "search for simple call",
      findMatchesCount(s87,s88),
      1
    );

    assertEquals(
      "search for simple call 2",
      findMatchesCount(s87,s88_2),
      1
    );

    assertEquals(
      "search for simple call 3",
      findMatchesCount(s87,s88_3),
      2
    );

    String s10015 = "DocumentListener[] listeners = getCachedListeners();";
    String s10016 = "'_Type 'Var = '_Call();";

    assertEquals(
      "search for definition with init",
      1,
      findMatchesCount(s10015,s10016)
    );

    String s10017 = "a = b; b = c; a=a; c=c;";
    String s10018 = "'_a = '_a;";

    assertEquals(
      "search silly assignments",
      2,
      findMatchesCount(s10017,s10018)
    );

    String s10019 = "a.b(); a.b(null); a.b(null, 1);";
    String s10020 = "a.b(null);";

    assertEquals(
      "search parameter",
      1,
      findMatchesCount(s10019,s10020)
    );

    //String s1008 = "int a, b, c, d; int a,b,c; int c,d; int e;";
    //String s1009 = "int 'a{3,4};";
    //
    //assertEquals(
    //  "search many declarations",
    //  2,
    //  findMatchesCount(s1008,s1009)
    //);

    //String s1 = "super(1,1);  call(1,1); call(2,2);";
    //String s2 = "super('t*);";
    //
    //assertEquals(
    //  "search super",
    //  1,
    //  findMatchesCount(s10019,s10020)
    //);

    String s10021 = "short a = 1;\n" +
                    "short b = 2;\n" +
                    "short c = a.b();";
    String s10022 = "short '_a = '_b.b();";

    assertEquals(
      "search def init bug",
      1,
      findMatchesCount(s10021,s10022)
    );

    String s10023 = "abstract class A { public abstract short getType(); }\n" +
                    "A a;\n" +
                    "switch(a.getType()) {\n" +
                    "  default:\n" +
                    "  return 0;\n" +
                    "}\n" +
                    "switch(a.getType()) {\n" +
                    "  case 1:\n" +
                    "  { return 0; }\n" +
                    "}";
    String s10024 = "switch('_a:[exprtype( short )]) { '_statement*; }";
    assertEquals(
      "finding switch",
      2,
      findMatchesCount(s10023,s10024)
    );

    String s10025 = "A[] a;\n" +
                    "A b[];\n" +
                    "A c;";
    String s10026 = "A[] 'a;";
    String s10026_2 = "A 'a[];";

    assertEquals(
      "array types in dcl",
      2,
      findMatchesCount(s10025,s10026)
    );

    assertEquals(
      "array types in dcl 2",
      2,
      findMatchesCount(s10025,s10026_2)
    );

    String s10027 = "try { a(); } catch(Exception ex) {}\n" +
                    "try { a(); } finally {}\n" +
                    "try { a(); } catch(Exception ex) {} finally {} \n";
    String s10028 = "try { a(); } finally {}\n";
    assertEquals(
      "finally matching",
      2,
      findMatchesCount(s10027,s10028)
    );

    String s10029 = "for(String a:b) { System.out.println(a); }";
    String s10030 = "for(String a:b) { '_a; }";
    assertEquals(
      "for each matching",
      1,
      findMatchesCount(s10029,s10030)
    );

    //String s10031 = "try { a(); } catch(Exception ex) {} catch(Error error) { 1=1; }\n" +
    //                "try { a(); } catch(Exception ex) {}";
    //String s10032 = "try { a(); } catch('_Type+ 'Arg+) { 'Statements*; }\n";
    //assertEquals(
    //  "finally matching",
    //  2,
    //  findMatchesCount(s10031,s10032)
    //);

    String s10033 = "return x;\n" +
                    "return !x;\n" +
                    "return (x);\n" +
                    "return (x);\n" +
                    "return !(x);";
    String s10034 = "return ('a);";
    assertEquals("Find statement with parethesized expr",2,findMatchesCount(s10033,s10034));
  }

  public void testSearchClass() {
    // no modifer list in interface vars
    assertEquals(
      "no modifier for interface vars",
      findMatchesCount(s43,s44),
      3
    );

    // different order of access modifiers
    assertEquals(
      "different order of access modifiers",
      findMatchesCount(s45,s46),
      1
    );

    // no access modifiers
    assertEquals(
      "no access modifier",
      findMatchesCount(s45,s46_2),
      2
    );

    // type could differ with package
    assertEquals(
      "type differs with package",
      findMatchesCount(s47,s48),
      2
    );

    // reference element could differ in package
    assertEquals(
      "reference could differ in package",
      findMatchesCount(s49,s50),
      1
    );

    //String s51 = "class C extends java.awt.List {} class A extends java.util.List {} class B extends java.awt.List {} ";
    //String s52 = "class 'B extends 'C:java\\.awt\\.List {}";
    //
    //assertEquals(
    //  "reference could differ in package 2",
    //  findMatchesCount(s51,s52),
    //  2
    //);

    assertEquals(
      "method access modifier",
      findMatchesCount(s93,s94),
      0
    );

    assertEquals(
      "method access modifier 2",
      findMatchesCount(s93,s94_2),
      1
    );

    assertEquals(
      "field access modifier",
      findMatchesCount(s93,s94_3),
      0
    );

    assertEquals(
      "field access modifier 2",
      findMatchesCount(s93,s94_4),
      1
    );

    final String s127 = "class a { void b() { new c() {}; } }";
      final String s128 = "class 't {}";
    assertEquals(
      "class finds anonymous class",
      findMatchesCount(s127,s128),
      2
    );

    final String s129 = "class a { public void run() {} }\n" +
                        "class a2 { public void run() { run(); } }\n" +
                        "class a3 { public void run() { run(); } }\n" +
                        "class a4 { public void run(); }";

    final String s130 = "class 'a { public void run() {} }";
    final String s130_2 = "class 'a { public void run() { '_statement; } }";
    final String s130_3 = "class 'a { public void run(); }";

    assertEquals(
      "empty method finds empty method only",
      findMatchesCount(s129,s130),
      1
    );

    assertEquals(
      "nonempty method finds nonempty method",
      findMatchesCount(s129,s130_2),
      2
    );

    assertEquals(
      "nonempty method finds nonempty method",
      findMatchesCount(s129,s130_3),
      4
    );

    //final String s131 = "class a { void run() {} int b; class c {} }\n" +
    //                    "public class a2 { public void run() {} private class c {} private int c; }\n" +
    //                    "class a3 { private void run() {} protected class c {} }\n" +
    //                    "new A() {}";
    //final String s132 = "/** @--strictly */ class 'a { void run() {} }";
    //final String s132_2 = "class 'a { /** @--strictly */ void run() {} }";
    //final String s132_3 = "class 'a { /** @--strictly */ int 'c; }";
    //final String s132_4 = "class 'a { /** @--strictly */ class 'c {} ; }";
    //
    //assertEquals(
    //  "strictly matching",
    //  findMatchesCount(s131,s132),
    //  2
    //);
    //assertEquals(
    //  "strictly matching",
    //  findMatchesCount(s131,s132_2),
    //  2
    //);
    //assertEquals(
    //  "strictly matching",
    //  findMatchesCount(s131,s132_3),
    //  2
    //);
    //assertEquals(
    //  "strictly matching",
    //  findMatchesCount(s129,s132_4),
    //  2
    //);

    final String s133 = "class S {\n" +
                        "void cc() {\n" +
                        "        new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                f();\n" +
                        "            }\n" +
                        "            private void f() {\n" +
                        "                //To change body of created methods use File | Settings | File Templates.\n" +
                        "            }\n" +
                        "        };\n" +
                        "        new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                f();\n" +
                        "            }\n" +
                        "            private void g() {\n" +
                        "                //To change body of created methods use File | Settings | File Templates.\n" +
                        "            }\n" +
                        "        };\n" +
                        "        new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                f();\n" +
                        "            }\n" +
                        "        };\n" +
                        "    }\n" +
                        "    private void f() {\n" +
                        "        //To change body of created methods use File | Settings | File Templates.\n" +
                        "    }\n" +
                        "} ";
    final String s134 = "new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                '_f ();\n" +
                        "            }\n" +
                        "            private void '_f ();\n" +
                        "        }";
    assertEquals(
      "complex expr matching",
      1,
      findMatchesCount(s133,s134)
    );
    //final String s133 = "class A extends java.util.List {}"

    final String s135 = "abstract class My {\n" +
                        "    abstract void f();\n" +
                        "}\n" +
                        "abstract class My2 {\n" +
                        "    abstract void f();\n" +
                        "    void fg() {}\n" +
                        "}";
    final String s136 = "class 'm {\n" +
                        "    void f();\n" +
                        "    '_type '_method{0,0} ('_paramtype* '_paramname* );\n" +
                        "}";
    assertEquals(
      "reject method with 0 max occurence",
      findMatchesCount(s135,s136),
      1
    );

    final String s137 = "abstract class My {\n" +
                        "  int a;\n" +
                        "}\n" +
                        "abstract class My2 {\n" +
                        "    Project b;\n" +
                        "}" +
                        "abstract class My3 {\n" +
                        "    Class clazz;"+
                        "    Project b = null;\n" +
                        "}" +
                        "abstract class My {\n" +
                        "  int a = 1;\n" +
                        "}\n";
    final String s138 = "class 'm {\n" +
                        "    Project '_f{0,0} = '_t?;\n" +
                        "}";
    assertEquals(
      "reject field with 0 max occurence",
      findMatchesCount(s137,s138),
      2
    );

    final String s139 = "class My { boolean equals(Object o); int hashCode(); }";
    final String s139_2 = "class My { boolean equals(Object o); }";
    final String s140 = "class 'A { boolean equals(Object '_o ); int '_hashCode{0,0}:hashCode (); }";

    assertEquals(
      "reject method with constraint",
      findMatchesCount(s139,s140),
      0
    );

    assertEquals(
      "reject field with 0 max occurence",
      findMatchesCount(s139_2,s140),
      1
    );

    final String s141 = "class A { static { a = 10 } }\n" +
                        "class B { { a = 10; } }\n" +
                        "class C { { a = 10; } }";
    final String s142 = "class '_ { static { a = 10; } } ";
    assertEquals(
      "static block search",
      findMatchesCount(s141,s142),
      1
    );

    final String s143 = "class A { A() {} };\n" +
                        "class B { B(int a) {} };\n" +
                        "class C { C() {} C(int a) {} };\n" +
                        "class D {}\n" +
                        "class E {}";
    final String s144 = "class 'a { 'd{0,0}:[ script( a == d ) ]('_b+ '_c+); }";
    assertEquals(
      "parameterless contructor search",
      3, //findMatchesCount(s143,s144), // TODO fix this!
      3
    );
  }

  public void testExprTypeWithObject() {
    String s1 = "import java.util.*;\n" +
                "class A {\n" +
                "  void b() {\n" +
                "    Map map = new HashMap();" +
                "    class AppPreferences {}\n" +
                "    String key = \"key\";\n" +
                "    AppPreferences value = new AppPreferences();\n" +
                "    map.put(key, value );\n" +
                "    map.put(value, value );\n" +
                "    map.put(\"key\", value );\n" +
                "    map.put(\"key\", new AppPreferences());\n" +
                "  }\n" +
                "}";
    String s2 = "'_map:[exprtype( *java\\.util\\.Map )].put('_key:[ exprtype( *Object ) ], '_value:[ exprtype( *AppPreferences ) ]);";

    assertEquals(
      "expr type with object",
      4,
      findMatchesCount(s1,s2,true)
    );
  }

  public void testInterfaceImplementationsSearch() {
    String in = "class A implements Cloneable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class B implements Serializable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class C implements Cloneable,Serializable {\n" +
                "    \n" +
                "  }\n" +
                "  class C2 implements Serializable,Cloneable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class E extends B implements Cloneable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class F extends A implements Serializable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class D extends C {\n" +
                "    \n" +
                "  }";
    String what = "class 'A implements '_B:*Serializable , '_C:*Cloneable {}";
    assertEquals(
      "search interface within hierarchy",
      5,
      findMatchesCount(in, what)
    );
  }

  public void testSearchBacktracking() {
    assertEquals(
      "backtracking greedy regexp",
      findMatchesCount(s89,s90),
      1
    );

    assertEquals(
      "backtracking greedy regexp 2",
      findMatchesCount(s89,s90_2),
      1
    );

    assertEquals(
      "backtracking greedy regexp 3",
      findMatchesCount(s89,s90_3),
      0
    );

    assertEquals(
      "counted regexp (with back tracking)",
      findMatchesCount(s89,s90_4),
      1
    );

    assertEquals(
      "nongreedy regexp (counted, with back tracking)",
      findMatchesCount(s89,s90_5),
      1
    );

    assertEquals(
      "nongreedy regexp (counted, with back tracking) 2",
      findMatchesCount(s89,s90_6),
      0
    );

    String s1000 = "class A {\n" +
                   "      void _() {}\n" +
                   "      void a(String in, String pattern) {}\n" +
                   "    }";
    String s1001 = "class '_Class { \n" +
                   "  '_ReturnType+ 'MethodName+ ('_ParameterType* '_Parameter* );\n" +
                   "}";
    assertEquals(
      "handling of no match",
      findMatchesCount(s1000,s1001),
      2
    );
  }

  public void testSearchSymbol() {
    final String s131 = "a.b(); c.d = 1; ";
    final String s132 = "'T:b|d";

    assertEquals(
      "symbol match",
      findMatchesCount(s131,s132),
      2
    );

    final String s129 = "A a = new A();";
    final String s130 = "'Sym:A";

    options.setCaseSensitiveMatch(true);
    assertEquals(
      "case sensitive match",
      findMatchesCount(s129,s130),
      2
    );

    options.setDistinct(true);
    assertEquals(
      "case sensitive disitinct match",
      findMatchesCount(s129,s130),
      1
    );

    options.setDistinct(false);
    options.setCaseSensitiveMatch(false);

  }

  public void testSearchGenerics() {
    assertEquals(
      "parameterized class match",
      findMatchesCount(s81,s82),
      2
    );

    assertEquals(
      "parameterized instanceof match",
      findMatchesCount(s81,s82_2),
      1
    );

    assertEquals(
      "parameterized cast match",
      findMatchesCount(s81,s82_3),
      1
    );

    assertEquals(
      "parameterized definition match",
      findMatchesCount(s81,s82_4),
      3
    );

    assertEquals(
      "parameterized method match",
      findMatchesCount(s81,s82_5),
      1
    );

    assertEquals(
      "parameterized constraint match",
      findMatchesCount(s81_2,s82_6),
      2
    );

    assertEquals(
      "symbol matches parameterization",
      findMatchesCount(s81,s82_7),
      27
    );

    assertEquals(
      "symbol matches parameterization 2",
      findMatchesCount(s81_2,s82_7),
      7
    );

    String s81_3 = " class A {\n" +
                   "  public static <T> Collection<T> unmodifiableCollection(int c) {\n" +
                   "    return new d<T>(c);\n" +
                   "  }\n" +
                   "  static class d<E> implements Collection<E>, Serializable {\n" +
                   "    public <T> T[] toArray(T[] a)       {return c.toArray(a);}\n" +
                   "  }\n" +
                   "}";
    assertEquals(
      "typed symbol symbol",
      findMatchesCount(s81_3,s82_5),
      2
    );

    String s81_4="class A<B> { \n" +
                 "  static <C> void c(D<E> f) throws R<S> {\n" +
                 "    if ( f instanceof G<H>) {\n" +
                 "      ((I<G<K>>)l).a();\n" +
                 "      throw new P<Q>();" +
                 "    }\n" +
                 "  }\n" +
                 "} " +
                 "class C {\n" +
                 "  void d(E f) throws Q {\n" +
                 "    if (g instanceof H) { a.c(); b.d(new A() {}); throw new Exception(((I)k)); }"+
                 "  }\n" +
                 "}";
    String s82_8 = "'T<'_Subst+>";
    assertEquals(
      "typed symbol",
      findMatchesCount(s81_4,s82_8),
      6
    );

    // @todo typed vars constrains (super),
    // @todo generic method invocation

    //String s83 = "class A {} List<A> a; List b;";
    //String s84 = "'a:List 'c;";
    //String s84_2 = "'a:List\\<'_\\> 'c;";
    //String s84_3 = "'a:List(?>\\<'_\\>) 'c;";
    //
    //assertEquals(
    //  "finding list",
    //  findMatchesCount(s83,s84),
    //  2
    //);
    //
    //assertEquals(
    //  "finding list 2",
    //  findMatchesCount(s83,s84_2),
    //  1
    //);
    //
    //assertEquals(
    //  "finding list 3",
    //  findMatchesCount(s83,s84_3),
    //  1
    //);
  }

  public void testSearchSubstitutions() {
    // searching for parameterized pattern
    assertEquals("search for parameterized pattern",findMatchesCount(s14_1,s15),2);

    assertEquals("search for parameterized pattern 2",findMatchesCount(s14_2,s15),5);

    options.setRecursiveSearch(false);

    assertEquals("search for parameterized pattern-non-recursive",findMatchesCount(s14_1,s15),1);

    assertEquals("search for parameterized pattern 2-non-recursive",findMatchesCount(s14_2,s15),2);

    // typed vars with arrays
    assertEquals("typed pattern with array 2-non-recursive",findMatchesCount(s23,s24_2),4);

    options.setRecursiveSearch(true);

      // searching for parameterized pattern
    assertEquals("search for parameterized pattern 3",findMatchesCount(s14_2,s16),1);

    // searching for parameterized pattern in complex expr (with field selection)
    assertEquals("search for parameterized pattern in field selection",findMatchesCount(s17,s18_1),1);

    // searching for parameterized pattern in complex expr (with method call)
    assertEquals("search for parameterized pattern with method call",findMatchesCount(s17,s18_2),1);

    // searching for parameterized pattern in complex expr (with method call)
    assertEquals("search for parameterized pattern with method call ep.2",findMatchesCount(s17,s18_3),4);

    // searching for parameterized pattern in definition with initializer
    assertEquals("search for same var constraint",findMatchesCount(s19,s20),1);

    // searching for semi anonymous parameterized pattern in definition with initializer
    assertEquals("search for same var constraint for semi anonymous typed vars",findMatchesCount(s19,s20_2),1);

    // support for type var constraint
    assertEquals("search for typed var constraint",findMatchesCount(s22,s21_1),1);

    // noncompatible same typed var constraints
    try {
      findMatchesCount(s22,s21_2);
      assertFalse("search for noncompatible typed var constraint",false);
    } catch(MalformedPatternException e) {
    }

      // compatible same typed var constraints
    assertEquals("search for same typed var constraint",findMatchesCount(s22,s21_3),1);

    // typed var with instanceof
    assertEquals("typed instanceof",findMatchesCount(s65,s66),1);

    // typed vars with arrays
    assertEquals("typed pattern with array",findMatchesCount(s23,s24_1),2);

    // typed vars with arrays
    assertEquals("typed pattern with array 2",findMatchesCount(s23,s24_2),6);

    // typed vars in class name, method name, its return type, parameter type and name
    assertEquals("typed pattern in class name, method name, return type, parameter type and name",findMatchesCount(s25,s26),1);

    assertEquals(
      "finding interface",
      findMatchesCount(s27,s28),
      1
    );

    // finding anonymous type vars
    assertEquals(
      "anonymous typed vars",
      findMatchesCount(s29,s30),
      1
    );

    // finding descedants
    assertEquals(
      "finding class descendants",
      findMatchesCount(s31,s32),
      2
    );

    // finding interface implementation
    assertEquals(
      "interface implementation",
      findMatchesCount(s33,s34),
      2
    );

    // different order of fields and methods
    assertEquals(
      "different order of fields and methods",
      findMatchesCount(s35,s36),
      1
    );

    // different order of exceptions in throws
    assertEquals(
      "differend order in throws",
      findMatchesCount(s37,s38),
      1
    );

    // class pattern without extends matches pattern with extends
    assertEquals(
      "match of class without extends to class with it",
      findMatchesCount(s39,s40),
      2
    );

    // class pattern without extends matches pattern with extends
    assertEquals(
      "match of class without extends to class with it, ep. 2",
      findMatchesCount(s41,s42_1),
      2
    );

    // class pattern without extends matches pattern with extends
    assertEquals(
      "match of class without extends to class with it, ep 3",
      findMatchesCount(s41,s42_2),
      2
    );

    // typed reference element
    assertEquals(
      "typed reference element",
      findMatchesCount(s51,s52),
      2
    );

    // empty name of type var
    assertEquals(
      "empty name for typed var",
      findMatchesCount(s59,s60),
      1
    );

    // comparing method with constructor
    assertEquals(
      "comparing method with constructor",
      findMatchesCount(s63,s64),
      1
    );

    // comparing method with constructor
    assertEquals(
      "finding nested class",
      findMatchesCount(s63_2,s64),
      2
    );

    // comparing method with constructor
    assertEquals(
      "finded nested class by special pattern",
      findMatchesCount(s63_2,s64_2),
      1
    );

    assertEquals(
      "* regexp for typed var",
      findMatchesCount(s61,s62_1),
      5
    );

    assertEquals(
      "+ regexp for typed var",
      findMatchesCount(s61,s62_2),
      4
    );

    assertEquals(
      "? regexp for typed var",
      findMatchesCount(s61,s62_3),
      2
    );

    assertEquals(
      "cast in method parameters",
      findMatchesCount(s67,s68),
      1
    );

    assertEquals(
      "searching for static field in static call",
      findMatchesCount(s69,s70),
      1
    );

    assertEquals(
      "* regexp for anonymous typed var",
      findMatchesCount(s61,s62_4),
      3
    );

    assertEquals(
      "+ regexp for anonymous typed var",
      findMatchesCount(s61,s62_5),
      2
    );

    assertEquals(
      "? regexp for anonymous typed var",
      findMatchesCount(s61,s62_6),
      2
    );

    assertEquals(
      "statement inside anonymous class",
      findMatchesCount(s71,s72),
      3
    );

    assertEquals(
      "clever regexp match",
      findMatchesCount(s91,s92),
      2
    );

    assertEquals(
      "clever regexp match 2",
      findMatchesCount(s91,s92_2),
      2
    );

    assertEquals(
      "clever regexp match 3",
      findMatchesCount(s91,s92_3),
      2
    );
  }

  public void testSearchJavaDoc() {
    // javadoc comment in class
    assertEquals(
      "java doc comment in class",
      1,
      findMatchesCount(s57,s58)
    );

    assertEquals(
      "java doc comment in class in file",
      1,
      findMatchesCount(s57_2,s58,true)
    );

    // javadoc comment for field
    assertEquals(
      "javadoc coment for field",
      findMatchesCount(s57,s58_2),
      2
    );

    // javadoc comment for method
    assertEquals(
      "javadoc coment for method",
      findMatchesCount(s57,s58_3),
      3
    );

    // just javadoc comment search
    assertEquals(
      "just javadoc comment search",
      findMatchesCount(s57,s58_4),
      4
    );

    if (IdeaTestUtil.bombExplodes(2006, Calendar.SEPTEMBER, 20, 15, 0, "maxim.mossienko", "next token after tag correctly becomes " +
                                                                                         "a tag parameter even if located on next line." +
                                                                                         "Leading asterisks should not be counted as well.")) {
      assertEquals(
      "XDoclet metadata",
        findMatchesCount(s83,s84),
        2
      );

      assertEquals(
      "XDoclet metadata 2",
        findMatchesCount(s83,s84_2),
        1
      );
    }

    assertEquals(
      "optional tag value match",
      findMatchesCount(s57,s58_5),
      6
    );

    assertEquals(
      "multiple tags match +",
      findMatchesCount(s75,s76),
      2
    );

    assertEquals(
      "multiple tags match *",
      findMatchesCount(s75,s76_2),
      3
    );

    assertEquals(
      "multiple tags match ?",
      findMatchesCount(s75,s76_3),
      3
    );

  }

  public void testNamedPatterns() {
    String s133 = "class String1 implements java.io.Serializable { " +
                  "private static final long serialVersionUID = -6849794470754667710L;" +
                  "private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];" +
                  "}" +
                  "class StringBuilder1 implements java.io.Serializable {" +
                  "    private void writeObject(java.io.ObjectOutputStream s)\n" +
                  "        throws java.io.IOException {\n" +
                  "        s.defaultWriteObject();\n" +
                  "    }" +
                  "private void readObject(java.io.ObjectInputStream s)\n" +
                  "        throws java.io.IOException, ClassNotFoundException {\n" +
                  "        s.defaultReadObject();\n" +
                  "    }" +
                  "    static final long serialVersionUID = 4383685877147921099L;" +
                  "}";
    String s134 = "class '_ implements '_:*Serializable {\n" +
                  "  static final long 'VersionField?:serialVersionUID = '_?;\n" +
                  "  private static final ObjectStreamField[] '_?:serialPersistentFields = '_?; \n" +
                  "  private void '_SerializationWriteHandler?:writeObject (ObjectOutputStream s) throws IOException;\n" +
                  "  private void '_SerializationReadHandler?:readObject (ObjectInputStream s) throws IOException, ClassNotFoundException;\n" +
                  "  Object '_SpecialSerializationReadHandler?:readResolve () throws ObjectStreamException;" +
                  "  Object '_SpecialSerializationWriteHandler?:writeReplace () throws ObjectStreamException;" +
                  "}";

    assertEquals(
      "serialization match",
      findMatchesCount(s133,s134),
      2
    );

    String s135 = "class SimpleStudentEventActionImpl extends Action { " +
                  "  public ActionForward execute(ActionMapping mapping,\n" +
                  "         ActionForm _form,\n" +
                  "         HttpServletRequest _request,\n" +
                  "         HttpServletResponse _response)" +
                  "  throws Exception {}" +
                  "} " +
                  "public class DoEnrollStudent extends SimpleStudentEventActionImpl { }" +
                  "public class DoCancelStudent extends SimpleStudentEventActionImpl { }";
    String s136 = "public class 'StrutsActionClass extends '_*:Action {" +
                  "  public ActionForward '_AnActionMethod:*execute (ActionMapping '_,\n" +
                  "                                 ActionForm '_,\n" +
                  "                                 HttpServletRequest '_,\n" +
                  "                                 HttpServletResponse '_);" +
                  "}";

    assertEquals(
      "Struts actions",
      findMatchesCount(s135,s136),
      2
    );

    final String s123 = "class NodeFilter {} public class MethodFilter extends NodeFilter {\n" +
                        "  private MethodFilter() {}\n" +
                        "\n" +
                        "  public static NodeFilter getInstance() {\n" +
                        "    if (instance==null) instance = new MethodFilter();\n" +
                        "    return instance;\n" +
                        "  }\n" +
                        "  private static NodeFilter instance;\n" +
                        "}";
    final String s124 = "class 'Class {\n" +
                        "  private 'Class('_* '_*) {\n" +
                        "   '_*;\n" +
                        "  }\n" +
                        "  private static '_Class2:* '_Instance;\n" +
                        "  static '_Class2:* '_GetInstance() {\n" +
                        "    '_*;\n" +
                        "    return '_Instance;\n" +
                        "  }\n" +
                        "}";

    assertEquals(
      "singleton search",
      findMatchesCount(s123,s124),
      1
    );

    String s1111 = "if (true) { a=1; b=1; } else { a=1; }\n" +
                   "if(true) { a=1; } else { a=1; b=1; }\n" +
                   "if(true) { a=1; b=2; } else { a = 1; b=2; }";
    String s1112 = "if (true) { '_a{1,2}; } else { '_a; }";

    assertEquals(
      "same multiple name pattern",
      findMatchesCount(s1111,s1112),
      1
    );
  }

  public void testHierarchy() {
    final String s105 = "class B {} class A extends B { }";
    final String s106 = "class '_ extends '_:[ref('T)] {}";
    assertEquals(
      "extends match",
      findMatchesCount(s105,s106),
      1
    );

    final String s107 = "interface IA {} interface IB extends IA { } interface IC extends IB {} interface ID extends IC {}" +
                        "class A implemenents IA {} class B extends A { } class C extends B implements IC {} class D extends C {}";
    final String s108 = "class '_ extends 'Type:+A {}";
    final String s108_2 = "class '_ implements 'Type:+IA {}";

    assertEquals(
      "extends navigation match",
      findMatchesCount(s107,s108),
      2
    );

    assertEquals(
      "implements navigation match",
      2,
      findMatchesCount(s107,s108_2)
    );

    final String s109 = "interface I {} interface I2 extends I {} class A implements I2 {} class B extends A { } class C extends B {} class D { void e() { C c; B b; A a;} }";
    final String s110 = "'_:*A '_;";
    final String s110_2 = "'_:*I '_;";
    final String s110_3 = "'_:*[regex( I ) && ref('T)] '_;";
    final String s110_4 = "'_:*[regex( I ) && ref2('T)] '_;";
    assertEquals(
      "extends navigation match in definition",
      findMatchesCount(s109,s110),
      3
    );

    assertEquals(
      "implements navigation match in definition 2",
      findMatchesCount(s109,s110_2),
      3
    );

    assertEquals(
      "implements navigation match in definition 2 with nested conditions",
      findMatchesCount(s109,s110_3),
      3
    );

    try {
      findMatchesCount(s109,s110_4);
      assertFalse("implements navigation match in definition 2 with nested conditions - incorrect cond",false);
    } catch(UnsupportedPatternException ex) {}

    final String s111 = "interface E {} class A implements E {} class B extends A { int f = 0; } class C extends B {} class D { void e() { C c; B b; A a;} }";
    final String s112 = "'_";
    assertEquals(
      "symbol match",
      findMatchesCount(s111,s112),
      17
    );

    final String s113 = "class B {int c; void d() {} } int a; B b; a = 1; b.d(); ++a; int c=a; System.out.println(a); " +
                        "b.c = 1; System.out.println(b.c); b.c++;";
    final String s114 = "'_:[read]";
    final String s114_2 = "'_:[write]";
    assertEquals(
      "read symbol match",
      findMatchesCount(s113,s114),
      11
    );

    assertEquals(
      "write symbol match",
      findMatchesCount(s113,s114_2),
      5
    );

    final String s115 = "class B {} public class C {}";
    final String s116 = "public class '_ {}";
    assertEquals(
      "public modifier for class",
      findMatchesCount(s115,s116),
      1
    );

    final String s117 = "class A { int b; void c() { int e; b=1; this.b=1; e=5; " +
                        "System.out.println(e); " +
                        "System.out.println(b); System.out.println(this.b);} }";
    final String s118 = "this.'Field";
    final String s118_2 = "this.'Field:[read]";
    final String s118_3 = "this.'Field:[write]";

    assertEquals(
      "fields of class",
      findMatchesCount(s117,s118),
      4
    );

    assertEquals(
      "fields of class read",
      findMatchesCount(s117,s118_2),
      2
    );

    assertEquals(
      "fields of class written",
      findMatchesCount(s117,s118_3),
      2
    );

    final String s119 = "try { a.b(); } catch(IOException e) { c(); } catch(Exception ex) { d(); }";
    final String s120 = "try { '_; } catch('_ '_) { '_; }";
    final String s120_2 = "try { '_; } catch(Throwable '_) { '_; }";
    assertEquals(
      "catches loose matching",
      findMatchesCount(s119,s120),
      1
    );

    assertEquals(
      "catches loose matching 2",
      findMatchesCount(s119,s120_2),
      0
    );

    final String s121 = "class A { private int a; class Inner {} } " +
                        "class B extends A { private int a; class Inner2 {} }";
    final String s122 = "class '_ { int '_:* ; }";
    final String s122_2 = "class '_ { int '_:+hashCode (); }";
    final String s122_3 = "class '_ { class '_:* {} }";
    assertEquals(
      "hierarchical matching",
      findMatchesCount(s121,s122),
      2
    );

    assertEquals(
      "hierarchical matching 2",
      findMatchesCount(s121,s122_2),
      4
    );

    assertEquals(
      "hierarchical matching 3",
      findMatchesCount(s121,s122_3),
      2
    );
  }

  public void testSearchInCommentsAndLiterals() {
    String s1 = "{" +
                "// This is some comment\n" +
                "/* This is another\n comment*/\n" +
                "// Some garbage\n"+
                "/** And now third comment*/\n" +
                "/** Some garbage*/ }";
    String s2 = "// 'Comment:[regex( .*(?:comment).* )]";
    String s3 = "/** 'Comment:[regex( .*(?:comment).* )] */";
    String s2_2 = "/* 'Comment:[regex( .*(?:comment).* )] */";

    assertEquals(
      "Comment matching",
      findMatchesCount(s1,s2),
      3
    );

    assertEquals(
      "Comment matching, 2",
      3,
      findMatchesCount(s1,s2_2)
    );

    assertEquals(
      "Java doc matching",
      findMatchesCount(s1,s3),
      1
    );

    String s4 = "\"'test\", \"another test\", \"garbage\"";
    String s5 = "\"'test:[regex( .*test.* )]\"";
    String s6 = "\"''test\"";

    assertEquals(
      "Literal content",
      findMatchesCount(s4,s5),
      2
    );

    assertEquals(
      "Literal content with escaping",
      findMatchesCount(s4,s6),
      1
    );

    String s7 = "\"aaa\"";
    String s8 = "\"'test:[regex( aaa )]\"";

    assertEquals(
      "Simple literal content",
      findMatchesCount(s7,s8),
      1
    );

    String s9 = "\" aaa \" \" bbb \" \" ccc ccc aaa\"";
    String s10 = "\"'test:[regexw( aaa|ccc )]\"";
    String s11 = "\"'test:[regexw( bbb )]\"";

    assertEquals(
      "Whole word literal content with alternations",
      findMatchesCount(s9,s10),
      2
    );

    assertEquals(
      "Whole word literal content",
      findMatchesCount(s9,s11),
      1
    );

    String s12 = "assert agentInfo != null : \"agentInfo is null\";\n" +
                 "assert addresses != null : \"addresses is null\";";
    String s13 = "assert $exp$ != null : \"$exp$ is null\";";

    assertEquals(
      "reference to substitution in comment",
      findMatchesCount(s12,s13),
      2
    );

    String s14 = "\"(some text with special chars)\"," +
                 "\" some\"," +
                 "\"(some)\"";
    String s15 = "\"('a:[regexw( some )])\"";

    assertEquals(
      "meta char in literal",
      2,
      findMatchesCount(s14,s15)
    );

    String s16 = "/**\n" +
                 "* Created by IntelliJ IDEA.\n" +
                 "* User: cdr\n" +
                 "* Date: Nov 15, 2005\n" +
                 "* Time: 4:23:29 PM\n" +
                 "* To change this template use File | Settings | File Templates.\n" +
                 "*/\n" +
                 "public class Y {\n" +
                 "}";
    String s17 = "/**\n" +
                 "* Created by IntelliJ IDEA.\n" +
                 "* User: '_USER\n" +
                 "* Date: '_DATE\n" +
                 "* Time: '_TIME\n" +
                 "* To change this template use File | Settings | File Templates.\n" +
                 "*/\n" +
                 "class 'c {\n" +
                 "}";
    assertEquals(
      "complete comment match",
      1,
      findMatchesCount(s16,s17,true)
    );

    String s18 = "public class A {\n" +
                 "   private void f(int i) {\n" +
                 "       int g=0; //sss\n" +
                 "   }\n" +
                 "}";
    String s19 = "class $c$ {\n" +
                 "   $type$ $f$($t$ $p$){\n" +
                 "       $s$; // sss\n" +
                 "   }\n" +
                 "}";
    assertEquals(
      "statement match with comment",
      1,
      findMatchesCount(s18,s19)
    );
  }

  public void testOther() {
    assertEquals(
      "optional init match in definition",
      findMatchesCount(s73,s74),
      4
    );

    assertEquals(
      "null match",
      findMatchesCount(s77,s78),
      0
    );

    assertEquals(
      "body of method by block search",
      findMatchesCount(s79,s80),
      2
    );


    assertEquals(
      "first matches, next not",
      findMatchesCount(s95,s96),
      2
    );

    final String s97 = "class A { int c; void b() { C d; } } class C { C() { A a; a.b(); a.c=1; } }";
    final String s98 = "'_.'_:[ref('T)] ()";
    final String s98_2 = "'_.'_:[ref('T)]";
    final String s98_3 = "'_:[ref('T)].'_ ();";
    final String s98_4 = "'_:[ref('T)] '_;";

    assertEquals(
      "method predicate match",
      findMatchesCount(s97,s98),
      1
    );

    assertEquals(
      "field predicate match",
      findMatchesCount(s97,s98_2),
      1
    );

    assertEquals(
      "dcl predicate match",
      findMatchesCount(s97,s98_3),
      1
    );

    final String s99 = " char s = '\\u1111';  char s1 = '\\n'; ";
    final String s100 = " char 'var = '\\u1111'; ";
    final String s100_2 = " char 'var = '\\n'; ";
    assertEquals(
      "char constants in pattern",
      findMatchesCount(s99,s100),
      1
    );

    assertEquals(
      "char constants in pattern 2",
      findMatchesCount(s99,s100_2),
      1
    );

    assertEquals(
      "class predicate match (from definition)",
      findMatchesCount(s97,s98_4),
      3
    );

    final String s125 = "a=1;";
    final String s126 = "'t:[regex(a)]";

    try {
      findMatchesCount(s125,s126);
      assertFalse("spaces around reg exp check",false);
    } catch(MalformedPatternException ex) {}

    options.setDistinct(true);

    final String s101 = "class A { void b() { String d; String e; String[] f; f.length=1; f.length=1; } }";
    final String s102 = "'_:[ref('T)] '_;";

    assertEquals(
      "distinct match",
      findMatchesCount(s101,s102),
      1
    );

    options.setDistinct(false);

    final String s103 = " a=1; ";
    final String s104 = "'T:{ ;";
    try {
      findMatchesCount(s103,s104);
      assertFalse("incorrect reg exp",false);
    } catch(MalformedPatternException ex) {
    }

    final String s106 = "$_ReturnType$ $MethodName$($_ParameterType$ $_Parameter$);";
    final String s105 = " aaa; ";

    try {
      findMatchesCount(s105,s106);
      assertFalse("incorrect reg exp 2",false);
    } catch(UnsupportedPatternException ex) {
    }

    String s107 = "class A {\n" +
                  "  /* */\n" +
                  "  void a() {\n" +
                  "  }" +
                  "  /* */\n" +
                  "  int b = 1;\n" +
                  "  /*" +
                  "   *" +
                  "   */\n" +
                  "   class C {}" +
                  "}";
    String s108 = "  /*" +
                  "   *" +
                  "   */";

    assertEquals("finding comments without typed var", 1, findMatchesCount(s107,s108));

    String s109 = "class A { void b(); int b(int c); char d(char e); }\n" +
                  "A a; a.b(1); a.b(2); a.b(); a.d('e'); a.d('f'); a.d('g');";
    String s110 = "'_a.'_b:[exprtype( int ) ]('_c*);";
    assertEquals("caring about method return type", 2, findMatchesCount(s109,s110));

    String s111 = "class A { void getManager() { getManager(); } };\n" +
                  "class B { void getManager() { getManager(); getManager(); } };";
    String s112 = "'Instance?:[exprtype( B )].getManager();";
    assertEquals("caring about missing qualifier type", 2, findMatchesCount(s111,s112));

    // b) hierarchy navigation support
    // c) or search support
    // d) contains support

    // e) xml search (down-up, nested query), navigation from xml representation <-> java code
    // f) impl data conversion (jdk 1.5 style) <-> other from (replace support)

    // Directions:
    // @todo different navigation on sub/supertyping relation (fixed depth), methods implementing interface,
    // g. contains, like predicates
    // i. performance
    // more context for top level classes, difference with interface, etc

    // global issues:
    // @todo matches out of context
    // @todo proper regexp support

    // @todo define strict equality of the matches
    // @todo search for field selection retrieves packages also
  }

  public void testFQNInPatternAndVariableConstraints() {
    String s1 = "import java.awt.List;\n" +
                "class A { List l; }";
    String s1_2 = "import java.util.List;\n" +
                  "class A { List l; }";
    String s2 = "class '_ { 'Type:java\\.util\\.List '_Field; }";

    assertEquals("No matches for qualified class",findMatchesCount(s1,s2,true),0);
    assertEquals("Matches for qualified class",findMatchesCount(s1_2,s2,true),1);

    String s3 = "import java.util.ArrayList;\n" +
                "class A { ArrayList l; }";
    String s4 = "class '_ { 'Type:*java\\.util\\.Collection '_Field; }";
    assertEquals("Matches for qualified class in hierarchy",findMatchesCount(s3,s4,true),1);

    String s5 = "import java.util.List;\n" +
                "class A { { List l = new List(); l.add(\"1\"); }  }";
    String s5_2 = "import java.awt.List;\n" +
                  "class A { { List l = new List(); l.add(\"1\"); } }";
    String s6 = "'a:[exprtype( java\\.util\\.List )]";
    String s6_2 = "'a:[exprtype( *java\\.util\\.Collection )]";
    String s6_3 = "java.util.List '_a = '_b?;";

    assertEquals("Matches for qualified expr type",findMatchesCount(s5,s6,true),1);
    assertEquals("No matches for qualified expr type",findMatchesCount(s5_2,s6,true),0);
    assertEquals("Matches for qualified expr type in hierarchy",findMatchesCount(s5,s6_2,true),1);

    assertEquals("Matches for qualified var type in pattern",findMatchesCount(s5,s6_3,true),1);
    assertEquals("No matches for qualified var type in pattern",findMatchesCount(s5_2,s6_3,true),0);

    String s7 = "import java.util.List;\n" +
                "class A extends List { }";
    String s7_2 = "import java.awt.List;\n" +
                  "class A extends List {}";

    String s8 = "class 'a extends java.util.List {}";

    assertEquals("Matches for qualified type in pattern",findMatchesCount(s7,s8,true),1);
    assertEquals("No matches for qualified type in pattern",findMatchesCount(s7_2,s8,true),0);

    String s9 = "String.intern(\"1\");\n" +
                "java.util.Collections.sort(null);" +
                "java.util.Collections.sort(null);";
    String s10 = "java.lang.String.'_method ( '_params* )";
    assertEquals("FQN in class name",1,findMatchesCount(s9,s10,false));
  }

  public void testAnnotations() throws Exception {
    String s1 = "@MyBean(\"\")\n" +
                "@MyBean2(\"\")\n" +
                "public class TestBean {}\n" +
                "@MyBean2(\"\")\n" +
                "@MyBean(\"\")\n" +
                "public class TestBean2 {}\n" +
                "public class TestBean3 {}\n";
    String s2 = "@MyBean(\"\")\n" +
                "@MyBean2(\"\")\n" +
                "public class $a$ {}\n";

    assertEquals("Simple find annotated class",2,findMatchesCount(s1,s2,false));

    String s3 = "@VisualBean(\"????????? ?????????? ? ??\")\n" +
                "public class TestBean\n" +
                "{\n" +
                "    @VisualBeanField(\n" +
                "            name = \"??? ????????????\",\n" +
                "            initialValue = \"?????????????\"\n" +
                "            )\n" +
                "    public String user;\n" +
                "\n" +
                "    @VisualBeanField(\n" +
                "            name = \"??????\",\n" +
                "            initialValue = \"\",\n" +
                "            fieldType = FieldTypeEnum.PASSWORD_FIELD\n" +
                "            )\n" +
                "    public String password;\n" +
                "\n" +
                "    @VisualBeanField(\n" +
                "            initialValue = \"User\",\n" +
                "            name = \"????? ???????\",\n" +
                "            name = \"Second name\",\n" +
                "            fieldType = FieldTypeEnum.COMBOBOX_FIELD,\n" +
                "            comboValues = {\n" +
                "               @ComboFieldValue(\"Administrator\"),\n" +
                "               @ComboFieldValue(\"User\"),\n" +
                "               @ComboFieldValue(\"Guest\")}\n" +
                "            )    \n" +
                "    public String accessRights;\n" +
                "    \n" +
                "    public String otherField;\n" +
                "}";
    String s4 = "class '_a {\n" +
                "  @'_Annotation+ ( 'AnnotationMember*:name = '_AnnotationValue* )\n" +
                "  String '_field* ;\n" +
                "}";
    String s4_2 = "class '_a {\n" +
                  "  @'_Annotation+ ()\n" +
                  "  String 'field* ;\n" +
                  "}";

    assertEquals("Find annotation members of annotated field class",4,findMatchesCount(s3,s4,false));
    assertEquals("Find annotation fields",3,findMatchesCount(s3,s4_2,false));

    String s5 = "class A {" +
                "  @NotNull private static Collection<PsiElement> resolveElements(final PsiReference reference, final Project project) {}\n" +
                "  @NotNull private static Collection resolveElements2(final PsiReference reference, final Project project) {}\n" +
                "}";
    String s6 = "class '_c {@NotNull '_rt 'method* ('_pt* '_p*){ '_inst*; } }";
    String s6_2 = "class '_c {@'_:NotNull '_rt 'method* ('_pt* '_p*){ '_inst*; } }";

    assertEquals("Find annotated methods",2,findMatchesCount(s5,s6));
    assertEquals("Find annotated methods, 2",2,findMatchesCount(s5,s6_2));

    String s7 = "class A { void message(@NonNls String msg); }\n" +
                "class B { void message2(String msg); }\n" +
                "class C { void message2(String msg); }";
    String s8 = "class '_A { void 'b( @'_Ann{0,0}:NonNls String  '_); }";
    assertEquals("Find not annotated methods",2,findMatchesCount(s7,s8));

    String s9 = "class A {\n" +
                "  Object[] method1() {}\n" +
                "  Object method1_2() {}\n" +
                "  Object method1_3() {}\n" +
                "  Object method1_4() {}\n" +
                "  @MyAnnotation Object[] method2(int a) {}\n" +
                "  @NonNls Object[] method3() {}\n" +
                "}";
    String s10 = "class '_A { @'_Ann{0,0}:NonNls '_Type:Object\\[\\] 'b+( '_pt* '_p* ); }";
    String s10_2 = "class '_A { @'_Ann{0,0}:NonNls '_Type [] 'b+( '_pt* '_p* ); }";
    String s10_3 = "class '_A { @'_Ann{0,0}:NonNls '_Type:Object [] 'b+( '_pt* '_p* ); }";
    assertEquals("Find not annotated methods, 2",2,findMatchesCount(s9,s10));
    assertEquals("Find not annotated methods, 2",2,findMatchesCount(s9,s10_2));
    assertEquals("Find not annotated methods, 2",2,findMatchesCount(s9,s10_3));
  }

  public void testBoxingAndUnboxing() {
    String s1 = " class A { void b(Integer i); void b2(int i); void c(int d); void c2(Integer d); }\n" +
                "A a;\n" +
                "a.b2(1)\n;" +
                "a.b2(1)\n;" +
                "a.b(1)\n;" +
                "a.b( new Integer(0) )\n;" +
                "a.b( new Integer(0) )\n;" +
                "a.c(new Integer(2));\n" +
                "a.c(new Integer(3));\n" +
                "a.c2(new Integer(3));\n" +
                "a.c(3);";
    String s2 = "a.'b('_Params:[formal( Integer ) && exprtype( int ) ])";
    String s2_2 = "a.c('_Params:[formal( int ) && exprtype( Integer ) ])";

    assertEquals("Find boxing in method call",1,findMatchesCount(s1,s2,false));
    assertEquals("Find unboxing in method call",2,findMatchesCount(s1,s2_2,false));
  }

  public void testCommentsInDclSearch() {
    String s1 = "class A {\n" +
                "  int a; // comment\n" +
                "  char b;\n" +
                "  int c; // comment2\n" +
                "}";
    String s1_2 = "class A {\n" +
                  "  // comment\n" +
                  "  int a;\n" +
                  "  char b;\n" +
                  "  // comment2\n" +
                  "  int c;\n" +
                  "}";

    String s2 = "'_Type '_Variable = '_Value?; //'Comment";
    String s2_2 = "//'Comment\n" +
                  "'_Type '_Variable = '_Value?;";

    assertEquals("Find field by dcl with comment",2,findMatchesCount(s1,s2));
    assertEquals("Find field by dcl with comment 2",2,findMatchesCount(s1_2,s2_2));
  }

  public void testSearchingEmptyModifiers() {

    String s1 = "class A {\n" +
                "  int a;\n" +
                "  private char b;\n" +
                "  private char b2;\n" +
                "  public int c;\n" +
                "  public int c2;\n" +
                "}";
    String s2 = "@Modifier(\"packageLocal\") '_Type '_Variable = '_Value?;";
    String s2_2 = "@Modifier({\"packageLocal\",\"private\"}) '_Type '_Variable = '_Value?;";
    String s2_3 = "@Modifier({\"PackageLocal\",\"private\"}) '_Type '_Variable = '_Value?;";

    assertEquals("Finding package local dcls",1,findMatchesCount(s1,s2));
    assertEquals("Finding package local dcls",3,findMatchesCount(s1,s2_2));

    try {
      findMatchesCount(s1,s2_3);
      assertTrue("Finding package local dcls",false);
    } catch(MalformedPatternException ex) {

    }

    String s3 = "class A {\n" +
                "  int a;\n" +
                "  static char b;\n" +
                "  static char b2;\n" +
                "}";
    String s4 = "@Modifier(\"Instance\") '_Type '_Variable = '_Value?;";
    String s4_2 = "@Modifier({\"static\",\"Instance\"}) '_Type '_Variable = '_Value?;";
    assertEquals("Finding instance fields",1,findMatchesCount(s3,s4));
    assertEquals("Finding all fields",3,findMatchesCount(s3,s4_2));
  }

  public void test() {
    String s1 = "if (LOG.isDebugEnabled()) {\n" +
                "  int a = 1;\n" +
                "  int a = 1;\n" +
                "}";
    String s2 = "if ('_Log.isDebugEnabled()) {\n" +
                "  '_ThenStatement;\n" +
                "  '_ThenStatement;\n" +
                "}";
    assertEquals("Comparing declarations",1,findMatchesCount(s1,s2));
  }

  public void testFindStaticMethodsWithinHierarchy() {
    String s1 = "class A {}\n" +
                "class B extends A { static void foo(); }\n" +
                "class B2 extends A { static void foo(int a); }\n" +
                "class B3 extends A { static void foo(int a, int b); }\n" +
                "class C { static void foo(); }\n" +
                "B.foo();\n" +
                "B2.foo(1);\n" +
                "B3.foo(2,3);\n" +
                "C.foo();";
    String s2 = "'_Instance:[regex( *A )].'_Method:[regex( foo )] ( '_Params* )";
    assertEquals("Find static methods within expr type hierarchy", 3, findMatchesCount(s1,s2));
  }
}
