<!-- ....................................................................... -->
<!-- XHTML Basic Table Module  ............................................. -->
<!-- file: xhtml-basic-table-1.mod

     This is XHTML Basic, a proper subset of XHTML.
     Copyright 1998-2005 W3C (MIT, ERCIM, Keio), All Rights Reserved.
     Revision: $Id: xhtml-basic-table-1.mod,v 1.1 2010/07/29 13:42:46 bertails Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Basic Tables 1.0//EN"
       SYSTEM "http://www.w3.org/MarkUp/DTD/xhtml-basic-table-1.mod"

     Revisions:
     (none)
     ....................................................................... -->

<!-- Basic Tables

        table, caption, tr, th, td

     This table module declares elements and attributes defining
     a table model based fundamentally on features found in the
     widely-deployed HTML 3.2 table model.  While this module
     mimics the content model and table attributes of HTML 3.2
     tables, the element types declared herein also includes all
     HTML 4 common and most of the HTML 4 table attributes.
-->

<!-- declare qualified element type names:
-->
<!ENTITY % table.qname  "table" >
<!ENTITY % caption.qname  "caption" >
<!ENTITY % tr.qname  "tr" >
<!ENTITY % th.qname  "th" >
<!ENTITY % td.qname  "td" >

<!-- horizontal alignment attributes for cell contents
-->
<!ENTITY % CellHAlign.attrib
     "align        ( left
                   | center
                   | right )                #IMPLIED"
>

<!-- vertical alignment attributes for cell contents
-->
<!ENTITY % CellVAlign.attrib
     "valign       ( top
                   | middle
                   | bottom )               #IMPLIED"
>

<!-- scope is simpler than axes attribute for common tables
-->
<!ENTITY % scope.attrib
     "scope        ( row | col  )           #IMPLIED"
>

<!-- table: Table Element .............................. -->

<!ENTITY % table.element  "INCLUDE" >
<![%table.element;[
<!ENTITY % table.content
     "( %caption.qname;?, %tr.qname;+ )"
>
<!ELEMENT %table.qname;  %table.content; >
<!-- end of table.element -->]]>

<!ENTITY % table.attlist  "INCLUDE" >
<![%table.attlist;[
<!ATTLIST %table.qname;
      %Common.attrib;
      summary      %Text.datatype;          #IMPLIED
      width        %Length.datatype;        #IMPLIED
>
<!-- end of table.attlist -->]]>

<!-- caption: Table Caption ............................ -->

<!ENTITY % caption.element  "INCLUDE" >
<![%caption.element;[
<!ENTITY % caption.content
     "( #PCDATA | %Inline.mix; )*"
>
<!ELEMENT %caption.qname;  %caption.content; >
<!-- end of caption.element -->]]>

<!ENTITY % caption.attlist  "INCLUDE" >
<![%caption.attlist;[
<!ATTLIST %caption.qname;
      %Common.attrib;
>
<!-- end of caption.attlist -->]]>

<!-- tr: Table Row ..................................... -->

<!ENTITY % tr.element  "INCLUDE" >
<![%tr.element;[
<!ENTITY % tr.content  "( %th.qname; | %td.qname; )+" >
<!ELEMENT %tr.qname;  %tr.content; >
<!-- end of tr.element -->]]>

<!ENTITY % tr.attlist  "INCLUDE" >
<![%tr.attlist;[
<!ATTLIST %tr.qname;
      %Common.attrib;
      %CellHAlign.attrib;
      %CellVAlign.attrib;
>
<!-- end of tr.attlist -->]]>

<!-- th: Table Header Cell ............................. -->

<!-- th is for header cells, td for data,
     but for cells acting as both use td
-->

<!ENTITY % th.element  "INCLUDE" >
<![%th.element;[
<!ENTITY % th.content
     "( #PCDATA | %FlowNoTable.mix; )*"
>
<!ELEMENT %th.qname;  %th.content; >
<!-- end of th.element -->]]>

<!ENTITY % th.attlist  "INCLUDE" >
<![%th.attlist;[
<!ATTLIST %th.qname;
      %Common.attrib;
      abbr         %Text.datatype;          #IMPLIED
      axis         CDATA                    #IMPLIED
      headers      IDREFS                   #IMPLIED
      %scope.attrib;
      rowspan      %Number.datatype;        '1'
      colspan      %Number.datatype;        '1'
      %CellHAlign.attrib;
      %CellVAlign.attrib;
>
<!-- end of th.attlist -->]]>

<!-- td: Table Data Cell ............................... -->

<!ENTITY % td.element  "INCLUDE" >
<![%td.element;[
<!ENTITY % td.content
     "( #PCDATA | %FlowNoTable.mix; )*"
>
<!ELEMENT %td.qname;  %td.content; >
<!-- end of td.element -->]]>

<!ENTITY % td.attlist  "INCLUDE" >
<![%td.attlist;[
<!ATTLIST %td.qname;
      %Common.attrib;
      abbr         %Text.datatype;          #IMPLIED
      axis         CDATA                    #IMPLIED
      headers      IDREFS                   #IMPLIED
      %scope.attrib;
      rowspan      %Number.datatype;        '1'
      colspan      %Number.datatype;        '1'
      %CellHAlign.attrib;
      %CellVAlign.attrib;
>
<!-- end of td.attlist -->]]>

<!-- end of xhtml-basic-table-1.mod -->
