<xsl:transform version="1.0"
               xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
               xmlns:pf1="urn:my">
  <xsl:template name="pf1:name1">
    <xsl:call-template name="pf1:name1" />
  </xsl:template>
</xsl:transform>