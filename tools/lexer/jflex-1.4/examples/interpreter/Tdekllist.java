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
 * AST node for declaration lists of functions
 */ 
class Tdekllist implements AST {
  Tdekllist dekllist;         // rest list (optional null)
  Tdekl dekl;                 // declaration

  public Tdekllist(Tdekllist p, Tdekl e) {
    dekllist=p;
    dekl=e;
  }

  public Tdekllist(Tdekl e) {
    dekllist=null;
    dekl=e;
  }

  public String toString() {
    if (dekllist!=null) 
      return(dekllist+",\n"+dekl);
    else 
      return(dekl.toString());
  }

  public void setSymtab(SymTab st) {
    if (dekllist!=null) 
      dekllist.setSymtab(st);
    dekl.setSymtab(st);
  }

  public void printSymtabs() {
    if (dekllist!=null) 
      dekllist.printSymtabs();
    dekl.printSymtabs();
  }
  
  public void checkcontext() {
    if (dekllist!=null) 
      dekllist.checkcontext();    
    dekl.checkcontext();              // CoCo (DefFun,DefVar,Arity)
  }                                   // in function body

  public void prepInterp(SymTab st) {  // set pointers and indices
    dekl.prepInterp(st);
    if (dekllist!=null) dekllist.prepInterp(st);
  }
}

