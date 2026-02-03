<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
      <a>
          <b>
              <c>
                  <xsl:if test="something">
                      <d>
                          <e></e>
                      </d>
                  </xsl:if>
              </c>
          </b>
      </a>
  </xsl:template>
</xsl:stylesheet>

