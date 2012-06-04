<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns="http://java.sun.com/xml/ns/j2ee/web-jsptaglibrary_2_0.xsd">

  <xsl:template match="/*">
    <taglib version="2.0">
      <xsl:if test="@version">
        <tlib-version><xsl:value-of select="@version" /></tlib-version>
      </xsl:if>
    </taglib>
  </xsl:template>
</xsl:stylesheet>