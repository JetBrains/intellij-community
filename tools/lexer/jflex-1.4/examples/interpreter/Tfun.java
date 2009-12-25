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
 * AST node for function application.
 * 
 * Also contains pointer to declaration location of the function.
 */ 
class Tfun extends Texp implements AST {
  Tident ident;                       // name of the function
  Texplist explist;                   // parameter list

  public Tfun(Tident i, Texplist e) {
    ident=i;
    explist=e;
  }

  public String toString() {
    return ident+"("+explist+")"; 
  }

  public void checkcontext(SymTab st) { // CoCo (DefFun,Arity)
    explist.checkcontext(st);
    SymtabEntry ste = st.lookup(ident.toString());
    if (ste==null)
      Main.error("function not defined: "+ident);
    else if (ste.kind() != SymtabEntry.FUN)
      Main.error("variable used as funktion: "+ident);
    else if (((STEfun)ste).arity() != explist.length())
      Main.error("wrong arity at function call: "+ident);
  }

  Tdekl fundekl;              // pointer to location of function declaration

  // set pointers and indices 
  public void prepInterp(SymTab st) {
    fundekl = ((STEfun)st.lookup(ident.toString())).getDekl();
    explist.prepInterp(st);
  }

  public int interpret(int[] in, int[] par) {
    int[] newparams = new int[fundekl.arity()];
    explist.interpret(in,par,newparams,0);
    return fundekl.interpret(in,newparams);
  }
}


