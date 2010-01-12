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
 * Encapsulates an action in the specification.
 *
 * It stores the Java code as String together with a priority (line number in the specification).
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.6 $, $Date: 2004/11/06 23:03:30 $
 */
final public class Action {


  /**
   * The Java code this Action represents
   */
  String content;

  /**
   * The priority (i.e. line number in the specification) of this Action. 
   */
  int priority;

  /**
   * True iff the action belongs to an lookahead expresstion 
   * (<code>a/b</code> or <code>r$</code>)
   */
  private boolean isLookAction;


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
    return "Action (priority "+priority+", lookahead "+isLookAction+") :"+Out.NL+content; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
  }


  /**
   * Returns <code>true</code> iff the parameter is an
   * Action with the same content as this one.
   *
   * @param a   the object to compare this Action with
   * @return    true if the action strings are equal
   */
  public boolean isEquiv(Action a) {
    return this == a || this.content.equals(a.content);
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
   * Return look ahead flag.
   * 
   * @return true if this actions belongs to a lookahead rule
   */
  public boolean isLookAction() {
    return isLookAction;
  }

  /**
   * Sets the look ahead flag for this action
   * 
   * @param b  set to true if this action belongs to a look ahead rule  
   */
  public void setLookAction(boolean b) {
    isLookAction = b;
  }
  
}
