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
 * Symbol table entry for functions.
 * 
 * Contains arity and reference to location of definition
 */ 
class STEfun extends SymtabEntry {
  int arity;
  Tdekl dekl; // location of definition
  
  public STEfun(String f, Tdekl d, int a) { 
    super(f);
    dekl=d;
    arity=a;
  }
  
  public int kind() { 
    return SymtabEntry.FUN; 
  }

  public String toString() { 
    return "function    "+name+", arity "+arity; 
  }

  public int arity() { 
    return arity; 
  }

  public Tdekl getDekl() { 
    return dekl; 
  }
}


