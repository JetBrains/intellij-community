/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.1                                                             *
 * Copyright (C) 1998-2004  Gerwin Klein <lsf@jflex.de>                    *
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


import JFlex.Skeleton;
import junit.framework.TestCase;

import java.io.File;

/**
 * SkeletonTest
 * 
 * @author Gerwin Klein
 * @version $Revision: 1.4 $, $Date: 2004/11/06 23:03:30 $
 */
public class SkeletonTest extends TestCase {

  /**
   * Constructor for SkeletonTest.
   * @param arg0 test name
   */
  public SkeletonTest(String arg0) {
    super(arg0);
  }

  public void testReplace() {
    assertEquals(Skeleton.replace("bla ", "blub", "bla blub bla "), 
                 "blubblub blub");
  }

  public void testMakePrivate() {
    Skeleton.makePrivate(); 
    for (int i=0; i < Skeleton.line.length; i++) {
      assertEquals(Skeleton.line[i].indexOf("public"), -1);
    }
  }

  public void testDefault() {
    Skeleton.readSkelFile(new File("src/skeleton.nested"));
    assertTrue(JFlex.Skeleton.line[3].indexOf("java.util.Stack") > 0);
    Skeleton.readDefault();
    assertEquals(JFlex.Skeleton.line[3].indexOf("java.util.Stack"), -1);
  }
}
