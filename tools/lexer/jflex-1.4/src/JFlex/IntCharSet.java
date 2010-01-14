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

import java.util.Vector;


/** 
 * CharSet implemented with intervalls
 *
 * [fixme: optimizations possible]
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.9 $, $Date: 2004/11/07 00:12:48 $
 */
public final class IntCharSet {

  private final static boolean DEBUG = false;

  /* invariant: all intervals are disjoint, ordered */
  private Vector intervalls;  
  private int pos; 

  public IntCharSet() {
    this.intervalls = new Vector();
  }

  public IntCharSet(char c) {
    this(new Interval(c,c));
  }

  public IntCharSet(Interval intervall) {
    this();
    intervalls.addElement(intervall);
  }

  public IntCharSet(Vector /* Interval */ chars) {
    int size = chars.size();

    this.intervalls = new Vector(size);
    
    for (int i = 0; i < size; i++) 
      add( (Interval) chars.elementAt(i) );    
  }

  
  

  /**
   * returns the index of the intervall that contains
   * the character c, -1 if there is no such intevall
   *
   * @prec: true
   * @post: -1 <= return < intervalls.size() && 
   *        (return > -1 --> intervalls[return].contains(c))
   * 
   * @param c  the character
   * @return the index of the enclosing interval, -1 if no such interval  
   */
  private int indexOf(char c) {
    int start = 0;
    int end   = intervalls.size()-1;

    while (start <= end) {
      int check = (start+end) / 2;
      Interval i = (Interval) intervalls.elementAt(check);
      
      if (start == end) 
        return i.contains(c) ? start : -1;      

      if (c < i.start) {
        end = check-1;
        continue;
      }

      if (c > i.end) {
        start = check+1;
        continue;
      }

      return check;
    }

    return -1;
  } 

  public IntCharSet add(IntCharSet set) {
    for (int i = 0; i < set.intervalls.size(); i++) 
      add( (Interval) set.intervalls.elementAt(i) );    
    return this;
  }

  public void add(Interval intervall) {
    
    int size = intervalls.size();

    for (int i = 0; i < size; i++) {
      Interval elem = (Interval) intervalls.elementAt(i);

      if ( elem.end+1 < intervall.start ) continue;
      
      if ( elem.contains(intervall) ) return;      

      if ( elem.start > intervall.end+1 ) {
        intervalls.insertElementAt(new Interval(intervall), i);
        return;
      }
      
      if (intervall.start < elem.start)
        elem.start = intervall.start;
      
      if (intervall.end <= elem.end) 
        return;
        
      elem.end = intervall.end;
        
      i++;      
      // delete all x with x.contains( intervall.end )
      while (i < size) {
        Interval x = (Interval) intervalls.elementAt(i);
        if (x.start > elem.end+1) return;
        
        elem.end = x.end;
        intervalls.removeElementAt(i);
        size--;
      }
      return;      
    }

    intervalls.addElement(new Interval(intervall));
  }

  public void add(char c) {
    int size = intervalls.size();

    for (int i = 0; i < size; i++) {
      Interval elem = (Interval) intervalls.elementAt(i);
      if (elem.end+1 < c) continue;

      if (elem.contains(c)) return; // already there, nothing to do

      // assert(elem.end+1 >= c && (elem.start > c || elem.end < c));
      
      if (elem.start > c+1) {
        intervalls.insertElementAt(new Interval(c,c), i);
        return;                 
      }

      // assert(elem.end+1 >= c && elem.start <= c+1 && (elem.start > c || elem.end < c));
 
      if (c+1 == elem.start) {
        elem.start = c;
        return;
      }
      
      // assert(elem.end+1 == c);
      elem.end = c;

      // merge with next interval if it contains c
      if (i+1 >= size) return;
      Interval x = (Interval) intervalls.elementAt(i+1);
      if (x.start <= c+1) {
        elem.end = x.end;
        intervalls.removeElementAt(i+1);
      }
      return;
    }
    
    // end reached but nothing found -> append at end
    intervalls.addElement(new Interval(c,c));
  } 

 
  public boolean contains(char singleChar) {
    return indexOf(singleChar) >= 0;
  }


  /**
   * o instanceof Interval
   */
  public boolean equals(Object o) {
    IntCharSet set = (IntCharSet) o;
    if ( intervalls.size() != set.intervalls.size() ) return false;

    for (int i = 0; i < intervalls.size(); i++) {
      if ( !intervalls.elementAt(i).equals( set.intervalls.elementAt(i)) ) 
        return false;
    }

    return true;
  }

  private char min(char a, char b) {
    return a <= b ? a : b;
  }

  private char max(char a, char b) {
    return a >= b ? a : b;
  }

  /* intersection */
  public IntCharSet and(IntCharSet set) {
    if (DEBUG) {
      Out.dump("intersection");
      Out.dump("this  : "+this);
      Out.dump("other : "+set);
    }

    IntCharSet result = new IntCharSet();

    int i = 0;  // index in this.intervalls
    int j = 0;  // index in set.intervalls

    int size = intervalls.size();
    int setSize = set.intervalls.size();

    while (i < size && j < setSize) {
      Interval x = (Interval) this.intervalls.elementAt(i);
      Interval y = (Interval) set.intervalls.elementAt(j);

      if (x.end < y.start) {
        i++;
        continue;
      }

      if (y.end < x.start) {
        j++;
        continue;
      }

      result.intervalls.addElement(
        new Interval(
          max(x.start, y.start), 
          min(x.end, y.end)
          )
        );

      if (x.end >= y.end) j++;
      if (y.end >= x.end) i++;
    }

    if (DEBUG) {
      Out.dump("result: "+result);
    }

    return result;
  }
  
  /* complement */
  /* prec: this.contains(set), set != null */
  public void sub(IntCharSet set) {
    if (DEBUG) {
      Out.dump("complement");
      Out.dump("this  : "+this);
      Out.dump("other : "+set);
    }

    int i = 0;  // index in this.intervalls
    int j = 0;  // index in set.intervalls

    int setSize = set.intervalls.size();

    while (i < intervalls.size() && j < setSize) {
      Interval x = (Interval) this.intervalls.elementAt(i);
      Interval y = (Interval) set.intervalls.elementAt(j);

      if (DEBUG) {
        Out.dump("this      : "+this);
        Out.dump("this  ["+i+"] : "+x);
        Out.dump("other ["+j+"] : "+y);
      }

      if (x.end < y.start) {
        i++;
        continue;
      }

      if (y.end < x.start) {
        j++;
        continue;
      }

      // x.end >= y.start && y.end >= x.start ->
      // x.end <= y.end && x.start >= y.start (prec)
      
      if ( x.start == y.start && x.end == y.end ) {
        intervalls.removeElementAt(i);
        j++;
        continue;
      }

      // x.end <= y.end && x.start >= y.start &&
      // (x.end < y.end || x.start > y.start) ->
      // x.start < x.end 

      if ( x.start == y.start ) {
        x.start = (char) (y.end+1);
        j++;
        continue;
      }

      if ( x.end == y.end ) {
        x.end = (char) (y.start-1);
        i++;
        j++;
        continue;
      }

      intervalls.insertElementAt(new Interval(x.start, (char) (y.start-1)), i);
      x.start = (char) (y.end+1);

      i++;
      j++;
    }   

    if (DEBUG) {
      Out.dump("result: "+this);
    }
  }

  public boolean containsElements() {
    return intervalls.size() > 0;
  }

  public int numIntervalls() {
    return intervalls.size();
  }

  // beware: depends on caller protocol, single user only 
  public Interval getNext() {
    if (pos == intervalls.size()) pos = 0;
    return (Interval) intervalls.elementAt(pos++);
  }

  /**
   * Create a caseless version of this charset.
   * <p>
   * The caseless version contains all characters of this char set,
   * and additionally all lower/upper/title case variants of the 
   * characters in this set.
   * 
   * @return a caseless copy of this set
   */
  public IntCharSet getCaseless() {
    IntCharSet n = copy();
        
    int size = intervalls.size();
    for (int i=0; i < size; i++) {
      Interval elem = (Interval) intervalls.elementAt(i);
      for (char c = elem.start; c <= elem.end; c++) {
        n.add(Character.toLowerCase(c)); 
        n.add(Character.toUpperCase(c)); 
        n.add(Character.toTitleCase(c)); 
      }
    }
    
    return n;    
  }


  /**
   * Make a string representation of this char set.
   * 
   * @return a string representing this char set.
   */
  public String toString() {
    StringBuffer result = new StringBuffer("{ ");

    for (int i = 0; i < intervalls.size(); i++)
      result.append( intervalls.elementAt(i) );

    result.append(" }");

    return result.toString();
  }
  
  
  /** 
   * Return a (deep) copy of this char set
   * 
   * @return the copy
   */
  public IntCharSet copy() {
    IntCharSet result = new IntCharSet();
    int size = intervalls.size();
    for (int i=0; i < size; i++) {
      Interval iv = ((Interval) intervalls.elementAt(i)).copy();
      result.intervalls.addElement(iv);
    }
    return result;
  }
}
