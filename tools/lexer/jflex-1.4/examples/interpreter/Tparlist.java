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
 * AST node for a parameter list.
 */ 
class Tparlist implements AST {
  Tparlist parlist;           // rest of the liste (optional null)
  Tident ident;               // identifier

  public Tparlist(Tparlist p, Tident i) {
    parlist=p;
    ident=i;
  }

  public Tparlist(Tident i) {
    parlist=null;
    ident=i;
  }

  public String toString() {
    if (parlist!=null) 
      return parlist+","+ident;
    else 
      return ident.toString();
  }

  public void setSymtab(SymTab st, boolean isInput, int index) {
    boolean isNew = st.enter(ident.toString(),
                             new STEvar(ident.toString(), isInput, index));
    
    if (!isNew) Main.error("Variable "+ident+" defined twice!");
    if (parlist!=null) parlist.setSymtab(st, isInput, index+1);
  }
}

