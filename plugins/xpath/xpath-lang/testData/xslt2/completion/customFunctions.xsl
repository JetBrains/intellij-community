<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:n="urn:my">

  <xsl:function name="n:foo" />

  <xsl:template match="/">
    <xsl:value-of select="n:f<caret>" />
  </xsl:template>

</xsl:stylesheet>