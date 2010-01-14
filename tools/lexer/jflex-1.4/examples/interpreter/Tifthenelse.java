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
 * AST node for if-then-else expressions
 */ 
class Tifthenelse extends Texp implements AST {
  Tboolexp boolexp;           // condition
  Texp exp1, exp2;            // then and else branch
  
  public Tifthenelse(Tboolexp b, Texp e1, Texp e2) {
    boolexp=b;
    exp1=e1;
    exp2=e2;
  }
  
  public String toString() {
    return "if "+boolexp+" then "+exp1+" else "+exp2+" fi"; 
  }

  public void checkcontext(SymTab st) {
    boolexp.checkcontext(st);
    exp1.checkcontext(st);
    exp2.checkcontext(st);
  }

  public void prepInterp(SymTab st) {
    boolexp.prepInterp(st);
    exp1.prepInterp(st);
    exp2.prepInterp(st);
  }

  public int interpret(int[] in, int[] par) {
    boolean b = boolexp.interpret(in,par);
    if (b) 
      return exp1.interpret(in,par);
    else 
      return exp2.interpret(in,par);
  }
}

