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

package JFlex;


/**
 * Encapsulates an action in the specification.
 *
 * It stores the Java code as String together with a priority (line number in the specification).
 *
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $
 */
final public class Action {

  /** A normal action */
  public final static int NORMAL = 0;
  /** Action of a lookahead expression r1/r2 with fixed length r1 */
  public final static int FIXED_BASE = 1;
  /** Action of a lookahead expression r1/r2 with fixed length r2 */
  public final static int FIXED_LOOK = 2;
  /** Action of a lookahead expression r1/r2 with a finite choice of 
   *  fixed lengths in r2 */
  public final static int FINITE_CHOICE = 3;
  /** Action of a general lookahead expression */
  public final static int GENERAL_LOOK = 4;
  /** Action of the 2nd forward pass for lookahead */  
  public final static int FORWARD_ACTION = 5;
  /** Action of the backward pass for lookahead */  
  public final static int BACKWARD_ACTION = 6;
  
  /**
   * The Java code this Action represents
   */
  String content;

  /**
   * The priority (i.e. line number in the specification) of this Action. 
   */
  int priority;

  /**
   * Which kind of action this is.
   * (normal, <code>a/b</code> with fixed length a, fixed length b, etc)
   */
  private int kind = NORMAL;

  /** The length of the lookahead (if fixed) */ 
  private int len;
  
  /** The entry state of the corresponding forward DFA (if general lookahead) */
  private int entryState;

  /**
   * Creates a new Action object with specified content and line number.
   * 
   * @param content    java code
   * @param priority   line number
   */
  public Action(String content, int priority) {
    this.content = content.trim();
    this.priority = priority;
  }  

  /**
   * Creates a new Action object of the specified kind. Only
   * accepts FORWARD_ACTION or BACKWARD_ACTION.
   * 
   * @param kind   the kind of action
   * 
   * @see #FORWARD_ACTION
   * @see #BACKWARD_ACTION
   */
  public Action(int kind) {
    if (kind != FORWARD_ACTION && kind != BACKWARD_ACTION)
      throw new GeneratorException();
    this.content = "";
    this.priority = Integer.MAX_VALUE;
    this.kind = kind;
  }  

  /**
   * Compares the priority value of this Action with the specified action.
   *
   * @param other  the other Action to compare this Action with.
   *
   * @return this Action if it has higher priority - the specified one, if not.
   */
  public Action getHigherPriority(Action other) {
    if (other == null) return this;

    // the smaller the number the higher the priority
    if (other.priority > this.priority) 
      return this;
    else
      return other;
  }


  /**
   * Returns the String representation of this object.
   * 
   * @return string representation of the action
   */
  public String toString() {
    return "Action (priority "+priority+", lookahead "+kind+") :" +
      Out.NL+content; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
  }


  /**
   * Returns <code>true</code> iff the parameter is an
   * Action with the same content as this one.
   *
   * @param a   the object to compare this Action with
   * @return    true if the action strings are equal
   */
  public boolean isEquiv(Action a) {
    return this == a || 
           (this.content.equals(a.content) &&
           this.kind == a.kind && 
           this.len == a.len && 
           this.entryState == a.entryState);
  }


  /**
   * Calculate hash value.
   * 
   * @return a hash value for this Action
   */
  public int hashCode() {
    return content.hashCode();
  }


  /**
   * Test for equality to another object.
   * 
   * This action equals another object if the other 
   * object is an equivalent action. 
   * 
   * @param o  the other object.
   * 
   * @see Action#isEquiv(Action)
   */
  public boolean equals(Object o) {
    if (o instanceof Action) 
      return isEquiv((Action) o);
    else
      return false;    
  }
  
  /**
   * Return true iff this is action belongs to a general lookahead rule.
   * 
   * @return true if this actions belongs to a general lookahead rule.
   */
  public boolean isGenLookAction() {
    return kind == GENERAL_LOOK;
  }
  
  /**
   * Return true if code for this is action should be emitted, false
   * if it is a BACK/FORWARD lookahead action.
   * 
   * @return true if code should be emitted for this action.
   */
  public boolean isEmittable() {
    return kind != BACKWARD_ACTION && kind != FORWARD_ACTION;
  }
  
  /**
   * Return kind of lookahead.
   */
  public int lookAhead() {
    return kind;
  }

  /**
   * Sets the lookahead kind and data for this action
   * 
   * @param kind   which kind of lookahead it is
   * @param data   the length for fixed length look aheads.
   *   
   */
  public void setLookAction(int kind, int data) {
    this.kind = kind;
    this.len = data;
  }
  
  /**
   * The length of the lookahead or base if this is a fixed length
   * lookahead action. 
   */
  public int getLookLength() {
    return len;
  }
  
  /**
   * Return the corresponding entry state for the forward DFA (if this
   * is a general lookahead expression) 
   * 
   * @return the forward DFA entry state (+1 is the backward DFA) 
   */
  public int getEntryState() {
    return entryState;
  }

  /**
   * Set the corresponding entry state for the forward DFA of this action
   * (if this is a general lookahead expression) 
   * 
   * @param the entry state for the forward DFA of this action
   */
  public void setEntryState(int entryState) {
    this.entryState = entryState;
  }
 
  public Action copyChoice(int length) {
    Action a = new Action(this.content, this.priority);
    a.setLookAction(FINITE_CHOICE, length);
    return a;
  }
  
  /**
   * String representation of the lookahead kind of this action.
   * 
   * @return the string representation
   */
  public String lookString() {
    switch (kind) {
    case NORMAL: return "";
    case BACKWARD_ACTION: return "LOOK_BACK";
    case FIXED_BASE: return "FIXED_BASE";
    case FIXED_LOOK: return "FIXED_LOOK";
    case FINITE_CHOICE: return "FINITE_CHOICE";
    case FORWARD_ACTION: return "LOOK_FORWARD";
    case GENERAL_LOOK: return "LOOK_ACTION";
    default: return "unknown lookahead type";
    }
  }
}
