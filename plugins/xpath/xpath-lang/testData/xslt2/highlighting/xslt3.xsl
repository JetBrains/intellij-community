<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <xsl:variable name="foo" select="foo"/>
        <xsl:variable name="<warning descr="Variable 'bar' is never used">bar</warning>" select="concat($foo, '/', $<error descr="Unresolved variable 'unknown'">unknown</error>)"/>
    </xsl:template>
</xsl:stylesheet>