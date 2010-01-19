/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.3                                                             *
 * Copyright (C) 1998-2009  Gerwin Klein <lsf@jflex.de>                    *
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
package JFlex;

import java.io.File;

/**
 * Performs simple semantic analysis on regular expressions.
 *
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $ 
 */
public final class SemCheck {

  // stored globally since they are used as constants in all checks
  private static Macros macros;
  
  /**
   * Performs semantic analysis for all expressions.
   *
   * Currently checks for empty expressions only.
   *
   * @param rs   the reg exps to be checked
   * @param m    the macro table (in expanded form)
   * @param f    the spec file containing the rules 
   */
  public static void check(RegExps rs, Macros m, File f) {
    macros = m;
    int num = rs.getNum();
    for (int i = 0; i < num; i++) {
      RegExp r = rs.getRegExp(i);
      RegExp l = rs.getLookAhead(i);
      Action a = rs.getAction(i);
      
      if (r != null && l != null && maybeEmtpy(r)) {
        if (a == null) 
          Out.error(ErrorMessages.EMPTY_MATCH, "");
        else 
          Out.error(f, ErrorMessages.EMPTY_MATCH, a.priority-1, -1);
      }
    }
  }


  /**
   * Checks if the expression potentially matches the empty string.
   *    
   */
  public static boolean maybeEmtpy(RegExp re) {
    RegExp2 r; 

    switch (re.type) {      

    case sym.BAR: {
      r = (RegExp2) re;
      return maybeEmtpy(r.r1) || maybeEmtpy(r.r2);
    }

    case sym.CONCAT: {
      r = (RegExp2) re;
      return maybeEmtpy(r.r1) && maybeEmtpy(r.r2);
    }

    case sym.STAR:
    case sym.QUESTION:
      return true;
      
    case sym.PLUS: {
      RegExp1 r1 = (RegExp1) re;
      return maybeEmtpy((RegExp) r1.content);
    }

    case sym.CCLASS:
    case sym.CCLASSNOT:
    case sym.CHAR:
    case sym.CHAR_I:
      return false;

    case sym.STRING: 
    case sym.STRING_I: {
      String content = (String) ((RegExp1) re).content;
      return content.length() == 0;
    }

    case sym.TILDE:
      return false;

    case sym.BANG: {
      RegExp1 r1 = (RegExp1) re;
      return !maybeEmtpy((RegExp) r1.content);
    }

    case sym.MACROUSE:      
      return maybeEmtpy(macros.getDefinition((String) ((RegExp1) re).content));
    }

    throw new Error("Unkown expression type "+re.type+" in "+re);   //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * Returns length if expression has fixed length, -1 otherwise.
   * 
   * Negation operators are treated as always variable length.   
   */
  public static int length(RegExp re) {
    RegExp2 r;

    switch (re.type) {      

    case sym.BAR: {
      r = (RegExp2) re;
      int l1 = length(r.r1);
      if (l1 < 0) return -1;
      int l2 = length(r.r2);

      if (l1 == l2) 
        return l1;
      else
        return -1;
    }

    case sym.CONCAT: {
      r = (RegExp2) re;
      int l1 = length(r.r1);
      if (l1 < 0) return -1;
      int l2 = length(r.r2);
      if (l2 < 0) return -1;
      return l1+l2;
    }

    case sym.STAR:
    case sym.PLUS:
    case sym.QUESTION:
      return -1;

    case sym.CCLASS:
    case sym.CCLASSNOT:
    case sym.CHAR:
    case sym.CHAR_I:
      return 1;

    case sym.STRING: 
    case sym.STRING_I: {
      String content = (String) ((RegExp1) re).content;
      return content.length();
    }

    case sym.TILDE:
    case sym.BANG: 
       // too hard to calculate at this level, use safe approx       
      return -1;

    case sym.MACROUSE:      
      return length(macros.getDefinition((String) ((RegExp1) re).content));
    }

    throw new Error("Unkown expression type "+re.type+" in "+re);   //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * Returns true iff the expression is a finite choice of fixed length
   * expressions. 
   * 
   * Negation operators are treated as always variable length.   
   */
  public static boolean isFiniteChoice(RegExp re) {
    RegExp2 r;

    switch (re.type) {      

    case sym.BAR: {
      r = (RegExp2) re;
      return isFiniteChoice(r.r1) && isFiniteChoice(r.r2);
    }

    case sym.CONCAT: {
      r = (RegExp2) re;
      int l1 = length(r.r1);
      if (l1 < 0) return false;
      int l2 = length(r.r2);
      return l2 >= 0;
    }

    case sym.STAR:
    case sym.PLUS:
    case sym.QUESTION:
      return false;

    case sym.CCLASS:
    case sym.CCLASSNOT:
    case sym.CHAR:
    case sym.CHAR_I:
      return true;

    case sym.STRING: 
    case sym.STRING_I: {
      return true;
    }

    case sym.TILDE:
    case sym.BANG: 
      return false;

    case sym.MACROUSE:      
      return isFiniteChoice(macros.getDefinition((String) ((RegExp1) re).content));
    }

    throw new Error("Unkown expression type "+re.type+" in "+re);   //$NON-NLS-1$ //$NON-NLS-2$
  }
}
