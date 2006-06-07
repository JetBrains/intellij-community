package com.intellij.structuralsearch;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 4, 2004
 * Time: 9:18:10 PM
 * To change this template use File | Settings | File Templates.
 */
@SuppressWarnings({"ALL"})
public class StructuralReplaceTest extends StructuralReplaceTestCase {
  public void testReplaceInLiterals() {
    String s1 = "String ID_SPEED = \"Speed\";";
    String s2 = "String 'name = \"'string\";";
    String s2_2 = "String 'name = \"'string:[regex( .* )]\";";
    String s3 = "VSegAttribute $name$ = new VSegAttribute(\"$string$\");";
    String expectedResult = "VSegAttribute ID_SPEED = new VSegAttribute(\"Speed\");";

    String actualResult = replacer.testReplace(s1,s2,s3,options);
    assertEquals(
      "Matching/replacing literals",
      expectedResult,
      actualResult
    );

    actualResult = replacer.testReplace(s1,s2_2,s3,options);
    assertEquals(
      "Matching/replacing literals",
      expectedResult,
      actualResult
    );

    String s4 = "params.put(\"BACKGROUND\", \"#7B528D\");";
    String s5 = "params.put(\"$FieldName$\", \"#$exp$\");";
    String s6 = "String $FieldName$ = \"$FieldName$\";\n" +
                "params.put($FieldName$, \"$exp$\");";
    String expectedResult2 = "String BACKGROUND = \"BACKGROUND\";\n" +
                             "params.put(BACKGROUND, \"7B528D\");";

    actualResult = replacer.testReplace(s4,s5,s6,options);

    assertEquals(
      "string literal replacement 2",
      expectedResult2,
      actualResult
    );

    String s7 = "IconLoader.getIcon(\"/ant/property.png\");\n" +
                "IconLoader.getIcon(\"/ant/another/property.png\");\n";
    String s8 = "IconLoader.getIcon(\"/'module/'name:[regex( \\w+ )].png\");";
    String s9 = "Icons.$module$.$name$;";
    String expectedResult3 = "Icons.ant.property;\n" +
                             "IconLoader.getIcon(\"/ant/another/property.png\");\n";

    actualResult = replacer.testReplace(s7,s8,s9,options);

    assertEquals(
      "string literal replacement 3",
      expectedResult3,
      actualResult
    );

    String s10 = "configureByFile(path + \"1.html\");\n" +
                 "    checkResultByFile(path + \"1_after.html\");\n" +
                 "    checkResultByFile(path + \"1_after2.html\");\n" +
                 "    checkResultByFile(path + \"1_after3.html\");";
    String s11 = "\"'a.html\"";
    String s12 = "\"$a$.\"+ext";
    String expectedResult4 = "configureByFile(path + (\"1.\"+ext));\n" +
                             "    checkResultByFile(path + (\"1_after.\"+ext));\n" +
                             "    checkResultByFile(path + (\"1_after2.\"+ext));\n" +
                             "    checkResultByFile(path + (\"1_after3.\"+ext));";

    actualResult = replacer.testReplace(s10,s11,s12,options);
    assertEquals(
      "string literal replacement 4",
      expectedResult4,
      actualResult
    );
  }

  public void testReplace2() {
    String s1 = "package com.www.xxx.yyy;\n" +
                "\n" +
                "import javax.swing.*;\n" +
                "\n" +
                "public class Test {\n" +
                "  public static void main(String[] args) {\n" +
                "    if (1==1)\n" +
                "      JOptionPane.showMessageDialog(null, \"MESSAGE\");\n" +
                "  }\n" +
                "}";
    String s2 = "JOptionPane.'showDialog(null, 'msg);";
    String s3 = "//FIXME provide a parent frame\n" +
                "JOptionPane.$showDialog$(null, $msg$);";

    String expectedResult = "package com.www.xxx.yyy;\n" +
                            "\n" +
                            "import javax.swing.*;\n" +
                            "\n" +
                            "public class Test {\n" +
                            "  public static void main(String[] args) {\n" +
                            "    if (1==1)\n" +
                            "      //FIXME provide a parent frame\n" +
                            "JOptionPane.showMessageDialog(null, \"MESSAGE\");\n" +
                            "  }\n" +
                            "}";

    actualResult = replacer.testReplace(s1,s2,s3,options);
    assertEquals(
      "adding comment to statement inside the if body",
      expectedResult,
      actualResult
    );

    String s4 = "myButton.setText(\"Ok\");";
    String s5 = "'Instance.'MethodCall:[regex( setText )]('Parameter*:[regex( \"Ok\" )]);";
    String s6 = "$Instance$.$MethodCall$(\"OK\");";

    String expectedResult2 = "myButton.setText(\"OK\");";

    actualResult = replacer.testReplace(s4,s5,s6,options);
    assertEquals(
      "adding comment to statement inside the if body",
      expectedResult2,
      actualResult
    );
  }

  public void testReplace() {
    String str = "// searching for several constructions\n" +
                 "    lastTest = \"several constructions match\";\n" +
                 "    matches = testMatcher.findMatches(s5,s4, options);\n" +
                 "    if (matches==null || matches.size()!=3) return false;\n" +
                 "\n" +
                 "    // searching for several constructions\n" +
                 "    lastTest = \"several constructions 2\";\n" +
                 "    matches = testMatcher.findMatches(s5,s6, options);\n" +
                 "    if (matches.size()!=0) return false;\n" +
                 "\n" +
                 "    //options.setLooseMatching(true);\n" +
                 "    // searching for several constructions\n" +
                 "    lastTest = \"several constructions 3\";\n" +
                 "    matches = testMatcher.findMatches(s7,s8, options);\n" +
                 "    if (matches.size()!=2) return false;";

    String str2="      lastTest = 'Descr;\n" +
                "      matches = testMatcher.findMatches('In,'Pattern, options);\n" +
                "      if (matches.size()!='Number) return false;";
    String str3 = "assertEquals($Descr$,testMatcher.findMatches($In$,$Pattern$, options).size(),$Number$);";
    String expectedResult1 = "// searching for several constructions\n" +
                             "    lastTest = \"several constructions match\";\n" +
                             "    matches = testMatcher.findMatches(s5, s4, options);\n" +
                             "    if (matches == null || matches.size() != 3) return false;\n" +
                             "\n" +
                             "    // searching for several constructions\n" +
                             "    assertEquals(\"several constructions 2\", testMatcher.findMatches(s5, s6, options).size(), 0);\n" +
                             "\n" +
                             "    //options.setLooseMatching(true);\n" +
                             "    // searching for several constructions\n" +
                             "    assertEquals(\"several constructions 3\", testMatcher.findMatches(s7, s8, options).size(), 2);";

    String str4 = "";

    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(str,str2,str3,options);
    options.setToReformatAccordingToStyle(false);
    assertEquals("Basic replacement with formatter",expectedResult1,actualResult);

    actualResult = replacer.testReplace(str,str2,str4,options);
    String expectedResult2 = "// searching for several constructions\n" +
                             "    lastTest = \"several constructions match\";\n" +
                             "    matches = testMatcher.findMatches(s5,s4, options);\n" +
                             "    if (matches==null || matches.size()!=3) return false;\n" +
                             "\n" +
                             "    // searching for several constructions\n" +
                             "\n" +
                             "    //options.setLooseMatching(true);\n" +
                             "    // searching for several constructions";

    assertEquals("Empty replacement",expectedResult2,actualResult);

    String str5 = "testMatcher.findMatches('In,'Pattern, options).size()";
    String str6 = "findMatchesCount($In$,$Pattern$)";
    String expectedResult3="// searching for several constructions\n" +
                           "    lastTest = \"several constructions match\";\n" +
                           "    matches = testMatcher.findMatches(s5, s4, options);\n" +
                           "    if (matches == null || matches.size() != 3) return false;\n" +
                           "\n" +
                           "    // searching for several constructions\n" +
                           "    assertEquals(\"several constructions 2\", findMatchesCount(s5,s6), 0);\n" +
                           "\n" +
                           "    //options.setLooseMatching(true);\n" +
                           "    // searching for several constructions\n" +
                           "    assertEquals(\"several constructions 3\", findMatchesCount(s7,s8), 2);";
    actualResult = replacer.testReplace(expectedResult1,str5,str6,options);

    assertEquals( "Expression replacement", expectedResult3,actualResult );

    String str7 = "try { a.doSomething(); b.doSomething(); } catch(IOException ex) {  ex.printStackTrace(); throw new RuntimeException(ex); }";
    String str8 = "try { 'Statements+; } catch('_ '_) { 'HandlerStatements+; }";
    String str9 = "$Statements$;";
    String expectedResult4 = "a.doSomething();\n" +
                             "b.doSomething();";

    actualResult = replacer.testReplace(str7,str8,str9,options);
    assertEquals( "Multi line match in replacement", expectedResult4,actualResult );

    String str10 = "    parentNode.insert(compositeNode, i);\n" +
                   "    if (asyncMode) {\n" +
                   "       myTreeModel.nodesWereInserted(parentNode,new int[] {i} );\n" +
                   "    }";
    String str11 = "    'parentNode.insert('newNode, 'i);\n" +
                   "    if (asyncMode) {\n" +
                   "       myTreeModel.nodesWereInserted('parentNode,new int[] {'i} );\n" +
                   "    }";
    String str12 = "addChild($parentNode$,$newNode$, $i$);";
    String expectedResult5 = "    addChild(parentNode,compositeNode, i);";

    actualResult = replacer.testReplace(str10,str11,str12,options);
    assertEquals( "Array initializer replacement", expectedResult5,actualResult);

    String str13 = "  aaa(5,6,3,4,1,2);";
    String str14 = "aaa('t{2,2},3,4,'q{2,2});";
    String str15 = "aaa($q$,3,4,$t$);";
    String expectedResult6 = "  aaa(1,2,3,4,5,6);";

    actualResult = replacer.testReplace(str13,str14,str15,options);
    assertEquals("Parameter multiple match",expectedResult6,actualResult);

    String str16 = "  int c = a();";
    String str17 = "'t:a ('q*,'p*)";
    String str18 = "$t$($q$,1,$p$)";
    String expectedResult7 = "  int c = a(1);";

    actualResult = replacer.testReplace(str16,str17,str18,options);
    assertEquals("Replacement of init in definition + empty substitution",expectedResult7,actualResult);

    String str19 = "  aaa(bbb);";
    String str20 = "'t('_);";
    String str21 = "$t$(ccc);";
    String expectedResult8 = "  aaa(ccc);";

    actualResult = replacer.testReplace(str19,str20,str21,options);
    assertEquals("One substition replacement",expectedResult8,actualResult);

    String str22 = "  instance.setAAA(anotherInstance.getBBB());";
    String str23 = "  'i.'m:set(.+) ('a.'m2:get(.+) ());";
    String str24 = "  $a$.set$m2_1$( $i$.get$m_1$() );";
    String expectedResult9 = "  anotherInstance.setBBB( instance.getAAA() );";

    actualResult = replacer.testReplace(str22,str23,str24,options);
    assertEquals("Reg exp substitution replacement",expectedResult9,actualResult);

    String str25 = "  LaterInvocator.invokeLater(new Runnable() {\n" +
                   "          public void run() {\n" +
                   "            LOG.info(\"refreshFilesAsync, modalityState=\" + ModalityState.current());\n" +
                   "            myHandler.getFiles().refreshFilesAsync(new Runnable() {\n" +
                   "              public void run() {\n" +
                   "                semaphore.up();\n" +
                   "              }\n" +
                   "            });\n" +
                   "          }\n" +
                   "        });";
    String str26 = "  LaterInvocator.invokeLater('Params{1,10});";
    String str27 = "  com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater($Params$);";
    String expectedResult10 = "  com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new Runnable() {\n" +
                              "          public void run() {\n" +
                              "            LOG.info(\"refreshFilesAsync, modalityState=\" + ModalityState.current());\n" +
                              "            myHandler.getFiles().refreshFilesAsync(new Runnable() {\n" +
                              "              public void run() {\n" +
                              "                semaphore.up();\n" +
                              "              }\n" +
                              "            });\n" +
                              "          }\n" +
                              "        });";

    actualResult = replacer.testReplace(str25,str26,str27,options);
    assertEquals("Anonymous in parameter",expectedResult10,actualResult);

    String str28 = "UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile,\n" +
                   "                                                          processedElement.getTextOffset(), true,\n" +
                   "                                                          !myUsageViewDescriptor.toMarkInvalidOrReadonlyUsages(), null);";
    String str29 = "new UTElementNode('param, 'directory, 'null, '0, 'true, !'descr.toMarkInvalidOrReadonlyUsages(),\n" +
                   "  'referencesWord)";
    String str30 = "new UTElementNode($param$, $directory$, $null$, $0$, $true$, true,\n" +
                   "  $referencesWord$)";

    String expectedResult11 = "UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile, processedElement.getTextOffset(), true, true,\n" +
                              "  null);";
    actualResult = replacer.testReplace(str28,str29,str30,options);
    assertEquals("Replace in def initializer",expectedResult11,actualResult);

    String s31 = "a = b; b = c; a=a; c=c;";
    String s32 = "'a = 'a;";
    String s33 = "1 = 1;";
    String expectedResult12 = "a = b; b = c; 1 = 1; 1 = 1;";

    actualResult = replacer.testReplace(s31,s32,s33,options);
    assertEquals(
      "replace silly assignments",
      expectedResult12,
      actualResult
    );

    String s34 = "ParamChecker.isTrue(1==1, \"!!!\");";
    String s35 = "ParamChecker.isTrue('expr, 'msg)";
    String s36 = "assert $expr$ : $msg$";

    String expectedResult13 = "assert 1==1 : \"!!!\";";
    actualResult = replacer.testReplace(s34,s35,s36,options);
    assertEquals(
      "replace with assert",
      expectedResult13,
      actualResult
    );

    String s37 = "try { \n" +
                 "  ParamChecker.isTrue(1==1, \"!!!\");\n  \n" +
                 "  // comment we want to leave\n  \n" +
                 "  ParamChecker.isTrue(2==2, \"!!!\");\n" +
                 "} catch(Exception ex) {}";
    String s38 = "try {\n" +
                 "  'Statement{0,100};\n" +
                 "} catch(Exception ex) {}";
    String s39 = "$Statement$;";

    String expectedResult14 = "ParamChecker.isTrue(1==1, \"!!!\");\n" +
                              "// comment we want to leave\n" +
                              "ParamChecker.isTrue(2==2, \"!!!\");";
    actualResult = replacer.testReplace(s37,s38,s39,options);
    assertEquals(
      "remove try with comments inside",
      expectedResult14,
      actualResult
    );

    String s40 = "ParamChecker.instanceOf(queryKey, GroupBySqlTypePolicy.GroupKey.class);";
    String s41 = "ParamChecker.instanceOf('obj, 'class.class);";
    String s42 = "assert $obj$ instanceof $class$ : \"$obj$ is an instance of \" + $obj$.getClass() + \"; expected \" + $class$.class;";
    String expectedResult15 = "assert queryKey instanceof GroupBySqlTypePolicy.GroupKey : \"queryKey is an instance of \" + queryKey.getClass() + \"; expected \" + GroupBySqlTypePolicy.GroupKey.class;";

    actualResult = replacer.testReplace(s40,s41,s42,options);
    assertEquals(
      "Matching/replacing .class literals",
      expectedResult15,
      actualResult
    );

    String s43 = "class Wpd {\n" +
                 "  static final String TAG_BEAN_VALUE = \"\";\n" +
                 "}\n" +
                 "XmlTag beanTag = rootTag.findSubTag(Wpd.TAG_BEAN_VALUE);";
    String s44 = "'Instance?.findSubTag( 'Parameter:[exprtype( *String ) ])";
    String s45 = "jetbrains.fabrique.util.XmlApiUtil.findSubTag($Instance$, $Parameter$)";
    String expectedResult16 = "class Wpd {\n" +
                              "  static final String TAG_BEAN_VALUE = \"\";\n" +
                              "}\n" +
                              "XmlTag beanTag = jetbrains.fabrique.util.XmlApiUtil.findSubTag(rootTag, Wpd.TAG_BEAN_VALUE);";

    actualResult = replacer.testReplace(s43,s44,s45,options);
    assertEquals(
      "Matching/replacing static fields",
      expectedResult16,
      actualResult
    );

    String s46 = "Rectangle2D rec = new Rectangle2D.Double(\n" +
                 "                drec.getX(),\n" +
                 "                drec.getY(),\n" +
                 "                drec.getWidth(),\n" +
                 "                drec.getWidth());";
    String s47 = "$Instance$.$MethodCall$()";
    String s48 = "OtherClass.round($Instance$.$MethodCall$(),5)";
    String expectedResult17 = "Rectangle2D rec = new Rectangle2D.Double(\n" +
                              "                OtherClass.round(drec.getX(),5),\n" +
                              "                OtherClass.round(drec.getY(),5),\n" +
                              "                OtherClass.round(drec.getWidth(),5),\n" +
                              "                OtherClass.round(drec.getWidth(),5));";
    actualResult = replacer.testReplace(s46,s47,s48,options);

    assertEquals(
      "Replace in constructor",
      expectedResult17,
      actualResult
    );

    String s49 = "class A {}\n" +
                 "class B extends A {}\n" +
                 "A a = new B();";
    String s50 = "A 'b = new 'B:*A ();";
    String s51 = "A $b$ = new $B$(\"$b$\");";
    String expectedResult18 = "class A {}\n" +
                              "class B extends A {}\n" +
                              "A a = new B(\"a\");";

    actualResult = replacer.testReplace(s49,s50,s51,options);

    assertEquals(
      "Class navigation",
      expectedResult18,
      actualResult
    );

    String s52 = "try {\n" +
                 "  aaa();\n" +
                 "} finally {\n" +
                 "  System.out.println();" +
                 "}\n" +
                 "try {\n" +
                 "  aaa2();\n" +
                 "} catch(Exception ex) {\n" +
                 "  aaa3();\n" +
                 "}\n" +
                 "finally {\n" +
                 "  System.out.println();\n" +
                 "}\n" +
                 "try {\n" +
                 "  aaa4();\n" +
                 "} catch(Exception ex) {\n" +
                 "  aaa5();\n" +
                 "}\n";
    String s53 = "try { 'a; } finally {\n" +
                 "  'b;" +
                 "}";
    String s54 = "$a$;";
    String expectedResult19 = "aaa();\n" +
                              "try {\n" +
                              "  aaa2();\n" +
                              "} catch(Exception ex) {\n" +
                              "  aaa3();\n" +
                              "}\n" +
                              "finally {\n" +
                              "  System.out.println();\n" +
                              "}\n" +
                              "try {\n" +
                              "  aaa4();\n" +
                              "} catch(Exception ex) {\n" +
                              "  aaa5();\n" +
                              "}\n";

    actualResult = replacer.testReplace(s52,s53,s54,options);

    assertEquals(
      "Try/ catch/ finally is replace with try/finally",
      expectedResult19,
      actualResult
    );

    String s55 = "for(Iterator<String> iterator = stringlist.iterator(); iterator.hasNext()) {\n" +
                 "      String str = iterator.next();\n" +
                 "      System.out.println( str );\n" +
                 "}";
    String s56 = "for (Iterator<$Type$> $variable$ = $container$.iterator(); $variable$.hasNext();) {\n" +
                 "    $Type$ $var$ = $variable$.next();\n" +
                 "    $Statements$;\n" +
                 "}";
    String s57 = "for($var$:$container$) {\n" +
                 "  $Statements$;\n" +
                 "}";
    String expectedResult20 = "for(str:stringlist) {\n" +
                              "  System.out.println( str );\n" +
                              "}";

    actualResult = replacer.testReplace(s55,s56,s57,options);

    assertEquals(
      "for with foreach",
      expectedResult20,
      actualResult
    );

    String s58 = "class A {\n" +
                 "  static Set<String> b_MAP = new HashSet<String>();\n" +
                 "  int c;\n" +
                 "}";
    String s59 = "'a:[ regex( (.*)_MAP ) ]";
    String s60 = "$a_1$_SET";
    String expectedResult21 = "class A {\n" +
                              "  static Set<String> b_SET = new HashSet<String>();\n" +
                              "  int c;\n" +
                              "}";

    actualResult = replacer.testReplace(s58,s59,s60,options);

    assertEquals(
      "replace symbol in definition",
      expectedResult21,
      actualResult
    );

    String s64 = "int x = 42;\n" +
                 "int y = 42; // Stuff";
    String s65 = "'Type 'Variable = 'Value; // 'Comment";
    String s66 = "/**\n" +
                 " *$Comment$\n" +
                 " */\n" +
                 "$Type$ $Variable$ = $Value$;";
    String expectedResult23 = "int x = 42;\n" +
                              "/**\n" +
                              " * Stuff\n" +
                              " */\n" +
                              "int y = 42;";

    actualResult = replacer.testReplace(s64,s65,s66,options);

    assertEquals(
      "Replacement of the comment with javadoc",
      expectedResult23,
      actualResult
    );

    String s61 = "try { 1=1; } catch(Exception e) { 1=1; } catch(Throwable t) { 2=2; }";
    String s62 = "try { 'a; } catch(Exception e) { 'b; }";
    String s63 = "try { $a$; } catch(Exception1 e) { $b$; } catch(Exception2 e) { $b$; }";
    String expectedResult22 = "try { 1=1; } catch(Exception1 e) { 1=1; } catch(Exception2 e) { 1=1; } catch (Throwable t) { 2=2; }";

    actualResult = replacer.testReplace(s61,s62,s63,options);

    assertEquals(
      "try replacement by another try will leave the unmatched catch",
      expectedResult22,
      actualResult
    );

  }

  public void testReplaceParameter() {
    String s1 = "class A { void b(int c, int d, int e) {} }";
    String s2 = "int d";
    String s3 = "int d2";
    String expectedResult = "class A { void b(int c, int d2, int e) {} }";

    actualResult = replacer.testReplace(s1,s2,s3,options);

    assertEquals(
      "replace method parameter",
      expectedResult,
      actualResult
    );
  }

  public void testReplaceWithComments() {
    String s1 = "map.put(key, value); // line 1";
    String s2 = "map.put(key, value); // line 1";
    String s3 = "map.put(key, value); // line 1";
    String expectedResult = "map.put(key, value); // line 1";

    actualResult = replacer.testReplace(s1,s2,s3,options);

    assertEquals(
      "replace self with comment after",
      expectedResult,
      actualResult
    );

    String s4 = "if (true) System.out.println(\"1111\"); else System.out.println(\"2222\");\n" +
                "while(true) System.out.println(\"1111\");";
    String s5 = "System.out.println('Test);";
    String s6 = "/* System.out.println($Test$); */";
    actualResult = replacer.testReplace(s4,s5,s6,options);
    String expectedResult2 = "if (true) /* System.out.println(\"1111\"); */; else /* System.out.println(\"2222\"); */;\n" +
                             "while(true) /* System.out.println(\"1111\"); */;";

    assertEquals(
      "replace with comment",
      expectedResult2,
      actualResult
    );
  }

  public void testSeveralStatements() {
    String s1 = "{\n" +
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n" +
                "      }\n" +
                "{\n" +
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n" +
                "      }\n" +
                "{\n" +
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n" +
                "      }";
    String s2 =
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n";
    String s3 = "        System.out.println(3);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(1);\n";
    String expectedResult1 = "    {\n" +
                             "        System.out.println(3);\n" +
                             "        System.out.println(2);\n" +
                             "        System.out.println(1);\n" +
                             "    }\n" +
                             "    {\n" +
                             "        System.out.println(3);\n" +
                             "        System.out.println(2);\n" +
                             "        System.out.println(1);\n" +
                             "    }\n" +
                             "    {\n" +
                             "        System.out.println(3);\n" +
                             "        System.out.println(2);\n" +
                             "        System.out.println(1);\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s1,s2,s3,options);
    options.setToReformatAccordingToStyle(false);
    assertEquals(
      "three statements replacement",
      expectedResult1,
      actualResult
    );

    String s4 = "ProgressManager.getInstance().startNonCancelableAction();\n" +
                "    try {\n" +
                "      read(id, READ_PARENT);\n" +
                "      return myViewport.parent;\n" +
                "    }\n" +
                "    finally {\n" +
                "      ProgressManager.getInstance().finishNonCancelableAction();\n" +
                "    }";
    String s5 = "ProgressManager.getInstance().startNonCancelableAction();\n" +
                "    try {\n" +
                "      '_statement{2,2};\n" +
                "    }\n" +
                "    finally {\n" +
                "      ProgressManager.getInstance().finishNonCancelableAction();\n" +
                "    }";
    String s6 = "$statement$;";
    String expectedResult2 = "read(id, READ_PARENT);\n" +
                             "return myViewport.parent;";
    actualResult = replacer.testReplace(s4,s5,s6,options);
    assertEquals(
      "extra ;",
      expectedResult2,
      actualResult
    );

    String s7 = "public class A {\n" +
                "    void f() {\n" +
                "        new Runnable() {\n" +
                "            public void run() {\n" +
                "                l();\n" +
                "            }\n" +
                "\n" +
                "            private void l() {\n" +
                "                int i = 9;\n" +
                "                int j = 9;\n" +
                "            }\n" +
                "        };\n" +
                "        new Runnable() {\n" +
                "            public void run() {\n" +
                "                l();\n" +
                "            }\n" +
                "\n" +
                "            private void l() {\n" +
                "                l();\n" +
                "                l();\n" +
                "            }\n" +
                "        };\n" +
                "    }\n" +
                "\n" +
                "}";
    String s8 = "new Runnable() {\n" +
                "    public void run() {\n" +
                "        'l ();\n" +
                "    }\n" +
                "    private void 'l () {\n" +
                "        'st{2,2};\n" +
                "    }\n" +
                "};";
    String s9 = "new My() {\n" +
                "    public void f() {\n" +
                "        $st$;\n" +
                "    }\n" +
                "};";

    String expectedResult3 = "public class A {\n" +
                             "    void f() {\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                int i = 9;\n" +
                             "                int j = 9;\n" +
                             "            }\n" +
                             "        };\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "        };\n" +
                             "    }\n" +
                             "\n" +
                             "}";
    boolean formatAccordingToStyle = options.isToReformatAccordingToStyle();
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s7,s8,s9,options);
    assertEquals(
      "extra ; 2",
      expectedResult3,
      actualResult
    );

    String s10 = "public class A {\n" +
                 "    void f() {\n" +
                 "        new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "\n" +
                 "        };\n" +
                 "        new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "\n" +
                 "        };\n" +
                 "new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                l2();\n" +
                 "                l2();\n" +
                 "            }\n" +
                 "\n" +
                 "        };\n" +
                 "    }\n" +
                 "\n" +
                 "    private void l() {\n" +
                 "        int i = 9;\n" +
                 "        int j = 9;\n" +
                 "    }\n" +
                 "}\n" +
                 "\n" +
                 "abstract class My {\n" +
                 "    abstract void f();\n" +
                 "}";
    String s11 = "new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                'l{2,2};\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                'l{2,2};\n" +
                 "            }\n" +
                 "\n" +
                 "        };";
    String s12 = "new My() {\n" +
                 "            public void f() {\n" +
                 "                $l$;\n" +
                 "            }\n" +
                 "        };";
    String expectedResult4 = "public class A {\n" +
                             "    void f() {\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "        };\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "        };\n" +
                             "        new Runnable() {\n" +
                             "            public void run() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "\n" +
                             "            public void run2() {\n" +
                             "                l2();\n" +
                             "                l2();\n" +
                             "            }\n" +
                             "\n" +
                             "        };\n" +
                             "    }\n" +
                             "\n" +
                             "    private void l() {\n" +
                             "        int i = 9;\n" +
                             "        int j = 9;\n" +
                             "    }\n" +
                             "}\n" +
                             "\n" +
                             "abstract class My {\n" +
                             "    abstract void f();\n" +
                             "}";

    actualResult = replacer.testReplace(s10,s11,s12,options);
    assertEquals(
      "same multiple occurences 2 times",
      expectedResult4,
      actualResult
    );

    options.setToReformatAccordingToStyle(formatAccordingToStyle);

    String s13 = "    PsiLock.LOCK.acquire();\n" +
                 "    try {\n" +
                 "      return value;\n" +
                 "    }\n" +
                 "    finally {\n" +
                 "      PsiLock.LOCK.release();\n" +
                 "    }";
    String s13_2 = "    PsiLock.LOCK.acquire();\n" +
                   "    try {\n" +
                   "      if (true) { return value; }\n" +
                   "    }\n" +
                   "    finally {\n" +
                   "      PsiLock.LOCK.release();\n" +
                   "    }";
    String s13_3 = "    PsiLock.LOCK.acquire();\n" +
                   "    try {\n" +
                   "      if (true) { return value; }\n\n" +
                   "      if (true) { return value; }\n" +
                   "    }\n" +
                   "    finally {\n" +
                   "      PsiLock.LOCK.release();\n" +
                   "    }";
    String s14 = "    PsiLock.LOCK.acquire();\n" +
                 "    try {\n" +
                 "      'T{1,1000};\n" +
                 "    }\n" +
                 "    finally {\n" +
                 "      PsiLock.LOCK.release();\n" +
                 "    }";
    String s15 = "synchronized(PsiLock.LOCK) {\n" +
                 "  $T$;\n" +
                 "}";

    String expectedResult5 = "    synchronized (PsiLock.LOCK) {\n" +
                             "        return value;\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s13,s14,s15,options);
    options.setToReformatAccordingToStyle(false);

    assertEquals(
      "extra ; over return",
      expectedResult5,
      actualResult
    );

    String expectedResult6 = "    synchronized (PsiLock.LOCK) {\n" +
                             "        if (true) {\n" +
                             "            return value;\n" +
                             "        }\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s13_2,s14,s15,options);
    options.setToReformatAccordingToStyle(false);

    assertEquals(
      "extra ; over if",
      expectedResult6,
      actualResult
    );

    String expectedResult7 = "    synchronized (PsiLock.LOCK) {\n" +
                             "        if (true) {\n" +
                             "            return value;\n" +
                             "        }\n" +
                             "\n" +
                             "        if (true) {\n" +
                             "            return value;\n" +
                             "        }\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s13_3,s14,s15,options);
    options.setToReformatAccordingToStyle(false);
    assertEquals(
      "newlines in matches of several lines",
      expectedResult7,
      actualResult
    );

    String s16 = "public class SSTest {\n" +
                 "  Object lock;\n" +
                 "  public Object getProducts (String[] productNames) {\n" +
                 "    synchronized (lock) {\n" +
                 "      Object o = new Object ();\n" +
                 "      assert o != null;\n" +
                 "      return o;\n" +
                 "    }\n" +
                 "  }\n" +
                 "}";
    String s16_2 = "public class SSTest {\n" +
                   "  Object lock;\n" +
                   "  public void getProducts (String[] productNames) {\n" +
                   "    synchronized (lock) {\n" +
                   "      boolean[] v = {true};\n" +
                   "    }\n" +
                   "  }\n" +
                   "}";

    String s17 = "synchronized(lock) {\n" +
                 "  'Statement*;\n" +
                 "}";

    String s18 = "$Statement$;";
    String expectedResult8 = "public class SSTest {\n" +
                             "  Object lock;\n" +
                             "  public Object getProducts (String[] productNames) {\n" +
                             "    Object o = new Object ();\n" +
                             "      assert o != null;\n" +
                             "      return o;\n" +
                             "  }\n" +
                             "}";
    String expectedResult8_2 = "public class SSTest {\n" +
                               "  Object lock;\n" +
                               "  public void getProducts (String[] productNames) {\n" +
                               "    boolean[] v = {true};\n" +
                               "  }\n" +
                               "}";

    actualResult = replacer.testReplace(s16,s17,s18,options);
    assertEquals(
      "extra ;",
      expectedResult8,
      actualResult
    );

    actualResult = replacer.testReplace(s16_2,s17,s18,options);
    assertEquals(
      "missed ;",
      expectedResult8_2,
      actualResult
    );
  }

  public void testClassReplacement() {
    boolean formatAccordingToStyle = options.isToReformatAccordingToStyle();
    options.setToReformatAccordingToStyle(true);

    String s1 = "class A { public void b() {} }";
    String s2 = "class 'a { 'Other* }";
    String s3 = "class $a$New { Logger LOG; $Other$ }";
    String expectedResult = "    class ANew {\n" +
                            "        Logger LOG;\n\n" +
                            "        public void b() {\n" +
                            "        }\n" +
                            "    }";
    String actualResult;
    actualResult = replacer.testReplace(s1,s2,s3,options);
    assertEquals(
      "Basic class replacement",
      expectedResult,
      actualResult
    );

    String s4 = "class A { class C {} public void b() {} int f; }";
    String s5 = "class 'a { 'Other* }";
    String s6 = "class $a$ { Logger LOG; $Other$ }";
    String expectedResult2 = "    class A {\n" +
                             "        Logger LOG;\n\n" +
                             "        class C {\n" +
                             "        }\n\n" +
                             "        public void b() {\n" +
                             "        }\n\n" +
                             "        int f;\n" +
                             "    }";

    actualResult = replacer.testReplace(s4,s5,s6,options);
    assertEquals(
      "Order of members in class replacement",
      expectedResult2,
      actualResult
    );

    String s7 = "class A extends B { int c; void b() {} { a = 1; } }";
    String s8 = "class 'A extends B { 'Other* }";
    String s9 = "class $A$ extends B2 { $Other$ }";
    String expectedResult3 = "    class A extends B2 {\n" +
                             "        int c;\n\n" +
                             "        void b() {\n" +
                             "        }\n\n" +
                             "        {\n" +
                             "            a = 1;\n" +
                             "        }\n" +
                             "    }";

    actualResult = replacer.testReplace(s7,s8,s9,options);
    assertEquals("Unsupported pattern exception",actualResult,expectedResult3);
    options.setToReformatAccordingToStyle(formatAccordingToStyle);

    String s10 = "/** @example */\n" +
                 "class A {\n" +
                 "  class C {}\n" +
                 "  public void b() {}\n" +
                 "  int f;\n" +
                 "}";
    String s11 = "class 'a { 'Other* }";
    String s12 = "public class $a$ {\n" +
                 "  $Other$\n" +
                 "}";
    String expectedResult4 = "/** @example */\n" +
                             "    public class A {\n" +
                             "        class C {\n" +
                             "        }\n\n" +
                             "        public void b() {\n" +
                             "        }\n\n" +
                             "        int f;\n" +
                             "    }";

    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s10,s11,s12,options);
    options.setToReformatAccordingToStyle(false);
    assertEquals("Make class public",expectedResult4,actualResult);

    String s13 = "class CustomThread extends Thread {\n" +
                 "public CustomThread(InputStream in, OutputStream out, boolean closeOutOnExit) {\n" +
                 "    super(CustomThreadGroup.getThreadGroup(), \"CustomThread\");\n" +
                 "    setDaemon(true);\n" +
                 "    if (in instanceof BufferedInputStream) {\n" +
                 "        bis = (BufferedInputStream)in;\n" +
                 "    } else {\n" +
                 "    bis = new BufferedInputStream(in);\n" +
                 "    }\n" +
                 "    this.out = out;\n" +
                 "    this.closeOutOnExit = closeOutOnExit;\n" +
                 "}\n" +
                 "}";
    String s14 = "class 'Class extends Thread {\n" +
                 "  'Class('ParameterType* 'ParameterName*) {\n" +
                 "\t  super (CustomThreadGroup.getThreadGroup(), 'superarg* );\n" +
                 "    'Statement*;\n" +
                 "  }\n" +
                 "}";
    String s15 = "class $Class$ extends CustomThread {\n" +
                 "  $Class$($ParameterType$ $ParameterName$) {\n" +
                 "\t  super($superarg$);\n" +
                 "    $Statement$;\n" +
                 "  }\n" +
                 "}";

    String expectedResult5 = "    class CustomThread extends CustomThread {\n" +
                             "        CustomThread(InputStream in, OutputStream out, boolean closeOutOnExit) {\n" +
                             "            super(\"CustomThread\");\n" +
                             "            setDaemon(true);\n" +
                             "            if (in instanceof BufferedInputStream) {\n" +
                             "                bis = (BufferedInputStream) in;\n" +
                             "            } else {\n" +
                             "                bis = new BufferedInputStream(in);\n" +
                             "            }\n" +
                             "            this.out = out;\n" +
                             "            this.closeOutOnExit = closeOutOnExit;\n" +
                             "        }\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s13,s14,s15,options);
    options.setToReformatAccordingToStyle(false);
    assertEquals("Constructor replacement",expectedResult5,actualResult);

    String s16 = "public class A {}\n" +
                 "final class B {}";
    String s17 = "class 'A { 'Other* }";
    String s17_2 = "class 'A { private Log log = LogFactory.createLog(); 'Other* }";
    String s18 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";
    String s18_2 = "class $A$ { $Other$ }";

    actualResult = replacer.testReplace(s16,s17,s18,options);
    String expectedResult6 = "public  class A { private Log log = LogFactory.createLog();  }\n" +
                             "final  class B { private Log log = LogFactory.createLog();  }";
    assertEquals("Modifier list for class",expectedResult6,actualResult);

    actualResult = replacer.testReplace(actualResult,s17_2,s18_2,options);
    String expectedResult7 = "public   class A {  }\n" +
                             "final   class B {  }";
    assertEquals("Removing field",expectedResult7,actualResult);

    String s19 = "public class A extends Object implements Cloneable {}\n";
    String s20 = "class 'A { 'Other* }";
    String s21 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";

    actualResult = replacer.testReplace(s19,s20,s21,options);
    String expectedResult8 = "public  class A extends Object implements Cloneable { private Log log = LogFactory.createLog();  }\n";
    assertEquals("Extends / implements list for class",expectedResult8,actualResult);

    String s22 = "public class A<T> { int Afield; }\n";
    String s23 = "class 'A { 'Other* }";
    String s24 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";

    actualResult = replacer.testReplace(s22,s23,s24,options);
    String expectedResult9 = "public  class A<T> { private Log log = LogFactory.createLog(); int Afield; }\n";
    assertEquals("Type parameters for the class",expectedResult9,actualResult);

    String s25 = "class A {\n" +
                 "  // comment before\n" +
                 "  protected short a; //  comment after\n" +
                 "}";
    String s26 = "short a;";
    String s27 = "Object a;";
    String expectedResult10 = "class A {\n" +
                              "  // comment before\n" +
                              "  protected  Object a; //  comment after\n" +
                              "}";

    actualResult = replacer.testReplace(s25,s26,s27,options);

    assertEquals(
      "Replacing dcl with saving access modifiers",
      expectedResult10,
      actualResult
    );

    String s28 = "aaa";
    String s29 = "class 'Class {\n" +
                 " 'Class('ParameterType 'ParameterName) {\n" +
                 "    'Class('ParameterName);\n" +
                 "  }\n" +
                 "}";
    String s30 = "class $Class$ {\n" +
                 "  $Class$($ParameterType$ $ParameterName$) {\n" +
                 "     this($ParameterName$);\n" +
                 "  }\n" +
                 "}";
    String expectedResult11 = "aaa";

    actualResult = replacer.testReplace(s28,s29,s30,options);

    assertEquals(
      "Complex class replacement",
      expectedResult11,
      actualResult
    );

    String s31 = "class A {\n" +
                 "  int a; // comment\n" +
                 "  char b;\n" +
                 "  int c; // comment2\n" +
                 "}";

    String s32 = "'Type 'Variable = 'Value?; //'Comment";
    String s33 = "/**$Comment$*/\n" +
                 "$Type$ $Variable$ = $Value$;";

    String expectedResult12 = "    class A {\n" +
                              "        /** comment */\n" +
                              "        int a;\n" +
                              "        char b;\n" +
                              "        /** comment2 */\n" +
                              "        int c;\n" +
                              "    }";
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s31,s32,s33,options);
    options.setToReformatAccordingToStyle(false);

    assertEquals(
      "Replacing comments with javadoc for fields",
      expectedResult12,
      actualResult
    );

    String s34 = "/**\n" +
                 " * This interface stores XXX\n" +
                 " * <p/>\n" +
                 " */\n" +
                 "public interface X {\n" +
                 "    public static final String HEADER = Headers.HEADER;\n" +
                 "\n" +
                 "}";

    String s35 = "public interface 'MessageInterface {\n" +
                 "    public static final String 'X = 'VALUE;\n" +
                 "    'blah*" +
                 "}";
    String s36 = "public interface $MessageInterface$ {\n" +
                 "    public static final String HEADER = $VALUE$;\n" +
                 "    $blah$\n" +
                 "}";

    String expectedResult13 = "/**\n" +
                              " * This interface stores XXX\n" +
                              " * <p/>\n" +
                              " */\n" +
                              "public interface X {\n" +
                              "    public static final String HEADER = Headers.HEADER;\n" +
                              "    \n" +
                              "}";

    actualResult = replacer.testReplace(s34,s35,s36,options, true);

    assertEquals(
      "Replacing interface with interface, saving comments properly",
      expectedResult13,
      actualResult
    );
  }

  public void testClassReplacement3() {
    if (true) return;
    final String actualResult;
    String s37 = "class A { int a = 1; void B() {} int C(char ch) { int z = 1; } int b = 2; }";

    String s38 = "class 'A { 'T* 'M*('PT* 'PN*) { 'S*; } 'O* }";
    String s39 = "class $A$ { $T$ $M$($PT$ $PN$) { System.out.println(\"$M$\"); $S$; } $O$ }";

    String expectedResult14 = "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";
    String expectedResult14_2 = "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";

    actualResult = replacer.testReplace(s37,s38,s39,options, true);

    assertEquals(
      "Multiple methods replacement",
      expectedResult14,
      actualResult
    );
  }

  public void testClassReplacement4() {
    final String actualResult;
    String s1 = "class A {\n" +
                "  int a = 1;\n" +
                "  int b;\n" +
                "  private int c = 2;\n" +
                "}";

    String s2 = "@Modifier(\"PackageLocal\") 'Type 'Instance = 'Init?;";
    String s3 = "public $Type$ $Instance$ = $Init$;";

    String expectedResult = "class A {\n" +
                            "  public int a = 1;\n" +
                            "  public int b  ;\n" +
                            "  private int c = 2;\n" +
                            "}";

    actualResult = replacer.testReplace(s1,s2,s3,options, true);

    assertEquals(
      "Multiple fields replacement",
      expectedResult,
      actualResult
    );
  }
  
  public void testClassReplacement5() {
    final String actualResult;
    String s1 = "public class X {\n" +
                "    /**\n" +
                "     * zzz\n" +
                "     */\n" +
                "    void f() {\n" +
                "\n" +
                "    }\n" +
                "}";

    String s2 = "class 'c {\n" +
                "    /**\n" +
                "     * zzz\n" +
                "     */\n" +
                "    void f(){}\n" +
                "}";
    String s3 = "class $c$ {\n" +
                "    /**\n" +
                "     * ppp\n" +
                "     */\n" +
                "    void f(){}\n" +
                "}";

    String expectedResult = "public  class X {\n" +
                            "    /**\n" +
                            "     * ppp\n" +
                            "     */\n" +
                            "    void f(){}\n" +
                            "}";

    actualResult = replacer.testReplace(s1,s2,s3,options, true);

    assertEquals(
      "Not preserving comment if it is present",
      expectedResult,
      actualResult
    );
  }

  public void testClassReplacement6() {
    String actualResult;
    String s1 = "public class X {\n" +
                "   /**\n" +
                "    * zzz\n" +
                "    */\n" +
                "   private void f(int i) {\n" +
                "       //s\n" +
                "   }\n" +
                "}";
    String s1_2 = "public class X {\n" +
                "   /**\n" +
                "    * zzz\n" +
                "    */\n" +
                "   private void f(int i) {\n" +
                "       int a = 1;\n" +
                "       //s\n" +
                "   }\n" +
                "}";

    String s2 = "class 'c {\n" +
                "   /**\n" +
                "    * zzz\n" +
                "    */\n" +
                "   void f('t 'p){'s+;}\n" +
                "}";
    String s3 = "class $c$ {\n" +
                "   /**\n" +
                "    * ppp\n" +
                "    */\n" +
                "   void f($t$ $p$){$s$;}\n" +
                "}";

    String expectedResult = "public  class X {\n" +
                            "   /**\n" +
                            "    * ppp\n" +
                            "    */\n" +
                            "   private  void f(int i){//s\n" +
                            "}\n" +
                            "}";

    actualResult = replacer.testReplace(s1,s2,s3,options);

    assertEquals(
      "Correct class replacement",
      expectedResult,
      actualResult
    );

    String expectedResult2 = "public  class X {\n" +
                            "   /**\n" +
                            "    * ppp\n" +
                            "    */\n" +
                            "   private  void f(int i){int a = 1;\n" +
                            "       //s\n" +
                            "}\n" +
                            "}";

    actualResult = replacer.testReplace(s1_2,s2,s3,options);

    assertEquals(
      "Correct class replacement, 2",
      expectedResult2,
      actualResult
    );
  }

  public void testClassReplacement7() {
    String s1 = "/**\n" +
                "* Created by IntelliJ IDEA.\n" +
                "* User: cdr\n" +
                "* Date: Nov 15, 2005\n" +
                "* Time: 4:23:29 PM\n" +
                "* To change this template use File | Settings | File Templates.\n" +
                "*/\n" +
                "public class CC {\n" +
                "   /** My Comment */ int a = 3; // aaa\n" +
                "   // bbb\n" +
                "   long c = 2;\n" +
                "   void f() {\n" +
                "   }\n" +
                "}";
    String s2 = "/**\n" +
                "* Created by IntelliJ IDEA.\n" +
                "* User: 'USER\n" +
                "* Date: 'DATE\n" +
                "* Time: 'TIME\n" +
                "* To change this template use File | Settings | File Templates.\n" +
                "*/\n" +
                "class 'c {\n" +
                "  'other*\n" +
                "}";
    String s3 = "/**\n" +
                "* by: $USER$\n" +
                "*/\n" +
                "class $c$ {\n" +
                "  $other$\n" +
                "}";
    String expectedResult = "/**\n" +
                            "* by: cdr\n" +
                            "*/\n" +
                            "public  class CC {\n" +
                            "  /** My Comment */ int a = 3; // aaa\n" +
                            "// bbb\n" +
                            "   long c = 2;\n" +
                            "void f() {\n" +
                            "   }\n" +
                            "}";

    actualResult = replacer.testReplace(s1,s2,s3,options,true);

    assertEquals(
      "Class with comment replacement",
      expectedResult,
      actualResult
    );
  }

  public void testClassReplacement8() {
    String s1 = "public class CC {\n" +
                "   /** AAA*/ int b = 1; // comment\n" +
                "}";
    String s2 = "int b = 1;";
    String s3 = "long c = 2;";
    String expectedResult = "public class CC {\n" +
                            "   /** AAA*/ long c = 2; // comment\n" +
                            "}";

    actualResult = replacer.testReplace(s1,s2,s3,options,true);

    assertEquals(
      "Class field replacement with simple pattern",
      expectedResult,
      actualResult
    );
  }

  public void testClassReplacement2() {
    final String actualResult;
    String s40 = "class A {\n" +
                 "  /* special comment*/\n" +
                 "  private List<String> a = new ArrayList();\n" +
                 "  static {\n" +
                 "    int a = 1;" +
                 "  }\n" +
                 "}";
    String s41 = "class 'Class {\n" +
                 "  'Stuff2*\n" +
                 "  'FieldType 'FieldName = 'Init?;\n" +
                 "  static {\n" +
                 "    'Stmt*;\n" +
                 "  }\n" +
                 "  'Stuff*\n" +
                 "}";
    String s42 = "class $Class$ {\n" +
                 "  $Stuff2$\n" +
                 "  $FieldType$ $FieldName$ = build$FieldName$Map();\n" +
                 "  private static $FieldType$ build$FieldName$Map() {\n" +
                 "    $FieldType$ $FieldName$ = $Init$;\n" +
                 "    $Stmt$;\n" +
                 "    return $FieldName$;\n" +
                 "  }\n" +
                 "  $Stuff$\n" +
                 "}";
    String expectedResult15 = "class A {\n" +
                              "  \n" +
                              "  /* special comment*/\n" +
                              "  private  List<String> a = buildaMap();\n" +
                              "  private static List<String> buildaMap() {\n" +
                              "    List<String> a = new ArrayList();\n" +
                              "    int a = 1;\n" +
                              "    return a;\n" +
                              "  }\n" +
                              "  \n" +
                              "}";

    actualResult = replacer.testReplace(s40,s41,s42,options, true);

    assertEquals(
      "Preserving var modifiers and generic information in type during replacement",
      expectedResult15,
      actualResult
    );
  }

  public void testReplaceExceptions() {
    String s1 = "a=a;";
    String s2 = "'a";
    String s3 = "$b$";

    try {
      replacer.testReplace(s1,s2,s3,options);
      assertTrue("Undefined replace variable is not checked",false);
    } catch(UnsupportedPatternException ex) {

    }

    String s4 = "a=a;";
    String s5 = "a=a;";
    String s6 = "a=a";

    try {
      replacer.testReplace(s4,s5,s6,options);
      assertTrue("Undefined no ; in replace",false);
    } catch(UnsupportedPatternException ex) {
    }

    try {
      replacer.testReplace(s4,s6,s5,options);
      assertTrue("Undefined no ; in search",false);
    } catch(UnsupportedPatternException ex) {
    }
  }

  public void testActualParameterReplacementInConstructorInvokation() {
    String s1 = "filterActions[0] = new Action(TEXT,\n" +
                "    LifeUtil.getIcon(\"search\")) {\n" +
                "        void test() {\n" +
                "            int a = 1;\n" +
                "        }\n" +
                "};";
    String s2 = "LifeUtil.getIcon(\"search\")";
    String s3 = "StdIcons.SEARCH_LIFE";
    String expectedResult = "filterActions[0] = new Action(TEXT,\n" +
                "        StdIcons.SEARCH_LIFE) {\n" +
                "        void test() {\n" +
                "            int a = 1;\n" +
                "        }\n" +
                "};";
    options.setToReformatAccordingToStyle(true);
    options.setToShortenFQN(true);

    String actualResult = replacer.testReplace(s1, s2, s3, options);
    assertEquals("Replace in anonymous class parameter", expectedResult, actualResult);
    options.setToShortenFQN(false);
    options.setToReformatAccordingToStyle(false);
  }

  public void testRemove() {
    String s1 = "class A {\n" +
                "  /* */\n" +
                "  void a() {\n" +
                "  }\n" +
                "  /*\n" +
                "  */\n" +
                "  int b = 1;\n" +
                "  /*\n" +
                "   *\n" +
                "   */\n" +
                "   class C {}\n" +
                "  {\n" +
                "    /* aaa */\n" +
                "    int a;\n" +
                "    /* */\n" +
                "    a = 1;\n" +
                "  }\n" +
                "}";
    String s2 = "/* 'a:[regex( .* )] */";
    String s2_2 = "/* */";
    String s3 = "";
    String expectedResult = "class A {\n" +
                            "    void a() {\n" +
                            "    }\n" +
                            "\n" +
                            "    int b = 1;\n" +
                            "\n" +
                            "    class C {\n" +
                            "    }\n" +
                            "\n" +
                            "    {\n" +
                            "        int a;\n" +
                            "        a = 1;\n" +
                            "    }\n" +
                            "}";
    options.setToReformatAccordingToStyle(true);
    actualResult = replacer.testReplace(s1,s2,s3,options);
    options.setToReformatAccordingToStyle(false);

    assertEquals(
      "Removing comments",
      expectedResult,
      actualResult
    );

    String expectedResult2 = "class A {\n" +
                             "  void a() {\n" +
                             "  }\n" +
                             "  /*\n" +
                             "  */\n" +
                             "  int b = 1;\n" +
                             "  /*\n" +
                             "   *\n" +
                             "   */\n" +
                             "   class C {}\n" +
                             "  {\n" +
                             "    /* aaa */\n" +
                             "    int a;\n" +
                             "    a = 1;\n" +
                             "  }\n" +
                             "}";

    actualResult = replacer.testReplace(s1,s2_2,s3,options);

    assertEquals(
      "Removing comments",
      expectedResult2,
      actualResult
    );
  }
}
