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
 * A set of NFA states (= integers). 
 *
 * Very similar to java.util.BitSet, but is faster and doesn't crash
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.4 $, $Date: 2004/11/06 23:03:32 $
 */
final public class StateSet {

  private final boolean DEBUG = false;

  public final static StateSet EMPTY = new StateSet();


  final static int BITS = 6;
  final static int MASK = (1<<BITS)-1;
  
  long bits[];
  
  
  public StateSet() {
    this(256);
  }  

  public StateSet(int size) {    
    bits = new long[size2nbits(size)];
  }

  public StateSet(int size, int state) {
    this(size);
    addState(state);
  }

  public StateSet(StateSet set) {
    bits = new long[set.bits.length];
    System.arraycopy(set.bits, 0, bits, 0, set.bits.length);
  }


  public void addState(int state) {
    if (DEBUG) {
      Out.dump("StateSet.addState("+state+") start"); //$NON-NLS-1$ //$NON-NLS-2$
      Out.dump("Set is : "+this); //$NON-NLS-1$
    }
   
    int index = state >> BITS;
    if (index >= bits.length) resize(state);
    bits[index] |= (1L << (state & MASK));
    
    if (DEBUG) {
      Out.dump("StateSet.addState("+state+") end"); //$NON-NLS-1$ //$NON-NLS-2$
      Out.dump("Set is : "+this); //$NON-NLS-1$
    }
  }


  private int size2nbits (int size) {
    return ((size >> BITS) + 1);
  }


  private void resize(int size) {
    int needed = size2nbits(size);

    // if (needed < bits.length) return;
         
    long newbits[] = new long[Math.max(bits.length*4,needed)];
    System.arraycopy(bits, 0, newbits, 0, bits.length);
    
    bits = newbits;
  }


  public void clear() {    
    int l = bits.length;
    for (int i = 0; i < l; i++) bits[i] = 0;
  }

  public boolean isElement(int state) {
    int index = state >> BITS;
    if (index >= bits.length)  return false;
    return (bits[index] & (1L << (state & MASK))) != 0;
  }

  /**
   * Returns one element of the set and removes it. 
   *
   * Precondition: the set is not empty.
   */
  public int getAndRemoveElement() {
    int i = 0;
    int o = 0;
    long m = 1;
    
    while (bits[i] == 0) i++;
    
    while ( (bits[i] & m) == 0 ) {
      m<<= 1;
      o++;
    }
    
    bits[i]&= ~m;
    
    return (i << BITS) + o;
  }

  public void remove(int state) {
    int index = state >> BITS;
    if (index >= bits.length) return;
    bits[index] &= ~(1L << (state & MASK));
  }

  /**
   * Returns the set of elements that contained are in the specified set
   * but are not contained in this set.
   */
  public StateSet complement(StateSet set) {
    
    if (set == null) return null;
    
    StateSet result = new StateSet();
    
    result.bits = new long[set.bits.length];
    
    int i;
    int m = Math.min(bits.length, set.bits.length);
    
    for (i = 0; i < m; i++) {
      result.bits[i] = ~bits[i] & set.bits[i];
    }
    
    if (bits.length < set.bits.length) 
      System.arraycopy(set.bits, m, result.bits, m, result.bits.length-m);
    
    if (DEBUG) 
      Out.dump("Complement of "+this+Out.NL+"and "+set+Out.NL+" is :"+result); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    
    return result;
  }

  public void add(StateSet set) {
    
    if (DEBUG) Out.dump("StateSet.add("+set+") start"); //$NON-NLS-1$ //$NON-NLS-2$

    if (set == null) return;

    long tbits[];
    long sbits[] = set.bits;
    int sbitsl = sbits.length;

    if (bits.length < sbitsl) {
      tbits = new long[sbitsl];
      System.arraycopy(bits, 0, tbits, 0, bits.length);
    }
    else {
      tbits = this.bits;
    }
    
    for (int i = 0; i < sbitsl; i++) {
      tbits[i] |= sbits[i];      
    }

    this.bits = tbits;
    
    if (DEBUG) {
      Out.dump("StateSet.add("+set+") end"); //$NON-NLS-1$ //$NON-NLS-2$
      Out.dump("Set is : "+this); //$NON-NLS-1$
    }
  }
  


  public boolean containsSet(StateSet set) {

    if (DEBUG)
      Out.dump("StateSet.containsSet("+set+"), this="+this); //$NON-NLS-1$ //$NON-NLS-2$

    int i;
    int min = Math.min(bits.length, set.bits.length);
    
    for (i = 0; i < min; i++) 
      if ( (bits[i] & set.bits[i]) != set.bits[i] ) return false;
    
    for (i = min; i < set.bits.length; i++)
      if ( set.bits[i] != 0 ) return false;
    
    return true;
  }



  /**
   * @throws ClassCastException if b is not a StateSet
   * @throws NullPointerException if b is null
   */
  public boolean equals(Object b) {

    int i = 0;
    int l1,l2;
    StateSet set = (StateSet) b;

    if (DEBUG) Out.dump("StateSet.equals("+set+"), this="+this); //$NON-NLS-1$ //$NON-NLS-2$

    l1 = bits.length;
    l2 = set.bits.length;

    if (l1 <= l2) {      
      while (i < l1) {
        if (bits[i] != set.bits[i]) return false;
        i++;
      }
      
      while (i < l2) 
        if (set.bits[i++] != 0) return false;
    }
    else {
      while (i < l2) {
        if (bits[i] != set.bits[i]) return false;
        i++;
      }
      
      while (i < l1) 
        if (bits[i++] != 0) return false;
    }

    return true;
  }

  public int hashCode() {
    long h = 1234;
    long [] _bits = bits;
    int  i = bits.length-1;

    // ignore zero high bits
    while (i >= 0 && _bits[i] == 0) i--;

    while (i >= 0)
      h ^= _bits[i--] * i;

    return (int)((h >> 32) ^ h);
  } 


  public StateSetEnumerator states() {
    return new StateSetEnumerator(this);
  }


  public boolean containsElements() {
    for (int i = 0; i < bits.length; i++)
      if (bits[i] != 0) return true;
      
    return false;
  }

  
  public StateSet copy() {
    StateSet set = new StateSet();
    set.bits = new long[bits.length];
    System.arraycopy(bits, 0, set.bits, 0, bits.length);
    return set;
  }


  public void copy(StateSet set) {
    
    if (DEBUG) 
      Out.dump("StateSet.copy("+set+") start"); //$NON-NLS-1$ //$NON-NLS-2$

    if (set == null) {
      for (int i = 0; i < bits.length; i++) bits[i] = 0;
      return;
    }

    if (bits.length < set.bits.length) {
      bits = new long[set.bits.length];
    }
    else {
      for (int i = set.bits.length; i < bits.length; i++) bits[i] = 0;
    }

    System.arraycopy(set.bits, 0, bits, 0, bits.length);        

    if (DEBUG) {
      Out.dump("StateSet.copy("+set+") end"); //$NON-NLS-1$ //$NON-NLS-2$
      Out.dump("Set is : "+this); //$NON-NLS-1$
    }
  }

  
  public String toString() {
    StateSetEnumerator set = states();

    StringBuffer result = new StringBuffer("{"); //$NON-NLS-1$

    if ( set.hasMoreElements() ) result.append(""+set.nextElement()); //$NON-NLS-1$

    while ( set.hasMoreElements() ) {
      int i = set.nextElement();
      result.append( ", "+i); //$NON-NLS-1$
    }

    result.append("}"); //$NON-NLS-1$

    return result.toString();
  }  
}
