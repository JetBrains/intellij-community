<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!--
    * para matches any para element
    * * matches any element
    * chapter|appendix matches any chapter element and any appendix element
    * olist/item matches any item element with an olist parent
    * appendix//para matches any para element with an appendix ancestor element
    * / matches the root node
    * text() matches any text node
    * processing-instruction() matches any processing instruction
    * node() matches any node other than an attribute node and the root node
    * id("W11") matches the element with unique ID W11
    * para[1] matches any para element that is the first para child element of its parent
    * *[position()=1 and self::para] matches any para element that is the first child element of its parent
    * para[last()=1] matches any para element that is the only para child element of its parent
    * items/item[position()>1] matches any item element that has a items parent and that is not the first item child of its parent
    * item[position() mod 2 = 1] would be true for any item element that is an odd-numbered item child of its parent.
    * div[@class="appendix"]//p matches any p element with a div ancestor element that has a class attribute with value appendix
    * @class matches any class attribute (not any element that has a class attribute)
    * @* matches any attribute
  -->
  <xsl:template match="para" />
  <xsl:template match="*" />
  <xsl:template match="chapter|appendix" />
  <xsl:template match="olist/item" />
  <xsl:template match="appendix//para" />
  <xsl:template match="/" />
  <xsl:template match="text()" />
  <xsl:template match="processing-instruction()" />
  <xsl:template match="node()" />
  <xsl:template match="id('W11')" />
  <xsl:template match="id('W11')/a" />
  <xsl:template match="key('W11', 'x')/a" />
  <xsl:template match="para[1]" />
  <xsl:template match="*[position()=1 and self::para]" />
  <xsl:template match="items/item[position()>1]" />
  <xsl:template match="item[position() mod 2 = 1]" />
  <xsl:template match="div[@class='appendix']//p" />
  <xsl:template match="@class" />
  <xsl:template match="@*" />
  <xsl:template match="child::*" />
  <xsl:template match="attribute::*" />

</xsl:stylesheet>