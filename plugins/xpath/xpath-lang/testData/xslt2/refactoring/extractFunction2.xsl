<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:f="urn:my">

  <xsl:template match="/">
    <xsl:variable name="bar" select="1" />

    <xsl:value-of select="<selection>1+$bar</selection>" />
  </xsl:template>
</xsl:stylesheet>
