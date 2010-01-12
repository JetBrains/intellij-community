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
 * AST node for an integer expression.
 * 
 * The non terminal exp is the sum of multiple variants and
 * therefore modeled as an abstract class.
 * 
 * The interpretation function <tt>interpret</tt> is called with
 * valuations of input variables <tt>in</tt> and parameters
 * <tt>par</tt>. Before interpret can be called, pointers
 * and variable indices must be set with <tt>prepInterp</tt>.
 */ 
abstract class Texp implements AST {
  // test context conditions (DefFun,DefVar,Arity)
  abstract public void checkcontext(SymTab st);
  
  // set pointers and indices for variables and functions
  abstract public void prepInterp(SymTab st);
  
  // interpretation
  abstract public int interpret(int[] in, int[] par);
}

