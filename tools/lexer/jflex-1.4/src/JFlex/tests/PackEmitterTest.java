/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * jflex                                                         *
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

import JFlex.Out;
import JFlex.PackEmitter;
import junit.framework.TestCase;

/**
 * PackEmitterTest
 * 
 * @author Gerwin Klein
 * @version $Revision: 1.7 $, $Date: 2004/04/12 10:07:48 $
 */
public class PackEmitterTest extends TestCase {

  private PackEmitter p;


  /**
   * Constructor for PackEmitterTest.
   */
  public PackEmitterTest() {
    super("PackEmitter test");
  }

  public void setUp() {
    p = new PackEmitter("Bla") {
          public void emitUnpack() { }
    };
  }

  public void testInit() {
    p.emitInit();
    assertEquals(
      "  private static final int [] ZZ_BLA = zzUnpackBla();" + Out.NL +
      Out.NL +
      "  private static final String ZZ_BLA_PACKED_0 =" + Out.NL +
      "    \"", 
      p.toString());
  }

  public void testEmitUCplain() {    
    p.emitUC(8);
    p.emitUC(0xFF00);
    
    assertEquals("\\10\\uff00", p.toString());
  }
  
  public void testLineBreak() {
    for (int i = 0; i < 36; i++) {
      p.breaks();
      p.emitUC(i);
    }
    System.out.println(p);
    assertEquals(
            "\\0\\1\\2\\3\\4\\5\\6\\7\\10\\11\\12\\13\\14\\15\\16\\17\"+"+Out.NL+
      "    \"\\20\\21\\22\\23\\24\\25\\26\\27\\30\\31\\32\\33\\34\\35\\36\\37\"+"+Out.NL+
      "    \"\\40\\41\\42\\43",
      p.toString());
  }
}
