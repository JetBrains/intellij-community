/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (C) 2004       Gerwin Klein <lsf@jflex.de>                    *
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

import java_cup.runtime.Symbol;

import java.io.FileReader;


/**
 * Simple test driver for the java lexer. Just runs it on some
 * input files and produces debug output. Needs symbol class from
 * parser. 
 */
public class TestLexer {

  /** some numerals to for lexer testing */
  int intDec = 37;
  long longDec = 37l;
  int intHex = 0x0001;
  long longHex = 0xFFFFl;
  int intOct = 0377;
  long longOc = 007l;
   

  public static void main(String argv[]) {

    for (int i = 0; i < argv.length; i++) {
      try {
        System.out.println("Lexing ["+argv[i]+"]");
        Scanner scanner = new Scanner(new UnicodeEscapes(new FileReader(argv[i])));
                
        Symbol s;
        do {
          s = scanner.debug_next_token();
          System.out.println("token: "+s);
        } while (s.sym != sym.EOF);
        
        System.out.println("No errors.");
      }
      catch (Exception e) {
        e.printStackTrace(System.out);
        System.exit(1);
      }
    }
  }
}
