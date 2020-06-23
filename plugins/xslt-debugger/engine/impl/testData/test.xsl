<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:variable name="foo" select="ffff"/>
    <xsl:variable name="bar" select="$foo"/>
    <xsl:template match="/">
        <xsl:variable name="eee" select="$bar"/>
        <xsl:variable name="eees" select="$eee"/>
        <foo/>
    </xsl:template>
</xsl:stylesheet>