<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="bar" />

  <xsl:template name="abc">
    <p>
      <xsl:variable name="foo" />
      <xsl:value-of select="$foo" />
    </p>
    <p>
      <xsl:variable name="foo" />
      <xsl:value-of select="$foo" />
    </p>

    <xsl:variable name="foo" />
    <xsl:value-of select="$foo" />
  </xsl:template>
</xsl:stylesheet>