<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:my="urn:my"
    version="2.0"
    exclude-result-prefixes="#all">

  <xsl:function name="my:test" as="xs:integer">
    <xsl:param name="b" as="xs:boolean" />
  </xsl:function>

  <xsl:template match="/">
    <xsl:variable name="a" as="xs:integer" select="my:test('true' cast as xs:boolean)" />
    <xsl:value-of select="$a" />
  </xsl:template>

</xsl:stylesheet>
