<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:nsx="nsx"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="nsx move-def.xsd"
                exclude-result-prefixes="nsx xsi">
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/">
    <result>
      <xsl:variable name="var1" as="nsx:custom-type-1">AAA</xsl:variable> <!-- red, bug -->
      <test><xsl:value-of select="$var1"/></test>
      <xsl:variable name="var2" as="nsx:customType2">BBB</xsl:variable> <!-- green, correct -->
      <test><xsl:value-of select="$var2"/></test>
      <xsl:variable name="var3" as="<error descr="Unknown Type">nsx:customType2-blam</error>">CCC</xsl:variable> <!-- green, bug -->
      <test><xsl:value-of select="$var3"/></test>
    </result>

  </xsl:template>
</xsl:stylesheet>