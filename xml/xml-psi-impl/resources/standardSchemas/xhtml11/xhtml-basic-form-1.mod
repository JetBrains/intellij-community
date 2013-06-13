
<!-- saved from url=(0042)http://www.rddl.org/xhtml-basic-form-1.mod -->
<HTML><BODY><PRE style="word-wrap: break-word; white-space: pre-wrap;">&lt;!-- ...................................................................... --&gt;
&lt;!-- XHTML Simplified Forms Module  ....................................... --&gt;
&lt;!-- file: xhtml-basic-form-1.mod

     This is XHTML Basic, a proper subset of XHTML.
     Copyright 1998-2000 W3C (MIT, INRIA, Keio), All Rights Reserved.
     Revision: $Id: xhtml-basic-form-1.mod,v 1.10 2000/10/16 21:15:23 radams Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Basic Forms 1.0//EN"  
       SYSTEM "http://www.w3.org/TR/xhtml-modulatization/DTD/xhtml-basic-form-1.mod"

     Revisions:
     (none)
     ....................................................................... --&gt;

&lt;!-- Basic Forms

     This forms module is based on the HTML 3.2 forms model, with
     the WAI-requested addition of the label element. While this 
     module essentially mimics the content model and attributes of 
     HTML 3.2 forms, the element types declared herein also include
     all HTML 4 common attributes.

        form, label, input, select, option, textarea
--&gt;

&lt;!-- declare qualified element type names:
--&gt;
&lt;!ENTITY % form.qname  "form" &gt;
&lt;!ENTITY % label.qname  "label" &gt;
&lt;!ENTITY % input.qname  "input" &gt;
&lt;!ENTITY % select.qname  "select" &gt;
&lt;!ENTITY % option.qname  "option" &gt;
&lt;!ENTITY % textarea.qname  "textarea" &gt;

&lt;!-- %BlkNoForm.mix; includes all non-form block elements,
     plus %Misc.class;
--&gt;
&lt;!ENTITY % BlkNoForm.mix
     "%Heading.class;
      | %List.class;
      | %BlkStruct.class;
      %BlkPhras.class;
      %BlkPres.class;
      | %table.qname; 
      %Block.extra;
      %Misc.class;"
&gt;

&lt;!-- form: Form Element ................................ --&gt;

&lt;!ENTITY % form.element  "INCLUDE" &gt;
&lt;![%form.element;[
&lt;!ENTITY % form.content
     "( %BlkNoForm.mix; )+"
&gt;
&lt;!ELEMENT %form.qname;  %form.content; &gt;
&lt;!-- end of form.element --&gt;]]&gt;

&lt;!ENTITY % form.attlist  "INCLUDE" &gt;
&lt;![%form.attlist;[
&lt;!ATTLIST %form.qname;
      %Common.attrib;
      action       %URI.datatype;           #REQUIRED
      method       ( get | post )           'get'
      enctype      %ContentType.datatype;   'application/x-www-form-urlencoded'
&gt;
&lt;!-- end of form.attlist --&gt;]]&gt;

&lt;!-- label: Form Field Label Text ...................... --&gt;

&lt;!ENTITY % label.element  "INCLUDE" &gt;
&lt;![%label.element;[
&lt;!-- Each label must not contain more than ONE field
--&gt;
&lt;!ENTITY % label.content
     "( #PCDATA 
      | %input.qname; | %select.qname; | %textarea.qname;
      | %InlStruct.class;
      %InlPhras.class;
      %I18n.class;
      %InlPres.class;
      %InlSpecial.class;
      %Misc.class; )*"
&gt;
&lt;!ELEMENT %label.qname;  %label.content; &gt;
&lt;!-- end of label.element --&gt;]]&gt;

&lt;!ENTITY % label.attlist  "INCLUDE" &gt;
&lt;![%label.attlist;[
&lt;!ATTLIST %label.qname;
      %Common.attrib;
      for          IDREF                    #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
&gt;
&lt;!-- end of label.attlist --&gt;]]&gt;

&lt;!-- input: Form Control ............................... --&gt;

&lt;!ENTITY % input.element  "INCLUDE" &gt;
&lt;![%input.element;[
&lt;!ENTITY % input.content  "EMPTY" &gt;
&lt;!ELEMENT %input.qname;  %input.content; &gt;
&lt;!-- end of input.element --&gt;]]&gt;

&lt;!-- Basic Forms removes 'image' and 'file' input types.
--&gt;
&lt;!ENTITY % input.attlist  "INCLUDE" &gt;
&lt;![%input.attlist;[
&lt;!ENTITY % InputType.class
     "( text | password | checkbox | radio 
      | submit | reset | hidden )"
&gt;
&lt;!-- attribute name required for all but submit &amp; reset
--&gt;
&lt;!ATTLIST %input.qname;
      %Common.attrib;
      type         %InputType.class;        'text'
      name         CDATA                    #IMPLIED
      value        CDATA                    #IMPLIED
      checked      ( checked )              #IMPLIED
      size         CDATA                    #IMPLIED
      maxlength    %Number.datatype;        #IMPLIED
      src          %URI.datatype;           #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
&gt;
&lt;!-- end of input.attlist --&gt;]]&gt;

&lt;!-- select: Option Selector ........................... --&gt;

&lt;!ENTITY % select.element  "INCLUDE" &gt;
&lt;![%select.element;[
&lt;!ENTITY % select.content  "( %option.qname; )+" &gt;
&lt;!ELEMENT %select.qname;  %select.content; &gt;
&lt;!-- end of select.element --&gt;]]&gt;

&lt;!ENTITY % select.attlist  "INCLUDE" &gt;
&lt;![%select.attlist;[
&lt;!ATTLIST %select.qname;
      %Common.attrib;
      name         CDATA                    #IMPLIED
      size         %Number.datatype;        #IMPLIED
      multiple     ( multiple )             #IMPLIED
&gt;
&lt;!-- end of select.attlist --&gt;]]&gt;

&lt;!-- option: Selectable Choice ......................... --&gt;

&lt;!ENTITY % option.element  "INCLUDE" &gt;
&lt;![%option.element;[
&lt;!ENTITY % option.content  "( #PCDATA )" &gt;
&lt;!ELEMENT %option.qname;  %option.content; &gt;
&lt;!-- end of option.element --&gt;]]&gt;

&lt;!ENTITY % option.attlist  "INCLUDE" &gt;
&lt;![%option.attlist;[
&lt;!ATTLIST %option.qname;
      %Common.attrib;
      selected     ( selected )             #IMPLIED
      value        CDATA                    #IMPLIED
&gt;
&lt;!-- end of option.attlist --&gt;]]&gt;

&lt;!-- textarea: Multi-Line Text Field ................... --&gt;

&lt;!ENTITY % textarea.element  "INCLUDE" &gt;
&lt;![%textarea.element;[
&lt;!ENTITY % textarea.content  "( #PCDATA )" &gt;
&lt;!ELEMENT %textarea.qname;  %textarea.content; &gt;
&lt;!-- end of textarea.element --&gt;]]&gt;

&lt;!ENTITY % textarea.attlist  "INCLUDE" &gt;
&lt;![%textarea.attlist;[
&lt;!ATTLIST %textarea.qname;
      %Common.attrib;
      name         CDATA                    #IMPLIED
      rows         %Number.datatype;        #REQUIRED
      cols         %Number.datatype;        #REQUIRED
      accesskey    %Character.datatype;     #IMPLIED
&gt;
&lt;!-- end of textarea.attlist --&gt;]]&gt;

&lt;!-- end of xhtml-basic-form-1.mod --&gt;
</PRE></BODY></HTML>