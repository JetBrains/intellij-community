<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:template match="/">
    <xsl:variable name="x" select="." as="xs:NMTOK<caret>"/>
  </xsl:template>
</xsl:stylesheet>
