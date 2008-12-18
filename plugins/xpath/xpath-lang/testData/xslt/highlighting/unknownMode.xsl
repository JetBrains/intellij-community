<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <xsl:apply-templates mode="<error>abc</error>" />
  </xsl:template>
</xsl:stylesheet>