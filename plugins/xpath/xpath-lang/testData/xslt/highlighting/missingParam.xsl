<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template name="abc">
    <xsl:param name="foo" />
    <xsl:value-of select="$foo" />
    
    <xsl:call-template name="<error>abc</error>" />
  </xsl:template>
</xsl:stylesheet>