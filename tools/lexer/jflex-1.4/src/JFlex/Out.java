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


import java.io.*;
import java.awt.TextArea;


/**
 * In this class all output to the java console is filtered.
 *
 * Use the switches verbose, time and DUMP at compile time to determine
 * the verbosity of JFlex output. There is no switch for
 * suppressing error messages. verbose and time can be overridden 
 * by command line paramters.
 *
 * Redirects output to a TextArea in GUI mode.
 *
 * Counts error and warning messages.
 *
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $
 */
public final class Out {

  /** platform dependent newline sequence */
  public static final String NL = System.getProperty("line.separator");
  
  /** count total warnings */
  private static int warnings;

  /** count total errors */
  private static int errors;

  /** output device */
  private static StdOutWriter out = new StdOutWriter();


  /**
   * Switches to GUI mode if <code>text</code> is not <code>null</code>
   *
   * @param text  the message TextArea of the JFlex GUI
   */
  public static void setGUIMode(TextArea text) {
    out.setGUIMode(text);
  }
  
  /**
   * Sets a new output stream and switches to non-gui mode.
   * 
   * @param stream  the new output stream
   */
  public static void setOutputStream(OutputStream stream) {
    out = new StdOutWriter(stream);
    out.setGUIMode(null);
  }

  /**
   * Report time statistic data.
   *
   * @param message  the message to be printed
   * @param time     elapsed time
   */
  public static void time(ErrorMessages message, Timer time) {
    if (Options.time) {
      String msg = ErrorMessages.get(message, time.toString());
      out.println(msg);
    } 
  }
  
  /**
   * Report time statistic data.
   *
   * @param message  the message to be printed
   */
  public static void time(String message) {
    if (Options.time) {
      out.println(message);
    } 
  }

  /**
   * Report generation progress.
   *
   * @param message  the message to be printed
   */
  public static void println(String message) {
    if (Options.verbose) 
      out.println(message);
  }

  /**
   * Report generation progress.
   *
   * @param message  the message to be printed
   * @param data     data to be inserted into the message
   */
  public static void println(ErrorMessages message, String data) {
    if (Options.verbose) {      
      out.println(ErrorMessages.get(message,data));
    }
  }

  /**
   * Report generation progress.
   *
   * @param message  the message to be printed
   * @param data     data to be inserted into the message
   */
  public static void println(ErrorMessages message, int data) {
    if (Options.verbose) {      
      out.println(ErrorMessages.get(message,data));
    }
  }

  /**
   * Report generation progress.
   *
   * @param message  the message to be printed
   */
  public static void print(String message) {
    if (Options.verbose) out.print(message);
  }

  /**
   * Dump debug information to System.out
   *
   * Use like this 
   *
   * <code>if (Out.DEBUG) Out.debug(message)</code>
   *
   * to save performance during normal operation (when DEBUG
   * is turned off).
   */
  public static void debug(String message) {
    if (Options.DEBUG) System.out.println(message); 
  }


  /**
   * All parts of JFlex, that want to provide dump information
   * should use this method for their output.
   *
   * @message the message to be printed 
   */
  public static void dump(String message) {
    if (Options.dump) out.println(message);
  }

  
  /**
   * All parts of JFlex, that want to report error messages
   * should use this method for their output.
   *
   * @message  the message to be printed
   */
  private static void err(String message) {
    out.println(message);
  }
  
  
  /**
   * throws a GeneratorException if there are any errors recorded
   */
  public static void checkErrors() {
    if (errors > 0) throw new GeneratorException();
  }
  

  /**
   * print error and warning statistics
   */
  public static void statistics() {    
    StringBuffer line = new StringBuffer(errors+" error");
    if (errors != 1) line.append("s");

    line.append(", "+warnings+" warning");
    if (warnings != 1) line.append("s");

    line.append(".");
    err(line.toString());
  }


  /**
   * reset error and warning counters
   */
  public static void resetCounters() {
    errors = 0;
    warnings = 0;
  }

  
  /**
   * print a warning without position information
   *
   * @param message   the warning message
   */  
  public static void warning(String message) {
    warnings++;

    err(NL+"Warning : "+message);
  }


  /**
   * print a warning with line information
   *
   * @param message  code of the warning message
   * @param line     the line information
   *
   * @see ErrorMessages
   */
  public static void warning(ErrorMessages message, int line) {
    warnings++;

    String msg = NL+"Warning";
    if (line > 0) msg = msg+" in line "+(line+1);

    err(msg+": "+ErrorMessages.get(message));
  }


  /**
   * print warning message with location information
   *
   * @param file     the file the warning is issued for
   * @param message  the code of the message to print
   * @param line     the line number of the position
   * @param column   the column of the position
   */
  public static void warning(File file, ErrorMessages message, int line, int column) {

    String msg = NL+"Warning";
    if (file != null) msg += " in file \""+file+"\"";
    if (line >= 0) msg = msg+" (line "+(line+1)+")";

    try {
      err(msg+": "+NL+ErrorMessages.get(message));
    }
    catch (ArrayIndexOutOfBoundsException e) {
      err(msg);
    }

    warnings++;

    if (line >= 0) {
      if (column >= 0)
        showPosition(file, line, column);
      else 
        showPosition(file, line);
    }
  }

  
  /**
   * print error message (string)
   *
   * @param message  the message to print
   */
  public static void error(String message) {
    errors++;
    err(NL+message);
  }


  /**
   * print error message (code)
   *  
   * @param message  the code of the error message
   *
   * @see ErrorMessages   
   */ 
  public static void error(ErrorMessages message) {
    errors++;
    err(NL+"Error: "+ErrorMessages.get(message) );
  }


  /**
   * print error message with data 
   *  
   * @param data     data to insert into the message
   * @param message  the code of the error message
   *
   * @see ErrorMessages   
   */ 
  public static void error(ErrorMessages message, String data) {
    errors++;    
    err(NL+"Error: "+ ErrorMessages.get(message,data));
  }


  /**
   * IO error message for a file (displays file 
   * name in parentheses).
   *
   * @param message  the code of the error message
   * @param file     the file it occurred for
   */
  public static void error(ErrorMessages message, File file) {
    errors++;
    err(NL+"Error: "+ErrorMessages.get(message)+" ("+file+")");
  }


  /**
   * print error message with location information
   *
   * @param file     the file the error occurred for
   * @param message  the code of the error message to print
   * @param line     the line number of error position
   * @param column   the column of error position
   */
  public static void error(File file, ErrorMessages message, int line, int column) {

    String msg = NL+"Error";
    if (file != null) msg += " in file \""+file+"\"";
    if (line >= 0) msg = msg+" (line "+(line+1)+")";

    try {
      err(msg+": "+NL+ErrorMessages.get(message));
    }
    catch (ArrayIndexOutOfBoundsException e) {
      err(msg);
    }

    errors++;

    if (line >= 0) {
      if (column >= 0)
        showPosition(file, line, column);
      else 
        showPosition(file, line);
    }
  }


  /**
   * prints a line of a file with marked position.
   *
   * @param file    the file of which to show the line
   * @param line    the line to show
   * @param column  the column in which to show the marker
   */
  public static void showPosition(File file, int line, int column) {
    try {
      String ln = getLine(file, line);
      if (ln != null) {
        err( ln );

        if (column < 0) return;
        
        String t = "^";  
        for (int i = 0; i < column; i++) t = " "+t;  
        
        err(t);
      }
    }
    catch (IOException e) {
      /* silently ignore IO errors, don't show anything */
    }
  }


  /**
   * print a line of a file
   *
   * @param file  the file to show
   * @param line  the line number 
   */
  public static void showPosition(File file, int line) {
    try {
      String ln = getLine(file, line);
      if (ln != null) err(ln);
    }
    catch (IOException e) {
      /* silently ignore IO errors, don't show anything */
    }
  }


  /**
   * get one line from a file 
   *
   * @param file   the file to read
   * @param line   the line number to get
   *
   * @throw IOException  if any error occurs
   */
  private static String getLine(File file, int line) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(file));

    String msg = "";

    for (int i = 0; i <= line; i++)
      msg = reader.readLine();

    reader.close();
    
    return msg;
  }


  /**
   * Print system information (e.g. in case of unexpected exceptions)
   */
  public static void printSystemInfo() {
    err("Java version:  "+System.getProperty("java.version"));
    err("Runtime name:  "+System.getProperty("java.runtime.name"));
    err("Vendor:        "+System.getProperty("java.vendor")); 
    err("VM version:    "+System.getProperty("java.vm.version")); 
    err("VM vendor:     "+System.getProperty("java.vm.vendor"));
    err("VM name:       "+System.getProperty("java.vm.name"));
    err("VM info:       "+System.getProperty("java.vm.info"));
    err("OS name:       "+System.getProperty("os.name"));
    err("OS arch:       "+System.getProperty("os.arch"));
    err("OS version:    "+System.getProperty("os.version"));
    err("Encoding:      "+System.getProperty("file.encoding"));
    err("JFlex version: "+Main.version);
  }


  /**
   * Request a bug report for an unexpected Exception/Error.
   */
  public static void requestBugReport(Error e) {
    err("An unexpected error occurred. Please send a report of this to");
    err("<bugs@jflex.de> and include the following information:");
    err("");
    printSystemInfo();
    err("Exception:");
    e.printStackTrace(out);
    err("");
    err("Please also include a specification (as small as possible)");
    err("that triggers this error. You may also want to check at");
    err("http://www.jflex.de if there is a newer version available");
    err("that doesn't have this problem");
    err("");
    err("Thanks for your support.");
  }
}
