<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" indent="yes"/>
  <xsl:template match="/">
    <result>
      <test attr="{ 1 + (: comment start { comment end :) 2 }"/>
      <test attr="{ 1 + (: comment start } comment end :) 2 }"/>
    </result>
  </xsl:template>
</xsl:stylesheet>