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

import java.util.*;


/**
 * Symbol table and expander for macros.
 * 
 * Maps macros to their (expanded) definitions, detects cycles and 
 * unused macros.
 *
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $
 */
final public class Macros {

  /** Maps names of macros to their definition */
  private Hashtable macros;

  /** Maps names of macros to their "used" flag */
  private Hashtable used;


  /**
   * Creates a new macro expander.
   */
  public Macros() {
    macros = new Hashtable();
    used = new Hashtable();
  }


  /**
   * Stores a new macro and its definition.
   *
   * @param name         the name of the new macro
   * @param definition   the definition of the new macro
   *
   * @return <code>true</code>, iff the macro name has not been
   *         stored before.
   */
  public boolean insert(String name, RegExp definition) {
    
    if (Options.DEBUG) 
      Out.debug("inserting macro "+name+" with definition :"+Out.NL+definition); //$NON-NLS-1$ //$NON-NLS-2$
      
    used.put(name, Boolean.FALSE);
    return macros.put(name,definition) == null;    
  }


  /**
   * Marks a makro as used.
   *
   * @return <code>true</code>, iff the macro name has been
   *         stored before.
   */
  public boolean markUsed(String name) {
    return used.put(name, Boolean.TRUE) != null;
  }


  /**
   * Tests if a macro has been used.
   *
   * @return <code>true</code>, iff the macro has been used in 
   *         a regular expression.
   */
  public boolean isUsed(String name) {
    return ((Boolean)used.get(name)).booleanValue();
  }


  /**
   * Returns all unused macros.
   *
   * @return the enumeration of macro names that have not been used.
   */
  public Enumeration unused() {
    
    Vector unUsed = new Vector();

    Enumeration names = used.keys();
    while ( names.hasMoreElements() ) {
      String name = (String) names.nextElement();
      Boolean isUsed = (Boolean) used.get( name );
      if ( !isUsed.booleanValue() ) unUsed.addElement(name);
    }
    
    return unUsed.elements();
  }


  /**
   * Fetches the definition of the macro with the specified name,
   * <p>
   * The definition will either be the same as stored (expand() not 
   * called), or an equivalent one, that doesn't contain any macro 
   * usages (expand() called before).
   *
   * @param name   the name of the macro
   *
   * @return the definition of the macro, <code>null</code> if 
   *         no macro with the specified name has been stored.
   *
   * @see JFlex.Macros#expand
   */
  public RegExp getDefinition(String name) {
    return (RegExp) macros.get(name);
  }


  /**
   * Expands all stored macros, so that getDefinition always returns
   * a defintion that doesn't contain any macro usages.
   *
   * @throws MacroException   if there is a cycle in the macro usage graph.
   */
   public void expand() throws MacroException {
    
    Enumeration names;

    names = macros.keys();
    
    while ( names.hasMoreElements() ) {
      String name = (String) names.nextElement();
      if ( isUsed(name) )
        macros.put(name, expandMacro(name, getDefinition(name))); 
      // this put doesn't get a new key, so only a new value
      // is set for the key "name" (without changing the enumeration 
      // "names"!)
    }
  }


  /**
   * Expands the specified macro by replacing each macro usage
   * with the stored definition. 
   *   
   * @param name        the name of the macro to expand (for detecting cycles)
   * @param definition  the definition of the macro to expand
   *
   * @return the expanded definition of the macro.
   * 
   * @throws MacroException when an error (such as a cyclic definition)
   *                              occurs during expansion
   */
  private RegExp expandMacro(String name, RegExp definition) throws MacroException {

    // Out.print("checking macro "+name);
    // Out.print("definition is "+definition);

    switch ( definition.type ) {
    case sym.BAR: 
    case sym.CONCAT:   
      RegExp2 binary = (RegExp2) definition;
      binary.r1 = expandMacro(name, binary.r1);
      binary.r2 = expandMacro(name, binary.r2);
      return definition;
      
    case sym.STAR:
    case sym.PLUS:
    case sym.QUESTION: 
    case sym.BANG:
    case sym.TILDE:
      RegExp1 unary = (RegExp1) definition;
      unary.content = expandMacro(name, (RegExp) unary.content);
      return definition;
      
    case sym.MACROUSE: 
      String usename = (String) ((RegExp1) definition).content;
      
      if ( name.equals(usename) )
        throw new MacroException(ErrorMessages.get(ErrorMessages.MACRO_CYCLE, name)); 
          
      RegExp usedef = getDefinition(usename);
      
      if ( usedef == null ) 
        throw new MacroException(ErrorMessages.get(ErrorMessages.MACRO_DEF_MISSING, usename, name));
      
      markUsed(usename);
      
      return expandMacro(name, usedef);

    case sym.STRING:
    case sym.STRING_I:
    case sym.CHAR:
    case sym.CHAR_I:
    case sym.CCLASS:
    case sym.CCLASSNOT:
      return definition;

    default:
      throw new MacroException("unknown expression type "+definition.type+" in macro expansion"); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }
}
