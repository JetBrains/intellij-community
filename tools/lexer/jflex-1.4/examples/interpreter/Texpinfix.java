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
 * AST node for infix expressions
 */ 
class Texpinfix extends Texp implements AST {
  Texp exp1, exp2;            // left and right sub expression
  char kind;                  // kind ('+', '-', '*', '/')

  public Texpinfix(Texp e1, char k, Texp e2) {
    exp1=e1;
    kind=k;
    exp2=e2;
  }

  public String toString() {
    return("("+exp1+kind+exp2+")"); 
  }

  public void checkcontext(SymTab st) { // context conditions
    exp1.checkcontext(st);
    exp2.checkcontext(st);
  }

  public void prepInterp(SymTab st) {  // set pointers und indices
    exp1.prepInterp(st);
    exp2.prepInterp(st);
  }

  public int interpret(int[] in, int[] par) {    
    int e1 = exp1.interpret(in,par);
    int e2 = exp2.interpret(in,par);

    switch (kind) {
    case '+': return(e1+e2);
    case '-': return(e1-e2);
    case '*': return(e1*e2);
    case '/': return(e1/e2);
    }

    return -1;        // error
  }
}


