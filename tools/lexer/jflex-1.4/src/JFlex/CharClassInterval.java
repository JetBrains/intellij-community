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

package JFlex;

/**
 * Stores an interval of characters together with the character class
 *
 * A character belongs to an interval, if its Unicode value is greater than or equal
 * to the Unicode value of <CODE>start</code> and smaller than or euqal to the Unicode
 * value of <CODE>end</code>.
 *
 * All characters of the interval must belong to the same character class.
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.3 $, $Date: 2004/11/06 23:03:30 $
 */
public class CharClassInterval {

  /**
   * The first character of the interval
   */
  int start;

  /**
   * The last character of the interval
   */
  int end;

  /**
   * The code of the class all characters of this interval belong to.
   */
  int charClass;
  

  /**
   * Creates a new CharClassInterval from <CODE>start</code> to <CODE>end</code>
   * that belongs to character class <CODE>charClass</code>.
   *
   * @param start         The first character of the interval
   * @param end           The last character of the interval  
   * @param charClass     The code of the class all characters of this interval belong to.
   */
  public CharClassInterval(int start, int end, int charClass) {
    this.start = start;
    this.end = end;
    this.charClass = charClass;
  }

  /**
   * returns string representation of this class interval
   */
  public String toString() {
    return "["+start+"-"+end+"="+charClass+"]";
  }
}
