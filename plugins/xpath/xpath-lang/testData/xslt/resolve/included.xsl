<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:variable name="dummy" />

  <xsl:template name="dummy">
    <xsl:param name="foo" />
    <xsl:value-of select="$foo" />
  </xsl:template>

</xsl:stylesheet>