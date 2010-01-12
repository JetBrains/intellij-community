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

import JFlex.*;
import junit.framework.TestCase;

/**
 * Unit tests for JFlex.RegExp 
 * 
 * @author Gerwin Klein
 * @version $Revision: 1.5 $, $Date: 2004/11/06 23:03:30 $
 */
public class RegExpTests extends TestCase implements sym {
  
  /**
   * Constructor for RegExpTests.
   * 
   * @param name the test name
   */
  public RegExpTests(String name) {
    super(name);
  }

  public void testCharClass() {
    Macros m = new Macros();    
    RegExp e1 = new RegExp1(CCLASS, new Interval('a','z'));
    RegExp e2 = new RegExp1(CHAR, new Character('Z'));
    RegExp e3 = new RegExp1(CCLASS, new Interval('0','9'));
    m.insert("macro", e3);
    RegExp s = new RegExp1(STAR, e1);
    RegExp u = new RegExp1(MACROUSE, "macro");    
    RegExp b = new RegExp2(BAR, e2, u);
    assertTrue(e1.isCharClass(m));
    assertTrue(e2.isCharClass(m));
    assertTrue(b.isCharClass(m));
    assertTrue(!s.isCharClass(m));
    assertTrue(u.isCharClass(m));
  }
}
