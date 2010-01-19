/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.3                                                             *
 * Copyright (C) 1998-2009  Gerwin Klein <lsf@jflex.de>                    *
 * All rights reserved.                                                    *
 *                                                                         *
 * This program is free software; you can redistribute it and/or modify    *
 * it under the terms of the GNU General Public License. See the file      *
 * COPYRIGHT for more information.                                         *
 *                                                                         *
 * This program is distributed in the hope that it will be useful,         *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of          *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the           *
 * GNU General Public License for more details.                            *
 *                                                                         *
 * You should have received a copy of the GNU General Public License along *
 * with this program; if not, write to the Free Software Foundation, Inc., *
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA                 *
 *                                                                         *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package JFlex.tests;

import java.io.File;
import java.io.IOException;

import JFlex.Options;
import JFlex.anttask.JFlexTask;

import junit.framework.TestCase;

/**
 * Unit tests for the jflex ant task.
 * 
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $
 */
public class AntTaskTests extends TestCase {

  private JFlexTask task;

  /**
   * Constructor for AntTaskTests.
   * 
   * @param name  test case name
   */
  public AntTaskTests(String name) {
    super(name);
  }

  /*
   * @see TestCase#setUp()
   */
  protected void setUp() throws Exception {
    super.setUp();
    Options.setDefaults();
    task = new JFlexTask();
  }

  public void testPackageAndClass() throws IOException {
    task.setFile(new File("src/JFlex/LexScan.flex"));
    task.findPackageAndClass();
    assertEquals(task.getPackage(), "JFlex");
    assertEquals(task.getClassName(), "LexScan");
  }

  public void testPackageAndClassDefaults() throws IOException {
    task.setFile(new File("examples/simple/simple.flex"));
    task.findPackageAndClass();
    assertEquals(task.getPackage(), null);
    assertEquals(task.getClassName(), "Yylex");
  }

  public void testDestdir() throws IOException {
    task.setFile(new File("src/JFlex/LexScan.flex"));
    File dir = new File("src");
    task.setDestdir(dir);
    task.findPackageAndClass();
    task.normalizeOutdir();
    // not default jflex logic, but javac (uses package name) 
    assertEquals(Options.getDir(), new File(dir, "JFlex"));
  }

  public void testOutdir() throws IOException {
    task.setFile(new File("src/JFlex/LexScan.flex"));
    File dir = new File("src");
    task.setOutdir(dir);
    task.findPackageAndClass();
    task.normalizeOutdir();
    // this should be default jflex logic 
    assertEquals(Options.getDir(), dir);
  }

  public void testDefaultDir() throws IOException {
    task.setFile(new File("src/JFlex/LexScan.flex"));
    task.findPackageAndClass();
    task.normalizeOutdir();
    // this should be default jflex logic 
    assertEquals(Options.getDir(), new File("src/JFlex"));
  }

  public void testNomin() {
    assertTrue(!Options.no_minimize);
    task.setNomin(true);
    assertTrue(Options.no_minimize);
  }

  public void testSkipMinimization() {
    assertTrue(!Options.no_minimize);
    task.setSkipMinimization(true);
    assertTrue(Options.no_minimize);
  }

  public void testNobak() {
    assertTrue(!Options.no_backup);
    task.setNobak(true);
    assertTrue(Options.no_backup);
  }

  public void testCodeGen() {
    task.setSwitch(true);
    assertEquals(Options.gen_method, Options.SWITCH);
    task.setTable(true);
    assertEquals(Options.gen_method, Options.TABLE);
    task.setPack(true);
    assertEquals(Options.gen_method, Options.PACK);
  }

  public void testSkel() {
    task.setVerbose(false); // avoid to java console pop up
    task.setSkeleton(new File("src/skeleton.nested"));
    assertTrue(JFlex.Skeleton.line[3].indexOf("java.util.Stack") > 0);
  }
  
  public void testVerbose() {
    task.setVerbose(false);
    assertTrue(!Options.verbose);
    task.setVerbose(true);
    assertTrue(Options.verbose);
  }

  public void testTime() {
    assertTrue(!Options.time);
    task.setTimeStatistics(true);
    assertTrue(Options.time);   
    task.setTime(false);
    assertTrue(!Options.time);    
  }
  
  public void testDot() {
    assertTrue(!Options.dot);
    task.setDot(true);
    assertTrue(Options.dot);
    task.setGenerateDot(false);
    assertTrue(!Options.dot);
  }
  
  public void testDump() {
    assertTrue(!Options.dump);
    task.setDump(true);
    assertTrue(Options.dump);
  }
  
  public void testJlex() {
    assertTrue(!Options.jlex);
    task.setJLex(true);
    assertTrue(Options.jlex);
  }
}
