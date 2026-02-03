<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/">
    <xsl:variable name="a" select="'a'"/>
    <xsl:value-of select="$<caret>a" />
  </xsl:template>
</xsl:stylesheet>
