<?xml version="1.0" encoding="UTF-8"?>
<!--
    This is a syntactically valid (but useless) XSLT 2 stylesheet to test IDEA's XSLT 2 support.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0"
    xmlns:myfn="<error>http://surguy.net/namespaces/myfunctions</error>"
    exclude-result-prefixes="#all">

    <xsl:character-map name="test">
        <xsl:output-character character="a" string="b"/>
    </xsl:character-map>

    <xsl:output byte-order-mark="no" use-character-maps="test" />

    <xsl:template match="/" mode="#all">
        This is a dummy template
        <xsl:apply-templates select="* except IgnoreMe" mode="#current">
            <xsl:sort/>
        </xsl:apply-templates>
    </xsl:template>

    <xsl:template match="text()">
        <xsl:analyze-string select="." regex="[a-z]">
            <xsl:matching-substring>Found something</xsl:matching-substring>
            <xsl:non-matching-substring>Found nothing</xsl:non-matching-substring>
        </xsl:analyze-string>
    </xsl:template>

    <xsl:function name="myfn:test">
        <xsl:param name="current"></xsl:param>
    </xsl:function>



</xsl:stylesheet>
