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
 * Stores a regular expression from the rules section of a JFlex specification.
 *
 * This class provides storage for one Object of content.
 * It is used for all regular expressions that are constructed from one object.
 * 
 * For instance:  a*  is new RegExp1(sym.STAR, new Character ('a'));
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.2 $, $Date: 2004/11/06 23:03:32 $
 */
public class RegExp1 extends RegExp {

  /**
   * The child of this expression node in the syntax tree of a regular expression.
   */
  Object content;

  
  /**
   * Constructs a new regular expression with one child object.
   *
   * @param type   a value from the cup generated class sym, defining the 
   *               kind of this regular expression
   *
   * @param content  the child of this expression
   */
  public RegExp1(int type, Object content) {
    super(type);
    this.content = content;
  }
  

  /**
   * Returns a String-representation of this regular expression
   * with the specified indentation.
   *
   * @param tab   a String that should contain only space characters and
   *              that is inserted in front of standard String-representation
   *              pf this object.
   */
  public String print(String tab) {
    if (content instanceof RegExp) {
      return tab+"type = "+type+Out.NL+tab+"content :"+Out.NL+((RegExp)content).print(tab+"  ");
    }
    else
      return tab+"type = "+type+Out.NL+tab+"content :"+Out.NL+tab+"  "+content;
  }

  
  /**
   * Returns a String-representation of this regular expression
   */
  public String toString() {
    return print("");
  }
}
