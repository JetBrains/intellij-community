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
 * Simple pair of integers.
 *
 * Used in NFA to represent a partial NFA by its start and end state.
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.2 $, $Date: 2004/11/06 23:03:32 $
 */
final class IntPair {

  int start;
  int end;
  
  IntPair(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public int hashCode() {
    return end + (start << 8);
  }  
  
  public boolean equals(Object o) {
    if ( o instanceof IntPair ) {
      IntPair p = (IntPair) o;
      return start == p.start && end == p.end;
    }
    return false;
  }
  
  public String toString() {
    return "("+start+","+end+")";
  }
} 
