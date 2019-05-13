
<!-- saved from url=(0096)http://dev.w3.org/2007/mobileok-ref/dtd/www.openmobilealliance.org/tech/DTD/xhtmlmp12-form-1.mod -->
<HTML><BODY><PRE style="word-wrap: break-word; white-space: pre-wrap;">&lt;!-- ...................................................................... --&gt;
&lt;!-- XHTMLMP1.2 Forms Module  .................................................. --&gt;
&lt;!-- file: xhtmlmp12-form-1.mod

     This is a xhtmlmp12-form-1.mod with inputmode attribute.

     (C) 2006 Open Mobile Alliance Ltd.  All Rights Reserved.	

     LEGAL DISCLAIMER

     Use of this document is subject to all of the terms and conditions
     of the Use Agreement located at
	http://www.openmobilealliance.org/UseAgreement.html

    NO REPRESENTATIONS OR WARRANTIES (WHETHER EXPRESS OR IMPLIED) ARE
    MADE BY THE OPEN MOBILE ALLIANCE OR ANY OPEN MOBILE ALLIANCE MEMBER
    OR ITS AFFILIATES REGARDING ANY OF THE IPRS REPRESENTED ON THE OMA
    IPR DECLARATIONS LIST, INCLUDING, BUT NOT LIMITED TO THE ACCURACY,
    COMPLETENESS, VALIDITY OR RELEVANCE OF THE INFORMATION OR WHETHER OR
    NOT SUCH RIGHTS ARE ESSENTIAL OR NON-ESSENTIAL.

    THE OPEN MOBILE ALLIANCE IS NOT LIABLE FOR AND HEREBY DISCLAIMS ANY
    DIRECT, INDIRECT, PUNITIVE, SPECIAL, INCIDENTAL, CONSEQUENTIAL, OR
    EXEMPLARY DAMAGES ARISING OUT OF OR IN CONNECTION WITH THE USE OF
    DOCUMENTS AND THE INFORMATION CONTAINED IN THE DOCUMENTS.
     ....................................................................... --&gt;

&lt;!-- Forms

        form, label, input, select, optgroup, option,
        textarea, fieldset, legend, button

     This module declares markup to provide support for online
     forms, based on the features found in HTML 4 forms.
--&gt;

&lt;!-- declare qualified element type names:
--&gt;
&lt;!ENTITY % form.qname  "form" &gt;
&lt;!ENTITY % label.qname  "label" &gt;
&lt;!ENTITY % input.qname  "input" &gt;
&lt;!ENTITY % select.qname  "select" &gt;
&lt;!ENTITY % optgroup.qname  "optgroup" &gt;
&lt;!ENTITY % option.qname  "option" &gt;
&lt;!ENTITY % textarea.qname  "textarea" &gt;
&lt;!ENTITY % fieldset.qname  "fieldset" &gt;
&lt;!ENTITY % legend.qname  "legend" &gt;
&lt;!ENTITY % button.qname  "button" &gt;

&lt;!-- %BlkNoForm.mix; includes all non-form block elements,
     plus %Misc.class;
--&gt;
&lt;!ENTITY % BlkNoForm.mix
     "%Heading.class;
      | %List.class;
      | %BlkStruct.class;
      %BlkPhras.class;
      %BlkPres.class;
      %Table.class;
      %Block.extra;
      %Misc.class;"
&gt;

&lt;!-- form: Form Element ................................ --&gt;

&lt;!ENTITY % form.element  "INCLUDE" &gt;
&lt;![%form.element;[
&lt;!ENTITY % form.content
     "( %BlkNoForm.mix;
      | %fieldset.qname; )+"
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
      accept-charset %Charsets.datatype;    #IMPLIED
      accept       %ContentTypes.datatype;  #IMPLIED
&gt;
&lt;!-- end of form.attlist --&gt;]]&gt;

&lt;!-- label: Form Field Label Text ...................... --&gt;

&lt;!-- Each label must not contain more than ONE field
--&gt;

&lt;!ENTITY % label.element  "INCLUDE" &gt;
&lt;![%label.element;[
&lt;!ENTITY % label.content
     "( #PCDATA
      | %input.qname; | %select.qname; | %textarea.qname; | %button.qname;
      | %InlStruct.class;
      %InlPhras.class;
      %I18n.class;
      %InlPres.class;
      %Anchor.class;
      %InlSpecial.class;
      %Inline.extra;
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

&lt;!ENTITY % input.attlist  "INCLUDE" &gt;
&lt;![%input.attlist;[
&lt;!ENTITY % InputType.class
     "( text | password | checkbox | radio | submit
      | reset | file | hidden | image | button )"
&gt;
&lt;!-- attribute 'name' required for all but submit &amp; reset
--&gt;
&lt;!ATTLIST %input.qname;
      %Common.attrib;
      type         %InputType.class;        'text'
      name         CDATA                    #IMPLIED
      value        CDATA                    #IMPLIED
      checked      ( checked )              #IMPLIED
      disabled     ( disabled )             #IMPLIED
      readonly     ( readonly )             #IMPLIED
      size         %Number.datatype;        #IMPLIED
      maxlength    %Number.datatype;        #IMPLIED
      src          %URI.datatype;           #IMPLIED
      alt          %Text.datatype;          #IMPLIED
      tabindex     %Number.datatype;        #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
      accept       %ContentTypes.datatype;  #IMPLIED
      inputmode    CDATA                    #IMPLIED
&gt;
&lt;!-- end of input.attlist --&gt;]]&gt;

&lt;!-- select: Option Selector ........................... --&gt;

&lt;!ENTITY % select.element  "INCLUDE" &gt;
&lt;![%select.element;[
&lt;!ENTITY % select.content
     "( %optgroup.qname; | %option.qname; )+"
&gt;
&lt;!ELEMENT %select.qname;  %select.content; &gt;
&lt;!-- end of select.element --&gt;]]&gt;

&lt;!ENTITY % select.attlist  "INCLUDE" &gt;
&lt;![%select.attlist;[
&lt;!ATTLIST %select.qname;
      %Common.attrib;
      name         CDATA                    #IMPLIED
      size         %Number.datatype;        #IMPLIED
      multiple     ( multiple )             #IMPLIED
      disabled     ( disabled )             #IMPLIED
      tabindex     %Number.datatype;        #IMPLIED
&gt;
&lt;!-- end of select.attlist --&gt;]]&gt;

&lt;!-- optgroup: Option Group ............................ --&gt;

&lt;!ENTITY % optgroup.element  "INCLUDE" &gt;
&lt;![%optgroup.element;[
&lt;!ENTITY % optgroup.content  "( %option.qname; )+" &gt;
&lt;!ELEMENT %optgroup.qname;  %optgroup.content; &gt;
&lt;!-- end of optgroup.element --&gt;]]&gt;

&lt;!ENTITY % optgroup.attlist  "INCLUDE" &gt;
&lt;![%optgroup.attlist;[
&lt;!ATTLIST %optgroup.qname;
      %Common.attrib;
      disabled     ( disabled )             #IMPLIED
      label        %Text.datatype;          #REQUIRED
&gt;
&lt;!-- end of optgroup.attlist --&gt;]]&gt;

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
      disabled     ( disabled )             #IMPLIED
      label        %Text.datatype;          #IMPLIED
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
      disabled     ( disabled )             #IMPLIED
      readonly     ( readonly )             #IMPLIED
      tabindex     %Number.datatype;        #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
      inputmode    CDATA                    #IMPLIED
&gt;
&lt;!-- end of textarea.attlist --&gt;]]&gt;

&lt;!-- fieldset: Form Control Group ...................... --&gt;

&lt;!-- #PCDATA is to solve the mixed content problem,
     per specification only whitespace is allowed
--&gt;

&lt;!ENTITY % fieldset.element  "INCLUDE" &gt;
&lt;![%fieldset.element;[
&lt;!ENTITY % fieldset.content
     "( #PCDATA | %legend.qname; | %Flow.mix; )*"
&gt;
&lt;!ELEMENT %fieldset.qname;  %fieldset.content; &gt;
&lt;!-- end of fieldset.element --&gt;]]&gt;

&lt;!ENTITY % fieldset.attlist  "INCLUDE" &gt;
&lt;![%fieldset.attlist;[
&lt;!ATTLIST %fieldset.qname;
      %Common.attrib;
&gt;
&lt;!-- end of fieldset.attlist --&gt;]]&gt;

&lt;!-- legend: Fieldset Legend ........................... --&gt;

&lt;!ENTITY % legend.element  "INCLUDE" &gt;
&lt;![%legend.element;[
&lt;!ENTITY % legend.content
     "( #PCDATA | %Inline.mix; )*"
&gt;
&lt;!ELEMENT %legend.qname;  %legend.content; &gt;
&lt;!-- end of legend.element --&gt;]]&gt;

&lt;!ENTITY % legend.attlist  "INCLUDE" &gt;
&lt;![%legend.attlist;[
&lt;!ATTLIST %legend.qname;
      %Common.attrib;
      accesskey    %Character.datatype;     #IMPLIED
&gt;
&lt;!-- end of legend.attlist --&gt;]]&gt;

&lt;!-- button: Push Button ............................... --&gt;

&lt;!ENTITY % button.element  "INCLUDE" &gt;
&lt;![%button.element;[
&lt;!ENTITY % button.content
     "( #PCDATA
      | %BlkNoForm.mix;
      | %InlStruct.class;
      %InlPhras.class;
      %InlPres.class;
      %I18n.class;
      %InlSpecial.class;
      %Inline.extra; )*"
&gt;
&lt;!ELEMENT %button.qname;  %button.content; &gt;
&lt;!-- end of button.element --&gt;]]&gt;

&lt;!ENTITY % button.attlist  "INCLUDE" &gt;
&lt;![%button.attlist;[
&lt;!ATTLIST %button.qname;
      %Common.attrib;
      name         CDATA                    #IMPLIED
      value        CDATA                    #IMPLIED
      type         ( button | submit | reset ) 'submit'
      disabled     ( disabled )             #IMPLIED
      tabindex     %Number.datatype;        #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
&gt;
&lt;!-- end of button.attlist --&gt;]]&gt;

&lt;!-- end of xhtml-form-1.mod --&gt;
</PRE></BODY></HTML>