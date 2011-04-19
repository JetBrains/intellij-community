package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyDuplicatesTest1 extends DuplicatesTestCase {
  /*
  def x = 1;
  def int x = 1;
  int x = 1;
   */

  /*
  void f(x){}
  void f(def x){}
  void f(def int x){}
  def void f(def int x){}
  def f(def int x){2}
  def f(def int x){sout(2)}
  int[] arr=[1, 2, 3]
   */

  @Override
  protected String getBasePath() {
    return "/groovy/duplicates/";
  }

  @Override
  protected Language[] getLanguages() {
    return new Language[] {GroovyFileType.GROOVY_LANGUAGE};
  }

  public void test1() throws Exception {
    doTest("grdups1.groovy", true, true, true, 1, "_0", 1);
    doTest("grdups1.groovy", false, true, true, 1, "_1", 1);
    doTest("grdups1.groovy", false, true, false, 1, "_2", 1);
  }

  public void test2() throws Exception {
    doTest("grdups2.groovy", false, false, true, 4, "", 1);
  }

  public void test3() throws Exception {
    doTest("grdups3.groovy", true, true, true, 2, "", 8);
  }

  public void test4() throws Exception {
    doTest("grdups4.groovy", true, true, true, 1, "_0", 8);
    doTest("grdups4.groovy", true, true, false, 1, "_1", 8);
  }

  public void test5() throws Exception {
    doTest("grdups5.groovy", true, true, true, 1, "", 10);
  }
}
