/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.1                                                             *
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

package JFlex;

import java.io.File;

/**
 * This Exception could be thrown while scanning the specification 
 * (e.g. unmatched input)
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.3 $, $Date: 2004/11/06 23:03:30 $
 */
public class ScannerException extends RuntimeException {
  
  public int line;
  public int column;
  public ErrorMessages message;
  public File file;

  private ScannerException(File file, String text, ErrorMessages message, int line, int column) {
    super(text);
    this.file    = file;
    this.message = message;
    this.line    = line;
    this.column  = column;
  }


  /**
   * Creates a new ScannerException with a message only.
   *
   * @param message   the code for the error description presented to the user.
   */
  public ScannerException(ErrorMessages message) {
    this( null, ErrorMessages.get(message), message, -1, -1 );
  }

  /**
   * Creates a new ScannerException for a file with a message only.
   *
   * @param file      the file in which the error occured
   * @param message   the code for the error description presented to the user.
   */
  public ScannerException(File file, ErrorMessages message) {
    this( file, ErrorMessages.get(message), message, -1, -1 );
  }


  /**
   * Creates a new ScannerException with a message and line number.
   *
   * @param message   the code for the error description presented to the user.
   * @param line      the number of the line in the specification that 
   *                  contains the error
   */
  public ScannerException(ErrorMessages message, int line) {
    this( null, ErrorMessages.get(message), message, line, -1 );
  }


  /**
   * Creates a new ScannerException for a file with a message and line number.
   *
   * @param message   the code for the error description presented to the user.
   * @param line      the number of the line in the specification that 
   *                  contains the error
   */
  public ScannerException(File file, ErrorMessages message, int line) {
    this( file, ErrorMessages.get(message), message, line, -1 );
  }


  /**
   * Creates a new ScannerException with a message, line number and column.
   *
   * @param message   the code for the error description presented to the user.
   * @param line      the number of the line in the specification that 
   *                  contains the error
   * @param column    the column where the error starts
   */
  public ScannerException(File file, ErrorMessages message, int line, int column) {
    this( file, ErrorMessages.get(message), message, line, column );
  }

}
