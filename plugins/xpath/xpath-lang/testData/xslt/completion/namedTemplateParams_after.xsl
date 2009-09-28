<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template name="dummy">
    <xsl:param name="foo" />
    <xsl:call-template name="dummy">
      <xsl:with-param name="foo" />
    </xsl:call-template>
  </xsl:template>

</xsl:stylesheet>