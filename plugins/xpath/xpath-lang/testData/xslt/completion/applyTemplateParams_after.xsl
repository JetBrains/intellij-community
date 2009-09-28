<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/">
    <xsl:param name="foo" />

    <xsl:apply-templates>
      <xsl:with-param name="foo" />
    </xsl:apply-templates>
  </xsl:template>

</xsl:stylesheet>