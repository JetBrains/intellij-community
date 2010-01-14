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
 * An intervall of characters with basic operations.
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.4 $, $Date: 2004/11/06 23:03:30 $
 */
public final class Interval {

  /* start and end of the intervall */
  public char start, end;
  

  /**
   * Constuct a new intervall from <code>start</code> to <code>end</code>.
   *
   * @param start  first character the intervall should contain
   * @param end    last  character the intervall should contain
   */
  public Interval(char start, char end) {
    this.start = start;
    this.end = end;
  }


  /**
   * Copy constructor
   */
  public Interval(Interval other) {
    this.start = other.start;
    this.end   = other.end;
  }


  /**
   * Return <code>true</code> iff <code>point</code> is contained in this intervall.
   *
   * @param point  the character to check
   */
  public boolean contains(char point) {
    return start <= point && end >= point;
  }


  /**
   * Return <code>true</code> iff this intervall completely contains the 
   * other one.
   *
   * @param other    the other intervall 
   */
  public boolean contains(Interval other) {
    return this.start <= other.start && this.end >= other.end;
  }
  

  /**
   * Return <code>true</code> if <code>o</code> is an intervall
   * with the same borders.
   *
   * @param o  the object to check equality with
   */
  public boolean equals(Object o) {
    if ( o == this ) return true;
    if ( !(o instanceof Interval) ) return false;

    Interval other = (Interval) o;
    return other.start == this.start && other.end == this.end;
  }
  

  /**
   * Set a new last character
   *
   * @param end  the new last character of this intervall
   */
  public void setEnd(char end) {
    this.end = end;
  }


  /** 
   * Set a new first character
   *
   * @param start the new first character of this intervall
   */ 
  public void setStart(char start) {
    this.start = start;
  } 
  
  
  /**
   * Check wether a character is printable.
   *
   * @param c the character to check
   */
  private static boolean isPrintable(char c) {
    // fixme: should make unicode test here
    return c > 31 && c < 127; 
  }


  /**
   * Get a String representation of this intervall.
   *
   * @return a string <code>"[start-end]"</code> or
   *         <code>"[start]"</code> (if there is only one character in
   *         the intervall) where <code>start</code> and
   *         <code>end</code> are either a number (the character code)
   *         or something of the from <code>'a'</code>.  
   */
  public String toString() {
    StringBuffer result = new StringBuffer("[");

    if ( isPrintable(start) )
      result.append("'"+start+"'");
    else
      result.append( (int) start );

    if (start != end) {
      result.append("-");

      if ( isPrintable(end) )
        result.append("'"+end+"'");
      else
        result.append( (int) end );
    }

    result.append("]");
    return result.toString();
  }


  /**
   * Make a copy of this interval.
   * 
   * @return the copy
   */
  public Interval copy() {    
    return new Interval(start,end);
  }
}
