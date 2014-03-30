/*
 * Created on Nov 18, 2004
 *
 * @author Fabio Zadrozny
 */
package com.jetbrains.python.console.pydev;

import java.io.Serializable;


/**
 * @author Fabio Zadrozny
 */
public interface IToken extends Serializable, Comparable{

    /**
     * Type for unknown.
     */
    int TYPE_UNKNOWN = -1;
    /**
     * Type for import (used to decide the icon)
     */
    int TYPE_IMPORT = 0;
    /**
     * Type for class (used to decide the icon)
     */
    int TYPE_CLASS = 1;
    /**
     * Type for function (used to decide the icon)
     */
    int TYPE_FUNCTION = 2;
    /**
     * Type for attr (used to decide the icon)
     */
    int TYPE_ATTR = 3;
    /**
     * Type for attr (used to decide the icon)
     */
    int TYPE_BUILTIN = 4;
    /**
     * Type for parameter (used to decide the icon)
     */
    int TYPE_PARAM = 5;
    /**
     * Type for package (used to decide the icon)
     */
    int TYPE_PACKAGE = 6;
    /**
     * Type for relative import
     */
    int TYPE_RELATIVE_IMPORT = 7;
    /**
     * Type for an epydoc field
     */
    int TYPE_EPYDOC = 8;
    /**
     * Type for local (used to decide the icon)
     */
    int TYPE_LOCAL = 9;
    /**
     * Type for local (used to decide the icon) -- so, this means that the token created results
     * as an interface from some object in a local scope.
     *
     * E.g.:
     * a = 10
     * a.foo = 20
     * a.bar = 30
     *
     * 'foo' and 'bar' would be generated with this type
     */
    int TYPE_OBJECT_FOUND_INTERFACE = 10;

    /**
     * @return the type for this token
     */
    int getType();

    /**
     *
     * @return the representation of this token.
     *
     * The following cases apply for imports:
     *
     * from x import testcase     (return testcase)
     * from x import testcase as t(return t)
     * import testcase            (return testcase)
     */
    String getRepresentation();
    String getDocStr();
    void setDocStr(String docStr);
    String getArgs();
    void setArgs(String args);
    String getParentPackage();

    /**
     *
     * @return The complete path for the token.
     *
     * The following cases apply for imports:
     *
     * on module test decorating with module
     * from x import testcase     (return test.x.testcase)
     * from x import testcase as t(return test.x.testcase)
     * import testcase            (return test.testcase)
     *
     * if not decorating would return the same as above without 'test'
     */
//    public String getOriginalRep(boolean decorateWithModule);

    /**
     * @param baseModule this is the module base to discover from where should it be made relative
     *
     * @return the representation as a relative import - this means that it return the original representation
     * plus the module it was defined within without its head.
     */
    String getAsRelativeImport(String baseModule);

    /**
     * Same as relative from "."
     */
    String getAsAbsoluteImport();

    /**
     * Constant to indicate that it was not possible to know in which line the
     * token was defined.
     */
    int UNDEFINED = -1;
    /**
     * @return the line where this token was defined
     */
    int getLineDefinition();
    /**
     * @return the col where this token was defined
     */
    int getColDefinition();

    /**
     * @return whether the token we have wrapped is an import
     */
    boolean isImport();

    /**
     * @return whether this token defined as part of "from ... import ..."
     */
    boolean isImportFrom();

    /**
     * @return whether the token we have wrapped is a wild import
     */
    boolean isWildImport();

    /**
     * @return the original representation (useful for imports)
     * e.g.: if it was import coilib.Exceptions as Exceptions, would return coilib.Exceptions
     */
    String getOriginalRep();

    /**
     * @return the original representation without the actual representation (useful for imports, because
     * we have to look within __init__ to check if the token is defined before trying to gather modules, if
     * we have a name clash).
     *
     * e.g.: if it was import from coilib.test import Exceptions, it would return coilib.test
     */
    String getOriginalWithoutRep();

    /**
     * @return
     */
    int[] getLineColEnd();

    boolean isString();

    /**
     * @return the image that should be used in the code-completion for this token.
     */
    //public Image getImage();
}