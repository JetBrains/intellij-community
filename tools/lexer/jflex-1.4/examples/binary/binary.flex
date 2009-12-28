/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (C) 1998-2004  Gerwin Klein <lsf@jflex.de>                    *
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


import java.io.*;

%%

%class BinaryLexer
%8bit

%int

%{
  public static void main(String [] argv) {
    for (int i = 0; i < argv.length; i++) {
      try {
        System.out.print("["+argv[i]+"] is ");
        BinaryLexer l = new BinaryLexer(new StraightStreamReader(new FileInputStream(argv[i])));
        l.yylex();
      }
      catch (Exception e) {
        e.printStackTrace(System.out);
        System.exit(1);
      }
    }
  }
%}

magic = \xCA \xFE \xBA \xBE

%%

{magic} [^]+  { System.out.println("a class file"); }

[^]+          { System.out.println("not a class file"); }
