<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/">
    <xsl:variable name="a" />
    <selection>
      <xsl:value-of select="$a" />
      <xsl:value-of select="$a" />
    </selection>
  </xsl:template>
</xsl:stylesheet>
