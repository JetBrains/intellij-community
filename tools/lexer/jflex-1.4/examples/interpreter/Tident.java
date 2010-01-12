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
 * AST node for an identifier
 */ 
class Tident extends Texp implements AST {
  String name;                

  public Tident(String s) {
    name = s; 
  }

  public String toString() {
    return name; 
  }

  public void checkcontext(SymTab st) {         // CoCo (DefVar)
    SymtabEntry ste = st.lookup(name);

    if (ste==null)
      Main.error("variable not defined: "+name);
    else if (ste.kind() != SymtabEntry.VAR)
      Main.error("function used as variable: "+name);
  }

  int index;              // number of ident in environment
  boolean is_input;       // is it an input variable?    

  public void prepInterp(SymTab st) {  // set index for environment 
    STEvar ste = (STEvar)st.lookup(name);
    index = ste.getIndex();
    is_input = ste.isInput();
  }

  public int interpret(int[] in, int[] par) {
    if (is_input) 
      return(in[index]); 
    else 
      return(par[index]);
  }
}


