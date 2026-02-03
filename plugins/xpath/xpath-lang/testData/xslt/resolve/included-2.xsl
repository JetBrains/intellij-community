<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:nsx="nsx">
  <xsl:function name="nsx:func1" as="xs:boolean+">
    <xsl:sequence select="(false(), true())"/>
  </xsl:function>
</xsl:stylesheet>