<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/" mode="abc" />

  <xsl:template match="/">
    <xsl:apply-templates mode="a<caret>" />
  </xsl:template>

</xsl:stylesheet>