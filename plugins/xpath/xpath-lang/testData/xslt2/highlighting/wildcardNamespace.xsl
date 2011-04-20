<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
  <xsl:template match="/">
    <xsl:value-of select="./*:root/*:container[1]" />
  </xsl:template>
</xsl:stylesheet>
