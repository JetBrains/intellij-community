<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template name="abc">
    <xsl:call-template name="abc">
      <xsl:with-param name="<error>foo</error>" />
    </xsl:call-template>
  </xsl:template>
</xsl:stylesheet>