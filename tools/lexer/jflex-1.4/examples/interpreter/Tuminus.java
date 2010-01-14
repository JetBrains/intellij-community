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
 * AST node for unary minus expressions.
 */ 
class Tuminus extends Texp implements AST {

  Texp exp;                           // the negated expression

  public Tuminus(Texp e) {
    exp=e; 
  }

  public String toString() {
    return "-"+exp; 
  }

  public void checkcontext(SymTab st) {
    exp.checkcontext(st); 
  }

  public void prepInterp(SymTab st) {  
    exp.prepInterp(st); 
  }

  public int interpret(int[] in, int[] par) {
    return -(exp.interpret(in,par)); 
  }
}

