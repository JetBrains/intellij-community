package com.intellij.psi.xml;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IDTDElementType;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 18.08.2004
 * Time: 16:45:52
 * To change this template use File | Settings | File Templates.
 */
public interface DTDTokenType {
  IDTDElementType DTD_NAME = new IDTDElementType("DTD_NAME");
  IDTDElementType DTD_NMTOKEN = new IDTDElementType("DTD_NMTOKEN");
  IDTDElementType DTD_ATTRIBUTE_VALUE_TOKEN = new IDTDElementType("DTD_ATTRIBUTE_VALUE_TOKEN");
  IDTDElementType DTD_ATTRIBUTE_VALUE_START_DELIMITER = new IDTDElementType("DTD_ATTRIBUTE_VALUE_START_DELIMITER");// "\"" or "'"
  IDTDElementType DTD_ATTRIBUTE_VALUE_END_DELIMITER = new IDTDElementType("DTD_ATTRIBUTE_VALUE_END_DELIMITER");// "\"" or "'"

  IDTDElementType DTD_ELEMENT_DECL_START = new IDTDElementType("DTD_ELEMENT_DECL_START");//<!ELEMENT
  IDTDElementType DTD_NOTATION_DECL_START = new IDTDElementType("DTD_NOTATION_DECL_START");//<!NOTATION
  IDTDElementType DTD_ATTLIST_DECL_START = new IDTDElementType("DTD_ATTLIST_DECL_START");//<!ATTLIST
  IDTDElementType DTD_ENTITY_DECL_START = new IDTDElementType("DTD_ENTITY_DECL_START");//<!ENTITY
  IDTDElementType DTD_CONDITIONAL_START = new IDTDElementType("DTD_CONDITIONAL_START");

  IDTDElementType DTD_DECL_END = new IDTDElementType("DTD_DECL_END");

  IDTDElementType DTD_LEFT_PAREN = new IDTDElementType("DTD_LEFT_PAREN");//(
  IDTDElementType DTD_RIGHT_PAREN = new IDTDElementType("DTD_RIGHT_PAREN");//)
  IDTDElementType DTD_QUESTION = new IDTDElementType("DTD_QUESTION");//?
  IDTDElementType DTD_STAR = new IDTDElementType("DTD_STAR");//*
  IDTDElementType DTD_PLUS = new IDTDElementType("DTD_PLUS");//+
  IDTDElementType DTD_BAR = new IDTDElementType("DTD_BAR");//|
  IDTDElementType DTD_COMMA = new IDTDElementType("DTD_COMMA");//,
  IDTDElementType DTD_AMP = new IDTDElementType("DTD_AMP");//&
  IDTDElementType DTD_PERCENT = new IDTDElementType("DTD_PERCENT");//%

  IDTDElementType DTD_TAG_CONTENT_SPEC = new IDTDElementType("DTD_TAG_CONTENT_SPEC");// EMPTY, ANY...
  IDTDElementType DTD_PCDATA = new IDTDElementType("DTD_PCDATA");//#PCDATA

  IDTDElementType DTD_ATTR_CONTENT_SPEC = new IDTDElementType("DTD_ATTR_CONTENT_SPEC");

  IDTDElementType DTD_ATT_IMPLIED = new IDTDElementType("DTD_ATT_IMPLIED");//; #IMPLIED
  IDTDElementType DTD_ATT_REQUIRED = new IDTDElementType("DTD_ATT_REQUIRED");//; #REQUIRED
  IDTDElementType DTD_ATT_FIXED = new IDTDElementType("DTD_ATT_FIXED");//; #FIXED

  IDTDElementType DTD_ENTITY_REF_TOKEN = new IDTDElementType("DTD_ENTITY_REF_TOKEN");
  IDTDElementType DTD_CHAR_ENTITY_REF = new IDTDElementType("DTD_CHAR_ENTITY_REF");

  IDTDElementType DTD_INCLUDE = new IDTDElementType("DTD_INCLUDE");
  IDTDElementType DTD_IGNORE = new IDTDElementType("DTD_IGNORE");

  IDTDElementType DTD_PUBLIC = new IDTDElementType("DTD_PUBLIC");
  IDTDElementType DTD_SYSTEM = new IDTDElementType("DTD_SYSTEM");

  IDTDElementType DTD_NOTATION = new IDTDElementType("DTD_NOTATION");

  IDTDElementType DTD_NDATA = new IDTDElementType("DTD_NDATA");

  IDTDElementType DTD_BAD_CHARACTER = new IDTDElementType("DTD_BAD_CHARACTER");

  IElementType DTD_WHITE_SPACE = JavaTokenType.WHITE_SPACE;

  IElementType DTD_COMMENT_START = XmlTokenType.XML_COMMENT_START;
  IElementType DTD_COMMENT_END = XmlTokenType.XML_COMMENT_END;
  IElementType DTD_COMMENT_CHARACTERS = XmlTokenType.XML_COMMENT_CHARACTERS;
}
