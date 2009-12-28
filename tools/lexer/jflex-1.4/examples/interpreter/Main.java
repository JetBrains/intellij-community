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


import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Main program of the interpreter for the AS programming language.
 * Based on JFlex/CUP.
 * 
 * Steps:
 * - scanning                               (Yylex)
 * - context free parsing and AST building  (yyparse)
 * - build up symbol table                  (setSymtabs)
 * - check context conditions               (checkcontext)
 * - prepare interpretation                 (prepInterp)
 * - start interpretation                   (interpret)
 */ 
public class Main {

  public static void main(String [] args) throws Exception {
    Reader reader = null;
    
    if (args.length == 1) {
      File input = new File(args[0]);
      if (!input.canRead()) {
        System.out.println("Error: could not read ["+input+"]");
      }
      reader = new FileReader(input);
    }
    else {  
     reader = new InputStreamReader(System.in);
    }

    Yylex scanner = new Yylex(reader);   // create scanner
    SymTab symtab = new SymTab();        // set global symbol table    
    scanner.setSymtab(symtab);

    parser parser = new parser(scanner); // create parser
    Tprogram syntaxbaum = null;

    try { 
      syntaxbaum = (Tprogram) parser.parse().value;  // parse
    }    
    catch (Exception e) { 
      e.printStackTrace(); 
    }

    // System.out.println(symtab);
    System.out.println(syntaxbaum);

    syntaxbaum.setSymtabs();          // set symbol table
    // syntaxbaum.printSymtabs();       // print symbol table

    syntaxbaum.checkcontext();        // CoCo (DefVar, DefFun, Arity)
    if(contexterror>0) return;

    syntaxbaum.prepInterp();          // var. indices and function pointers
    // im Syntaxbaum setzen
    syntaxbaum.interpret();           // interpretation
  }

  static int contexterror = 0;        // number of errors in context conditions

  public static void error(String s) { 
    System.out.println((contexterror++)+". "+s); 
  }
}
