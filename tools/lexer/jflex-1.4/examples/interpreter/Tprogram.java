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
 * AST node for the whole program (top node).
 * 
 * Also contains two symbol tables, one for input variables,
 * one for function names. 
 *
 * All operations like context check, symbol table build up
 * etc. start here.
 */ 
class Tprogram implements AST {

  Tparlist parlist;           // input variables
  Tdekllist dekllist;         // function declarations 
  Texplist explist;           // result expressions
  Texplist arguments;         // input values  

  public Tprogram(Tparlist p, Tdekllist d, Texplist e, Texplist a) {
    parlist=p;
    dekllist=d;
    explist=e;
    arguments=a;
  }

  public String toString() {
    return("Program:\n=============\ninput "+parlist+
           "\nfunctions\n"+dekllist+"\noutput "+explist+
           "\narguments "+arguments+"\nend");
  }

  SymTab inputs;      // table of input variables
  SymTab functions;   // table of functions

  public void setSymtabs() {          // calculate symbol table entries
    inputs = new SymTab();            // set input variables
    parlist.setSymtab(inputs, true, 0);
    functions = new SymTab(inputs);
    dekllist.setSymtab(functions);
  }

  public void printSymtabs() {
    System.out.print("Input variables-\n"+inputs);
    System.out.print("Functions-\n"+functions);
    dekllist.printSymtabs();
  }

  public void checkcontext() {
    dekllist.checkcontext();          // CoCo (DefFun,DefVar,Arity)
                                      // in function bodies
    explist.checkcontext(functions);  // CoCo (DefFun,DefVar,Arity)
                                      // in result expressions
    arguments.checkcontext(new SymTab()); // CoCo  (constants)
                                          // in arguments
    if (arguments.length()!=inputs.size())
      Main.error("Argument list and input variables list differ!");
  }

  public void prepInterp() {          // set pointers and indices
    dekllist.prepInterp(functions);
    explist.prepInterp(functions);
  }
  
  public void interpret() {    
    int[] inputEnv = new int[inputs.size()];      // set input 

    arguments.interpret(null,null,inputEnv,0);

    System.out.println("Result:\n=============");

    int[] ergebnis = new int[explist.length()];
    explist.interpret(inputEnv,null,ergebnis,0);  // calculate result

    int i;
    for (i=explist.length()-1; i > 0; i--)
      System.out.print(ergebnis[i]+",  ");
    System.out.println(ergebnis[i]);
  }
}

