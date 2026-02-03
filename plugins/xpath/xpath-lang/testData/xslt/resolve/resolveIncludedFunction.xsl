<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nsx="nsx">
  <xsl:import href="included-2.xsl" />

  <xsl:template match="/">
    <xsl:variable name="test" select="nsx:func1<caret>()" />
  </xsl:template>
</xsl:stylesheet>