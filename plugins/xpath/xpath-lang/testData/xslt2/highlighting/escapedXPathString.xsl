<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template name="abc">
    <xsl:value-of select="&apos;&apos;" />
    <xsl:value-of select="&apos;&quot;abc&quot;&apos;" />
    <xsl:value-of select="&apos;a&apos;&apos;b&apos;" />
    <xsl:value-of select="<error descr="Malformed string literal">&apos;a&apos;&apos;</error>" />
  </xsl:template>
</xsl:stylesheet>