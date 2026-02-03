<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="bar"/>

  <xsl:template match="/">
    <xsl:variable name="foo" select="1"/>
    <xsl:value-of select="$foo"/>
    <xsl:variable name="<warning>foo</warning>" select="2"/>
    <xsl:value-of select="$foo"/>
  </xsl:template>
</xsl:stylesheet>