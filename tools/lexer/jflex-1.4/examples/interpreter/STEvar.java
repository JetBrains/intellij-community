/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (C) 2001       Gerwin Klein <lsf@jflex.de>                    *
 * Copyright (C) 2001       Bernhard Rumpe <rumpe@in.tum.de>               *
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


/**
 * Symbol table entry for variables.
 * 
 * Contains index in the parameter list and a flag if it
 * is an input variable.
 */ 
class STEvar extends SymtabEntry {
  boolean is_input;           
  int index;                  

  public STEvar(String v, boolean ii, int ind) {
    super(v);
    is_input=ii;
    index=ind;
  }

  public int kind() {
    return SymtabEntry.VAR; 
  }

  public String toString() {
    if (is_input) 
      return "input var "+name+"  ("+index+")";
    else 
      return "parameter "+name+"  ("+index+")";
  }

  public int getIndex() {
    return index; 
  }

  public boolean isInput() {
    return is_input; 
  }
}

