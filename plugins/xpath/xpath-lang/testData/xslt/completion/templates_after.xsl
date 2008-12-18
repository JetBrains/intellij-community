<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template name="dummy">
    <xsl:call-template name="dummy" />
  </xsl:template>

</xsl:stylesheet>