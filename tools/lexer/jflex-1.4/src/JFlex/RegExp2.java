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
 * Regular expression with two children (e.g. a | b)
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.3 $, $Date: 2004/11/06 23:03:30 $
 */
public class RegExp2 extends RegExp {

  RegExp r1, r2;

  public RegExp2(int type, RegExp r1, RegExp r2) {
    super(type);
    this.r1 = r1;
    this.r2 = r2;
  }
  
  public String print(String tab) {
    return tab+"type = "+type+Out.NL+tab+"child 1 :"+Out.NL+ //$NON-NLS-1$ //$NON-NLS-2$
           r1.print(tab+"  ")+Out.NL+tab+"child 2 :"+Out.NL+ //$NON-NLS-1$ //$NON-NLS-2$
           r2.print(tab+"  "); //$NON-NLS-1$
  }
  
  public String toString() {
    return print(""); //$NON-NLS-1$
  }
}
  
