// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.python.requirements.parser.psi.impl.*;

public interface RequirementsTypes {

  IElementType AUTHORITY = new RequirementsElementType("AUTHORITY");
  IElementType BZR_LAUNCHPAD_URI = new RequirementsElementType("BZR_LAUNCHPAD_URI");
  IElementType EDITABLE_OPTION = new RequirementsElementType("EDITABLE_OPTION");
  IElementType ENV_VARIABLE = new RequirementsElementType("ENV_VARIABLE");
  IElementType EXTRAS = new RequirementsElementType("EXTRAS");
  IElementType EXTRAS_LIST = new RequirementsElementType("EXTRAS_LIST");
  IElementType FRAGMENT = new RequirementsElementType("FRAGMENT");
  IElementType GIT_URI = new RequirementsElementType("GIT_URI");
  IElementType GIT_URI_PATH = new RequirementsElementType("GIT_URI_PATH");
  IElementType GIT_URI_PATH_SEGMENT = new RequirementsElementType("GIT_URI_PATH_SEGMENT");
  IElementType HOST = new RequirementsElementType("HOST");
  IElementType LONG_OPTION = new RequirementsElementType("LONG_OPTION");
  IElementType LONG_OPTION_NAME = new RequirementsElementType("LONG_OPTION_NAME");
  IElementType MARKER_AND = new RequirementsElementType("MARKER_AND");
  IElementType MARKER_EXPR = new RequirementsElementType("MARKER_EXPR");
  IElementType MARKER_NAME = new RequirementsElementType("MARKER_NAME");
  IElementType MARKER_OP = new RequirementsElementType("MARKER_OP");
  IElementType MARKER_OR = new RequirementsElementType("MARKER_OR");
  IElementType NAME_REQ = new RequirementsElementType("NAME_REQ");
  IElementType OPTION = new RequirementsElementType("OPTION");
  IElementType OPTION_VALUE = new RequirementsElementType("OPTION_VALUE");
  IElementType PACKAGE_NAME = new RequirementsElementType("PACKAGE_NAME");
  IElementType PATH = new RequirementsElementType("PATH");
  IElementType PATH_REQ = new RequirementsElementType("PATH_REQ");
  IElementType PORT = new RequirementsElementType("PORT");
  IElementType PYTHON_STR = new RequirementsElementType("PYTHON_STR");
  IElementType QUERY = new RequirementsElementType("QUERY");
  IElementType QUOTED_MARKER = new RequirementsElementType("QUOTED_MARKER");
  IElementType SCHEME = new RequirementsElementType("SCHEME");
  IElementType SHORT_OPTION = new RequirementsElementType("SHORT_OPTION");
  IElementType SHORT_OPTION_NAME = new RequirementsElementType("SHORT_OPTION_NAME");
  IElementType URI = new RequirementsElementType("URI");
  IElementType URI_PATH = new RequirementsElementType("URI_PATH");
  IElementType URI_REFERENCE = new RequirementsElementType("URI_REFERENCE");
  IElementType URL_REQ = new RequirementsElementType("URL_REQ");
  IElementType USERINFO = new RequirementsElementType("USERINFO");
  IElementType VARIABLE_NAME = new RequirementsElementType("VARIABLE_NAME");
  IElementType VCS_REVISION = new RequirementsElementType("VCS_REVISION");
  IElementType VERSION = new RequirementsElementType("VERSION");
  IElementType VERSIONSPEC = new RequirementsElementType("VERSIONSPEC");
  IElementType VERSION_CMP = new RequirementsElementType("VERSION_CMP");
  IElementType VERSION_ONE = new RequirementsElementType("VERSION_ONE");

  IElementType AND = new RequirementsTokenType("AND");
  IElementType AT = new RequirementsTokenType("AT");
  IElementType BZR_LAUNCHPAD_SCHEME = new RequirementsTokenType("BZR_LAUNCHPAD_SCHEME");
  IElementType COLON = new RequirementsTokenType("COLON");
  IElementType COMMA = new RequirementsTokenType("COMMA");
  IElementType COMMENT = new RequirementsTokenType("COMMENT");
  IElementType DIGIT = new RequirementsTokenType("DIGIT");
  IElementType DOLLAR_SIGN = new RequirementsTokenType("DOLLAR_SIGN");
  IElementType DOT = new RequirementsTokenType("DOT");
  IElementType DQUOTE = new RequirementsTokenType("DQUOTE");
  IElementType DRIVE_LETTER = new RequirementsTokenType("DRIVE_LETTER");
  IElementType EDITABLE_OPTION_IDENTIFIER = new RequirementsTokenType("EDITABLE_OPTION_IDENTIFIER");
  IElementType ENV_MARKER_NAME = new RequirementsTokenType("ENV_MARKER_NAME");
  IElementType ENV_VARIABLE_END = new RequirementsTokenType("ENV_VARIABLE_END");
  IElementType ENV_VARIABLE_NAME = new RequirementsTokenType("ENV_VARIABLE_NAME");
  IElementType ENV_VARIABLE_START = new RequirementsTokenType("ENV_VARIABLE_START");
  IElementType EOL = new RequirementsTokenType("EOL");
  IElementType EQUAL = new RequirementsTokenType("EQUAL");
  IElementType GIT_URI_SCHEME = new RequirementsTokenType("GIT_URI_SCHEME");
  IElementType HYPHEN = new RequirementsTokenType("HYPHEN");
  IElementType IN_OP = new RequirementsTokenType("IN_OP");
  IElementType LETTER = new RequirementsTokenType("LETTER");
  IElementType LONG_OPTION_IDENTIFIER = new RequirementsTokenType("LONG_OPTION_IDENTIFIER");
  IElementType LPARENTHESIS = new RequirementsTokenType("LPARENTHESIS");
  IElementType LSBRACE = new RequirementsTokenType("LSBRACE");
  IElementType NOTIN_OP = new RequirementsTokenType("NOTIN_OP");
  IElementType OPTION_VALUE_TOKEN = new RequirementsTokenType("OPTION_VALUE_TOKEN");
  IElementType OR = new RequirementsTokenType("OR");
  IElementType PACKAGE_NAME_TOKEN = new RequirementsTokenType("PACKAGE_NAME_TOKEN");
  IElementType PATH_SEGMENT = new RequirementsTokenType("PATH_SEGMENT");
  IElementType PATH_SEPARATOR = new RequirementsTokenType("PATH_SEPARATOR");
  IElementType PCT_ENCODED = new RequirementsTokenType("PCT_ENCODED");
  IElementType QUESTION_MARK = new RequirementsTokenType("QUESTION_MARK");
  IElementType QUOTED_STRING_TOKEN = new RequirementsTokenType("QUOTED_STRING_TOKEN");
  IElementType RPARENTHESIS = new RequirementsTokenType("RPARENTHESIS");
  IElementType RSBRACE = new RequirementsTokenType("RSBRACE");
  IElementType SEMICOLON = new RequirementsTokenType("SEMICOLON");
  IElementType SHARP = new RequirementsTokenType("SHARP");
  IElementType SHORT_OPTION_IDENTIFIER = new RequirementsTokenType("SHORT_OPTION_IDENTIFIER");
  IElementType SLASH = new RequirementsTokenType("SLASH");
  IElementType SQUOTE = new RequirementsTokenType("SQUOTE");
  IElementType UNDERSCORE = new RequirementsTokenType("UNDERSCORE");
  IElementType URI_SCHEME = new RequirementsTokenType("URI_SCHEME");
  IElementType URI_SUB_DELIMITER = new RequirementsTokenType("URI_SUB_DELIMITER");
  IElementType URI_UNRESERVED = new RequirementsTokenType("URI_UNRESERVED");
  IElementType VERSION_CMP_TOKEN = new RequirementsTokenType("VERSION_CMP_TOKEN");
  IElementType VERSION_TOKEN = new RequirementsTokenType("VERSION_TOKEN");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == AUTHORITY) {
        return new AuthorityImpl(node);
      }
      else if (type == BZR_LAUNCHPAD_URI) {
        return new BzrLaunchpadUriImpl(node);
      }
      else if (type == EDITABLE_OPTION) {
        return new EditableOptionImpl(node);
      }
      else if (type == ENV_VARIABLE) {
        return new EnvVariableImpl(node);
      }
      else if (type == EXTRAS) {
        return new ExtrasImpl(node);
      }
      else if (type == EXTRAS_LIST) {
        return new ExtrasListImpl(node);
      }
      else if (type == FRAGMENT) {
        return new FragmentImpl(node);
      }
      else if (type == GIT_URI) {
        return new GitUriImpl(node);
      }
      else if (type == GIT_URI_PATH) {
        return new GitUriPathImpl(node);
      }
      else if (type == GIT_URI_PATH_SEGMENT) {
        return new GitUriPathSegmentImpl(node);
      }
      else if (type == HOST) {
        return new HostImpl(node);
      }
      else if (type == LONG_OPTION) {
        return new LongOptionImpl(node);
      }
      else if (type == LONG_OPTION_NAME) {
        return new LongOptionNameImpl(node);
      }
      else if (type == MARKER_AND) {
        return new MarkerAndImpl(node);
      }
      else if (type == MARKER_EXPR) {
        return new MarkerExprImpl(node);
      }
      else if (type == MARKER_NAME) {
        return new MarkerNameImpl(node);
      }
      else if (type == MARKER_OP) {
        return new MarkerOpImpl(node);
      }
      else if (type == MARKER_OR) {
        return new MarkerOrImpl(node);
      }
      else if (type == NAME_REQ) {
        return new NameReqImpl(node);
      }
      else if (type == OPTION) {
        return new OptionImpl(node);
      }
      else if (type == OPTION_VALUE) {
        return new OptionValueImpl(node);
      }
      else if (type == PACKAGE_NAME) {
        return new PackageNameImpl(node);
      }
      else if (type == PATH) {
        return new PathImpl(node);
      }
      else if (type == PATH_REQ) {
        return new PathReqImpl(node);
      }
      else if (type == PORT) {
        return new PortImpl(node);
      }
      else if (type == PYTHON_STR) {
        return new PythonStrImpl(node);
      }
      else if (type == QUERY) {
        return new QueryImpl(node);
      }
      else if (type == QUOTED_MARKER) {
        return new QuotedMarkerImpl(node);
      }
      else if (type == SCHEME) {
        return new SchemeImpl(node);
      }
      else if (type == SHORT_OPTION) {
        return new ShortOptionImpl(node);
      }
      else if (type == SHORT_OPTION_NAME) {
        return new ShortOptionNameImpl(node);
      }
      else if (type == URI) {
        return new UriImpl(node);
      }
      else if (type == URI_PATH) {
        return new UriPathImpl(node);
      }
      else if (type == URI_REFERENCE) {
        return new UriReferenceImpl(node);
      }
      else if (type == URL_REQ) {
        return new UrlReqImpl(node);
      }
      else if (type == USERINFO) {
        return new UserinfoImpl(node);
      }
      else if (type == VARIABLE_NAME) {
        return new VariableNameImpl(node);
      }
      else if (type == VCS_REVISION) {
        return new VcsRevisionImpl(node);
      }
      else if (type == VERSION) {
        return new VersionImpl(node);
      }
      else if (type == VERSIONSPEC) {
        return new VersionspecImpl(node);
      }
      else if (type == VERSION_CMP) {
        return new VersionCmpImpl(node);
      }
      else if (type == VERSION_ONE) {
        return new VersionOneImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
