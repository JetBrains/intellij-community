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

package JFlex;

/**
 * HiLowEmitter
 * 
 * @author Gerwin Klein
 * @version $Revision: 1.5 $, $Date: 2004/04/12 10:07:48 $
 */
public class HiLowEmitter extends PackEmitter {

  /** number of entries in expanded array */
  private int numEntries;

  /**
   * Create new emitter for values in [0, 0xFFFFFFFF] using hi/low encoding.
   * 
   * @param name   the name of the generated array
   */
  public HiLowEmitter(String name) {
    super(name);
  }

  /**
   * Emits hi/low pair unpacking code for the generated array. 
   * 
   * @see JFlex.PackEmitter#emitUnPack()
   */
  public void emitUnpack() {
    // close last string chunk:
    println("\";");
    nl();
    println("  private static int [] zzUnpack"+name+"() {");
    println("    int [] result = new int["+numEntries+"];");
    println("    int offset = 0;");

    for (int i = 0; i < chunks; i++) {
      println("    offset = zzUnpack"+name+"("+constName()+"_PACKED_"+i+", offset, result);");
    }

    println("    return result;");
    println("  }");

    nl();
    println("  private static int zzUnpack"+name+"(String packed, int offset, int [] result) {");
    println("    int i = 0;  /* index in packed string  */");
    println("    int j = offset;  /* index in unpacked array */");
    println("    int l = packed.length();");
    println("    while (i < l) {");
    println("      int high = packed.charAt(i++) << 16;");
    println("      result[j++] = high | packed.charAt(i++);");
    println("    }");
    println("    return j;");
    println("  }");
  }

  /**
   * Emit one value using two characters. 
   *
   * @param val  the value to emit
   * @prec  0 <= val <= 0xFFFFFFFF 
   */
  public void emit(int val) {
    numEntries+= 1;
    breaks();
    emitUC(val >> 16);
    emitUC(val & 0xFFFF);        
  }
}
