<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="text" omit-xml-declaration="yes" encoding="UTF-8"/>

    <xsl:template match="/agent-definition/agent[@name='CustomAppsNexus']/properties/group[@id='nexusConfig']">
        <xsl:apply-templates select="string|integer|boolean"/>
    </xsl:template>

    <xsl:template match="string">
        <xsl:a<caret>ttribute select="@id"/><xsl:text></xsl:text>
    </xsl:template>

</xsl:stylesheet>