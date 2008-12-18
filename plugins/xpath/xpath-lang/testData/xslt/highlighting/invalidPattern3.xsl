<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="x" />

  <xsl:template match='<error descr="Bad pattern">parent::foo</error>' />
</xsl:stylesheet>