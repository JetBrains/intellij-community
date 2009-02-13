<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/">
    <xsl:variable name="a" />
    <xsl:variable name="b" />
    <selection><xsl:value-of select="$a + $b" /></selection>
  </xsl:template>
</xsl:stylesheet>
