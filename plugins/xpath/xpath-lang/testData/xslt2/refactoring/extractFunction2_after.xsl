<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:f="urn:my">

  <xsl:function name="f:foo" as="xs:integer">
    <xsl:param name="bar" as="xs:integer" />
    <xsl:sequence select="1+$bar" />
  </xsl:function>
  <xsl:template match="/">
    <xsl:variable name="bar" select="1" />

    <xsl:value-of select="<selection>f:foo($bar)</selection>" />
  </xsl:template>
</xsl:stylesheet>
