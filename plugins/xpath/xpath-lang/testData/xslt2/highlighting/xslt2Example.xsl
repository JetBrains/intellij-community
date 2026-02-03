<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:my="urn:my"
    version="2.0"
    exclude-result-prefixes="#all">

  <xsl:output indent="yes" />

  <!--
      the input, just in a variable
      ready to use, easy for testing, no need
      for exslt:node-set()
  -->
  <xsl:variable name="preferences">
    <james-johnsson>Saxon, c, xslt 2</james-johnsson>
    <george-williams-geraldson>xsltproc, nc, xslt 1</george-williams-geraldson>
    <super-troopers>xsltproc, nc, xslt 1</super-troopers>
    <merry-mirriams>libxslt, nc, xslt 1</merry-mirriams>
    <john-ronald-reuel-tolkien>saxon, c, xslt 2</john-ronald-reuel-tolkien>
    <sir-tomald-richards>gestAlt, nc, XSLT 2</sir-tomald-richards>
    <agatha-kirsten>saxon, c, xslt 2</agatha-kirsten>
    <mollie-jollie>saxon, c, xslt 2</mollie-jollie>
  </xsl:variable>

  <xsl:template match="/" name="main">
    <xsl:variable name="micro-pipeline">
      <xsl:apply-templates select="$preferences/*" />
    </xsl:variable>

    <!-- group by processor -->
    <xsl:for-each-group
        select="$micro-pipeline/processor"
        group-by="token[1]/upper-case(text())">

      <processor name="{my:camel-case(token[1])}" >
        <xsl:apply-templates select="token[position() = 2 to 3]" />
        <users>
          <!--
              join the users in one string
              and camel case their names
          -->
          <xsl:value-of select="
                       string-join(
                       my:camel-case(current-group()/user)
                       , ', ')" />
        </users>
      </processor>
    </xsl:for-each-group>

  </xsl:template>

  <!--
      matches for $preferences nodes
  -->
  <xsl:template match="*" priority="0">
    <processor>
      <xsl:next-match />
    </processor>
  </xsl:template>

  <xsl:template match="*">
    <user><xsl:value-of select="local-name(.)" /></user>
    <xsl:next-match />
  </xsl:template>


  <xsl:template match="text()">
    <xsl:for-each select="tokenize(., ',')">
      <token><xsl:value-of select="normalize-space(.)" /></token>
    </xsl:for-each>
  </xsl:template>


  <!--
      what follows: matches for micro pipeline
      all matches are case-insensitive, with no
      need for translate() and trouble with more complex
      characters
  -->
  <xsl:template match="token[matches(., '^c$', 'i')]">
    <level>processor is compliant</level>
  </xsl:template>

  <xsl:template match="token[matches(., '^nc$', 'i')]">
    <level>processor is non-compliant</level>
  </xsl:template>

  <xsl:template match="token[matches(., '^xslt', 'i')]">
    <language><xsl:value-of select="upper-case(.)" /></language>
  </xsl:template>

  <!--
      put the nasty bit aside in a function
      it camel-cases a dashed or space delimited string
  -->
  <xsl:function name="my:camel-case" as="xs:string*">
    <xsl:param name="string" as="xs:string*"/>
    <xsl:sequence select="for $s in $string
           return string-join(
               for $word in tokenize($s, '-| ')
               return
                   concat(
                       upper-case(substring($word, 1, 1)),
                       substring($word, 2))
               , ' ')" />
  </xsl:function>
</xsl:stylesheet>
