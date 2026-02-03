<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xss="http://www.w3.org/2001/XMLSchema"
                xmlns:n="urn:my">

  <xsl:template match="/">
    <xsl:value-of select="1 treat as xss:boo<caret>" />
  </xsl:template>

</xsl:stylesheet>