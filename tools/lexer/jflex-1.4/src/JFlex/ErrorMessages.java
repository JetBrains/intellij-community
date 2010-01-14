/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.1                                                             *
 * Copyright (C) 1998-2004  Gerwin Klein <lsf@jflex.de>                    *
 * All rights reserved.                                                    *
 *                                                                         *
 * This program is free software); you can redistribute it and/or modify    *
 * it under the terms of the GNU General Public License. See the file      *
 * COPYRIGHT for more information.                                         *
 *                                                                         *
 * This program is distributed in the hope that it will be useful,         *
 * but WITHOUT ANY WARRANTY); without even the implied warranty of          *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the           *
 * GNU General Public License for more details.                            *
 *                                                                         *
 * You should have received a copy of the GNU General Public License along *
 * with this program); if not, write to the Free Software Foundation, Inc., *
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA                 *
 *                                                                         *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

package JFlex;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * Central class for all kinds of JFlex messages.
 * 
 * [Is not yet used exclusively, but should]
 * 
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.8 $, $Date: 2004/11/06 23:03:30 $
 */
public class ErrorMessages {  
  private String key;

  private static final ResourceBundle RESOURCE_BUNDLE =
    ResourceBundle.getBundle("JFlex.Messages");

  private ErrorMessages(String key) {
    this.key = key;
  }

  public static String get(ErrorMessages msg) {
    try {
      return RESOURCE_BUNDLE.getString(msg.key);
    } catch (MissingResourceException e) {
      return '!' + msg.key + '!';
    }
  }
  
  public static String get(ErrorMessages msg, String data) {
    Object [] args = { data };
    return MessageFormat.format(get(msg),args);
  }
  
  public static String get(ErrorMessages msg, String data1, String data2) {
    Object [] args = { data1, data2 };
    return MessageFormat.format(get(msg),args);
  }

  public static String get(ErrorMessages msg, int data) {
    Object [] args = { new Integer(data) };
    return MessageFormat.format(get(msg),args);
  }

  // typesafe enumeration (generated, do not edit)  
  public static ErrorMessages UNTERMINATED_STR = new ErrorMessages("UNTERMINATED_STR");
  public static ErrorMessages EOF_WO_ACTION = new ErrorMessages("EOF_WO_ACTION");
  public static ErrorMessages EOF_SINGLERULE = new ErrorMessages("EOF_SINGLERULE");
  public static ErrorMessages UNKNOWN_OPTION = new ErrorMessages("UNKNOWN_OPTION");
  public static ErrorMessages UNEXPECTED_CHAR = new ErrorMessages("UNEXPECTED_CHAR");
  public static ErrorMessages UNEXPECTED_NL = new ErrorMessages("UNEXPECTED_NL");
  public static ErrorMessages LEXSTATE_UNDECL = new ErrorMessages("LEXSTATE_UNDECL");
  public static ErrorMessages STATE_IDENT_EXP = new ErrorMessages("STATE_IDENT_EXP");
  public static ErrorMessages REPEAT_ZERO = new ErrorMessages("REPEAT_ZERO");
  public static ErrorMessages REPEAT_GREATER = new ErrorMessages("REPEAT_GREATER");
  public static ErrorMessages REGEXP_EXPECTED = new ErrorMessages("REGEXP_EXPECTED");
  public static ErrorMessages MACRO_UNDECL = new ErrorMessages("MACRO_UNDECL");
  public static ErrorMessages CHARSET_2_SMALL = new ErrorMessages("CHARSET_2_SMALL");
  public static ErrorMessages CS2SMALL_STRING = new ErrorMessages("CS2SMALL_STRING");
  public static ErrorMessages CS2SMALL_CHAR = new ErrorMessages("CS2SMALL_CHAR");
  public static ErrorMessages CHARCLASS_MACRO = new ErrorMessages("CHARCLASS_MACRO");
  public static ErrorMessages UNKNOWN_SYNTAX = new ErrorMessages("UNKNOWN_SYNTAX");
  public static ErrorMessages SYNTAX_ERROR = new ErrorMessages("SYNTAX_ERROR");
  public static ErrorMessages NOT_AT_BOL = new ErrorMessages("NOT_AT_BOL");
  public static ErrorMessages NO_MATCHING_BR = new ErrorMessages("NO_MATCHING_BR");
  public static ErrorMessages EOF_IN_ACTION = new ErrorMessages("EOF_IN_ACTION");
  public static ErrorMessages EOF_IN_COMMENT = new ErrorMessages("EOF_IN_COMMENT");
  public static ErrorMessages EOF_IN_STRING = new ErrorMessages("EOF_IN_STRING");
  public static ErrorMessages EOF_IN_MACROS = new ErrorMessages("EOF_IN_MACROS");
  public static ErrorMessages EOF_IN_STATES = new ErrorMessages("EOF_IN_STATES");
  public static ErrorMessages EOF_IN_REGEXP = new ErrorMessages("EOF_IN_REGEXP");
  public static ErrorMessages UNEXPECTED_EOF = new ErrorMessages("UNEXPECTED_EOF");
  public static ErrorMessages NO_LEX_SPEC = new ErrorMessages("NO_LEX_SPEC");
  public static ErrorMessages NO_LAST_ACTION = new ErrorMessages("NO_LAST_ACTION");
  public static ErrorMessages LOOKAHEAD_ERROR = new ErrorMessages("LOOKAHEAD_ERROR");
  public static ErrorMessages NO_DIRECTORY = new ErrorMessages("NO_DIRECTORY");
  public static ErrorMessages NO_SKEL_FILE = new ErrorMessages("NO_SKEL_FILE");
  public static ErrorMessages WRONG_SKELETON = new ErrorMessages("WRONG_SKELETON");
  public static ErrorMessages OUT_OF_MEMORY = new ErrorMessages("OUT_OF_MEMORY");
  public static ErrorMessages QUIL_INITTHROW = new ErrorMessages("QUIL_INITTHROW");
  public static ErrorMessages QUIL_EOFTHROW = new ErrorMessages("QUIL_EOFTHROW");
  public static ErrorMessages QUIL_YYLEXTHROW = new ErrorMessages("QUIL_YYLEXTHROW");
  public static ErrorMessages ZERO_STATES = new ErrorMessages("ZERO_STATES");
  public static ErrorMessages NO_BUFFER_SIZE = new ErrorMessages("NO_BUFFER_SIZE");
  public static ErrorMessages NOT_READABLE = new ErrorMessages("NOT_READABLE");
  public static ErrorMessages FILE_CYCLE = new ErrorMessages("FILE_CYCLE");
  public static ErrorMessages FILE_WRITE = new ErrorMessages("FILE_WRITE");
  public static ErrorMessages QUIL_SCANERROR = new ErrorMessages("QUIL_SCANERROR");
  public static ErrorMessages NEVER_MATCH = new ErrorMessages("NEVER_MATCH");
  public static ErrorMessages QUIL_THROW = new ErrorMessages("QUIL_THROW");
  public static ErrorMessages EOL_IN_CHARCLASS = new ErrorMessages("EOL_IN_CHARCLASS");
  public static ErrorMessages QUIL_CUPSYM = new ErrorMessages("QUIL_CUPSYM");
  public static ErrorMessages CUPSYM_AFTER_CUP = new ErrorMessages("CUPSYM_AFTER_CUP");
  public static ErrorMessages ALREADY_RUNNING = new ErrorMessages("ALREADY_RUNNING");
  public static ErrorMessages CANNOT_READ_SKEL = new ErrorMessages("CANNOT_READ_SKEL");
  public static ErrorMessages READING_SKEL = new ErrorMessages("READING_SKEL");
  public static ErrorMessages SKEL_IO_ERROR = new ErrorMessages("SKEL_IO_ERROR");
  public static ErrorMessages SKEL_IO_ERROR_DEFAULT = new ErrorMessages("SKEL_IO_ERROR_DEFAULT");
  public static ErrorMessages READING = new ErrorMessages("READING");
  public static ErrorMessages CANNOT_OPEN = new ErrorMessages("CANNOT_OPEN");
  public static ErrorMessages NFA_IS = new ErrorMessages("NFA_IS");
  public static ErrorMessages NFA_STATES = new ErrorMessages("NFA_STATES");
  public static ErrorMessages DFA_TOOK = new ErrorMessages("DFA_TOOK");
  public static ErrorMessages DFA_IS = new ErrorMessages("DFA_IS");
  public static ErrorMessages MIN_TOOK = new ErrorMessages("MIN_TOOK");
  public static ErrorMessages MIN_DFA_IS = new ErrorMessages("MIN_DFA_IS");
  public static ErrorMessages WRITE_TOOK = new ErrorMessages("WRITE_TOOK");
  public static ErrorMessages TOTAL_TIME = new ErrorMessages("TOTAL_TIME");
  public static ErrorMessages IO_ERROR = new ErrorMessages("IO_ERROR");
  public static ErrorMessages THIS_IS_JFLEX = new ErrorMessages("THIS_IS_JFLEX");
  public static ErrorMessages UNKNOWN_COMMANDLINE = new ErrorMessages("UNKNOWN_COMMANDLINE");
  public static ErrorMessages MACRO_CYCLE = new ErrorMessages("MACRO_CYCLE");
  public static ErrorMessages MACRO_DEF_MISSING = new ErrorMessages("MACRO_DEF_MISSING");
  public static ErrorMessages PARSING_TOOK = new ErrorMessages("PARSING_TOOK");
  public static ErrorMessages NFA_TOOK = new ErrorMessages("NFA_TOOK");
}