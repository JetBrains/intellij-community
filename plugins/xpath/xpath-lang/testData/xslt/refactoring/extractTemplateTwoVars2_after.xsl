<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/">
    <xsl:variable name="a" />
    <xsl:variable name="b" />
    <xsl:call-template name="foo">
      <xsl:with-param name="a" select="$a" />
      <xsl:with-param name="b" select="$b" />
    </xsl:call-template>
  </xsl:template>
  <xsl:template name="foo">
    <xsl:param name="a" />
    <xsl:param name="b" />
    <xsl:value-of select="$a + $b" />
  </xsl:template>
</xsl:stylesheet>
