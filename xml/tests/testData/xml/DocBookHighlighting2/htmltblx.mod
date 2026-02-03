<!-- ...................................................................... -->
<!-- DocBook XML HTML Table Module V4.5 ................................... -->
<!-- File htmltblx.mod .................................................... -->

<!-- Copyright 2003-2006 ArborText, Inc., Norman Walsh, Sun Microsystems,
     Inc., and the Organization for the Advancement of Structured Information
     Standards (OASIS).

     $Id: htmltblx.mod 6340 2006-10-03 13:23:24Z nwalsh $

     Permission to use, copy, modify and distribute the DocBook XML DTD
     and its accompanying documentation for any purpose and without fee
     is hereby granted in perpetuity, provided that the above copyright
     notice and this paragraph appear in all copies.  The copyright
     holders make no representation about the suitability of the DTD for
     any purpose.  It is provided "as is" without expressed or implied
     warranty.

     If you modify the DocBook XML DTD in any way, except for declaring and
     referencing additional sets of general entities and declaring
     additional notations, label your DTD as a variant of DocBook.  See
     the maintenance documentation for more information.

     Please direct all questions, bug reports, or suggestions for
     changes to the docbook@lists.oasis-open.org mailing list. For more
     information, see http://www.oasis-open.org/docbook/.
-->

<!-- ...................................................................... -->

<!-- This module contains the definitions for elements that are
     isomorphic to the HTML elements. One could argue we should
     instead have based ourselves on the XHTML Table Module, but the
     HTML one is more like what browsers are likely to accept today
     and users are likely to use.

     This module has been developed for use with the DocBook V4.5
     "union table model" in which elements and attlists common to both
     models are defined (as the union) in the CALS table module by
     setting various parameter entities appropriately in this file.

     In DTD driver files referring to this module, please use an entity
     declaration that uses the public identifier shown below:

     <!ENTITY % htmltbl PUBLIC
     "-//OASIS//ELEMENTS DocBook XML HTML Tables V4.5//EN"
     "htmltblx.mod">
     %htmltbl;

     See the documentation for detailed information on the parameter
     entity and module scheme used in DocBook, customizing DocBook and
     planning for interchange, and changes made since the last release
     of DocBook.
-->

<!--======================= XHTML Tables =======================================-->

<!ENTITY % html.coreattrs
 "%common.attrib;
  class       CDATA          #IMPLIED
  style       CDATA          #IMPLIED
  title       CDATA         #IMPLIED"
  >

<!-- Does not contain lang or dir because they are in %common.attribs -->
<![%sgml.features;[
<!ENTITY % i18n "">
]]>
<!ENTITY % i18n
 "xml:lang    NMTOKEN        #IMPLIED"
  >

<!ENTITY % events
 "onclick     CDATA       #IMPLIED
  ondblclick  CDATA       #IMPLIED
  onmousedown CDATA       #IMPLIED
  onmouseup   CDATA       #IMPLIED
  onmouseover CDATA       #IMPLIED
  onmousemove CDATA       #IMPLIED
  onmouseout  CDATA       #IMPLIED
  onkeypress  CDATA       #IMPLIED
  onkeydown   CDATA       #IMPLIED
  onkeyup     CDATA       #IMPLIED"
  >

<!ENTITY % attrs "%html.coreattrs; %i18n; %events;">

<!ENTITY % cellhalign
  "align      (left|center|right|justify|char) #IMPLIED
   char       CDATA    #IMPLIED
   charoff    CDATA       #IMPLIED"
  >

<!ENTITY % cellvalign
  "valign     (top|middle|bottom|baseline) #IMPLIED"
  >

<!--doc:A group of columns in an HTML table.-->
<!ELEMENT colgroup %ho; (col)*>
<!--doc:Specifications for a column in an HTML table.-->
<!ELEMENT col %ho; EMPTY>
<!--doc:A row in an HTML table.-->
<!ELEMENT tr %ho;  (th|td)+>
<!--doc:A table header entry in an HTML table.-->
<!ELEMENT th %ho;  (%para.char.mix; | %tabentry.mix; | table | informaltable)*>
<!--doc:A table ntry in an HTML table.-->
<!ELEMENT td %ho;  (%para.char.mix; | %tabentry.mix; | table | informaltable)*>

<!ATTLIST colgroup
  %attrs;
  span        CDATA       "1"
  width       CDATA  #IMPLIED
  %cellhalign;
  %cellvalign;
  >

<!ATTLIST col
  %attrs;
  span        CDATA       "1"
  width       CDATA  #IMPLIED
  %cellhalign;
  %cellvalign;
  >

<!ATTLIST tr
  %attrs;
  %cellhalign;
  %cellvalign;
  bgcolor     CDATA        #IMPLIED
  >

<!ATTLIST th
  %attrs;
  abbr        CDATA         #IMPLIED
  axis        CDATA          #IMPLIED
  headers     IDREFS         #IMPLIED
  scope       (row|col|rowgroup|colgroup)   #IMPLIED
  rowspan     CDATA       "1"
  colspan     CDATA       "1"
  %cellhalign;
  %cellvalign;
  nowrap      (nowrap)       #IMPLIED
  bgcolor     CDATA         #IMPLIED
  width       CDATA       #IMPLIED
  height      CDATA       #IMPLIED
  >

<!ATTLIST td
  %attrs;
  abbr        CDATA         #IMPLIED
  axis        CDATA          #IMPLIED
  headers     IDREFS         #IMPLIED
  scope       (row|col|rowgroup|colgroup)   #IMPLIED
  rowspan     CDATA       "1"
  colspan     CDATA       "1"
  %cellhalign;
  %cellvalign;
  nowrap      (nowrap)       #IMPLIED
  bgcolor     CDATA         #IMPLIED
  width       CDATA       #IMPLIED
  height      CDATA       #IMPLIED
  >

<!-- ====================================================== -->
<!--        Set up to read in the CALS model configured to
            merge with the XHTML table model                -->
<!-- ====================================================== -->

<!ENTITY % tables.role.attrib "%role.attrib;">

<!-- Add label and role attributes to table and informaltable -->
<!ENTITY % bodyatt "
		floatstyle	CDATA			#IMPLIED
		rowheader	(firstcol|norowheader)	#IMPLIED
                %label.attrib;"
>

<!-- Add common attributes to Table, TGroup, TBody, THead, TFoot, Row, 
     EntryTbl, and Entry (and InformalTable element). -->

<!ENTITY % secur "
	%common.attrib;
	class       CDATA          #IMPLIED
	style       CDATA          #IMPLIED
	title       CDATA         #IMPLIED
	%i18n;
	%events;
	%tables.role.attrib;">

<!ENTITY % common.table.attribs
	"%bodyatt;
	%secur;">

<!-- Content model for Table (that also allows HTML tables) -->
<!ENTITY % tbl.table.mdl
	"((blockinfo?,
           (%formalobject.title.content;),
           (%ndxterm.class;)*,
           textobject*,
           (graphic+|mediaobject+|tgroup+))
         |(caption, (col*|colgroup*), thead?, tfoot?, (tbody+|tr+)))">

<!ENTITY % informal.tbl.table.mdl
	"(textobject*,
          (graphic+|mediaobject+|tgroup+))
         | ((col*|colgroup*), thead?, tfoot?, (tbody+|tr+))">

<!-- Attributes for Table (including HTML ones) -->

<!-- N.B. rules = (none | groups | rows | cols | all) but it can't be spec'd -->
<!-- that way because 'all' already occurs in a different enumeration in -->
<!-- CALS tables (frame). -->

<!ENTITY % tbl.table.att        '
    tabstyle    CDATA           #IMPLIED
    tocentry    %yesorno.attvals;       #IMPLIED
    shortentry  %yesorno.attvals;       #IMPLIED
    orient      (port|land)     #IMPLIED
    pgwide      %yesorno.attvals;       #IMPLIED 
    summary     CDATA          #IMPLIED
    width       CDATA        #IMPLIED
    border      CDATA        #IMPLIED
    rules       CDATA		#IMPLIED
    cellspacing CDATA        #IMPLIED
    cellpadding CDATA        #IMPLIED
    align       (left|center|right)   #IMPLIED
    bgcolor     CDATA         #IMPLIED
'>

<!ENTITY % tbl.frame.attval "void|above|below|hsides|lhs|rhs|vsides|box|border|
top|bottom|topbot|all|sides|none">

<!-- Allow either objects or inlines; beware of REs between elements. -->
<!ENTITY % tbl.entry.mdl "%para.char.mix; | %tabentry.mix;">

<!-- thead, tfoot, and tbody are defined in both table models,
     so we set up parameter entities to define union models for them
 -->

<!ENTITY % tbl.hdft.mdl        "(tr+|(colspec*,row+))">
<!ENTITY % tbl.tbody.mdl       "(tr+|row+)">
<!ENTITY % tbl.valign.attval   "top|middle|bottom|baseline">

<!-- End of DocBook XML HTML Table Module V4.5 ............................ -->
<!-- ...................................................................... -->
