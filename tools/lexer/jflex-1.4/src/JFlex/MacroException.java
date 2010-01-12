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
 * This Exception is used in the macro expander to report cycles or
 * undefined macro usages.
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.2 $, $Date: 2004/11/06 23:03:32 $
 */
public class MacroException extends RuntimeException {

  /**
   * Creates a new MacroException without message
   */
  public MacroException() {
  }


  /**
   * Creates a new MacroException with the specified message
   *
   * @param message   the error description presented to the user.
   */
  public MacroException(String message) {
    super(message);
  }

}
