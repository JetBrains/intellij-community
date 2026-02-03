<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nsx="nsx" xmlns:xs="http://www.w3.org/2001/XMLSchema" >
  <xsl:function name="nsx:func1" as="xs:boolean+">
    <xsl:sequence select="(false(), true())"/>
  </xsl:function>

  <xsl:template match="/">
    <xsl:variable name="test" select="nsx:func1<caret>()" />
  </xsl:template>
</xsl:stylesheet>