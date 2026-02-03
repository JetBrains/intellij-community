<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE xsl:stylesheet[
    <!ENTITY root "/doc">
    <!ENTITY elem "&root;/elem">
    ]>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:variable name="sample" select="&elem;/elem2 | &elem;/elem3" />

</xsl:stylesheet>