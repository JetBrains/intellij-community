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
 * AST node for function declarations.
 * 
 * Also contains a reference to the symbol table of 
 * the paramaters and their arity.
 */ 
class Tdekl implements AST {
  Tident ident;               // identifier
  Tparlist parlist;           // liste of parameter
  Texp exp;                   // function body

  public Tdekl(Tident i, Tparlist p, Texp e) {
    parlist=p;
    ident=i;
    exp=e;
  }

  public String toString() {
    return(ident+"("+parlist+") = \n  "+exp); 
  }

  SymTab params;              // symbol table of the parameters 
  int arity;                  

  public void setSymtab(SymTab st) {
    params = new SymTab(st);
    parlist.setSymtab(params,false,0);
    arity = params.size();
    
    boolean isNew = st.enter(ident.toString(),
                             new STEfun(ident.toString(),this,arity));
    // CoCo (Fun)
    if(!isNew) Main.error("funktion "+ident+" defined twice!");
  }

  public void printSymtabs() {
    System.out.print("funktion "+ident.toString()+"\n"+params); 
  }

  public void checkcontext() {        
    exp.checkcontext(params);         // CoCo (DefFun,DefVar,Arity)
  }
    
  public void prepInterp(SymTab st) {   // set pointers and indices
    exp.prepInterp(params);
  }

  public int interpret(int[] in, int[] par) {    
    return(exp.interpret(in,par)); 
  }

  public int arity() { return(arity); }
}

