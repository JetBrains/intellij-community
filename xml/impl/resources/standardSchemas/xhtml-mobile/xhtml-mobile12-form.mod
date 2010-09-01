<!--
    FILE INFORMATION

    OMA Permanent Document
       File: OMA-SUP-MOD_xhtml_mobile12-form-V1_2-20080331-A
       Type: Text

    Public Reachable Information
       Path: http://www.openmobilealliance.org/tech/DTD
       Name: xhtml-mobile12-form.mod

    NORMATIVE INFORMATION

    Information about this file can be found in the specification
     OMA-TS-XHTMLMP-V1_2-20080331-A available at
       http://www.openmobilealliance.org/

    Send comments to technical-comments@mail.openmobilealliance.org
	
    LEGAL DISCLAIMER

    Use of this document is subject to all of the terms and conditions
    of the Use Agreement located at
        http://www.openmobilealliance.org/UseAgreement.html

    You may use this document or any part of the document for internal
    or educational purposes only, provided you do not modify, edit or
    take out of context the information in this document in any manner.
    Information contained in this document may be used, at your sole
    risk, for any purposes.

    You may not use this document in any other manner without the prior
    written permission of the Open Mobile Alliance.  The Open Mobile
    Alliance authorizes you to copy this document, provided that you
    retain all copyright and other proprietary notices contained in the
    original materials on any copies of the materials and that you
    comply strictly with these terms.  This copyright permission does
    not constitute an endorsement of the products or services.  The
    Open Mobile Alliance assumes no responsibility for errors or
    omissions in this document.

    Each Open Mobile Alliance member has agreed to use reasonable
    endeavors to inform the Open Mobile Alliance in a timely manner of
    Essential IPR as it becomes aware that the Essential IPR is related
    to the prepared or published specification.  However, the members
    do not have an obligation to conduct IPR searches.  The declared
    Essential IPR is publicly available to members and non-members of
    the Open Mobile Alliance and may be found on the "OMA IPR
    Declarations" list at http://www.openmobilealliance.org/ipr.html.
    The Open Mobile Alliance has not conducted an independent IPR review
    of this document and the information contained herein, and makes no
    representations or warranties regarding third party IPR, including
    without limitation patents, copyrights or trade secret rights.  This
    document may contain inventions for which you must obtain licenses
    from third parties before making, using or selling the inventions.
    Defined terms above are set forth in the schedule to the Open Mobile
    Alliance Application Form.

    NO REPRESENTATIONS OR WARRANTIES (WHETHER EXPRESS OR IMPLIED) ARE
    MADE BY THE OPEN MOBILE ALLIANCE OR ANY OPEN MOBILE ALLIANCE MEMBER
    OR ITS AFFILIATES REGARDING ANY OF THE IPR'S REPRESENTED ON THE "OMA
    IPR DECLARATIONS" LIST, INCLUDING, BUT NOT LIMITED TO THE ACCURACY,
    COMPLETENESS, VALIDITY OR RELEVANCE OF THE INFORMATION OR WHETHER OR     
    NOT SUCH RIGHTS ARE ESSENTIAL OR NON-ESSENTIAL.

    THE OPEN MOBILE ALLIANCE IS NOT LIABLE FOR AND HEREBY DISCLAIMS ANY
    DIRECT, INDIRECT, PUNITIVE, SPECIAL, INCIDENTAL, CONSEQUENTIAL, OR
    EXEMPLARY DAMAGES ARISING OUT OF OR IN CONNECTION WITH THE USE OF
    DOCUMENTS AND THE INFORMATION CONTAINED IN THE DOCUMENTS.

    Copyright 2008 Open Mobile Alliance Ltd.  All Rights Reserved.
    Used with the permission of the Open Mobile Alliance Ltd. under the
    terms set forth above.
-->

<!-- Forms

        form, label, input, select, optgroup, option,
        textarea, fieldset, legend, button

     This module declares markup to provide support for online
     forms, based on the features found in HTML 4 forms.
-->

<!-- declare qualified element type names:
-->
<!ENTITY % form.qname  "form" >
<!ENTITY % label.qname  "label" >
<!ENTITY % input.qname  "input" >
<!ENTITY % select.qname  "select" >
<!ENTITY % optgroup.qname  "optgroup" >
<!ENTITY % option.qname  "option" >
<!ENTITY % textarea.qname  "textarea" >
<!ENTITY % fieldset.qname  "fieldset" >
<!ENTITY % legend.qname  "legend" >
<!ENTITY % button.qname  "button" >

<!-- %BlkNoForm.mix; includes all non-form block elements,
     plus %Misc.class;
-->
<!ENTITY % BlkNoForm.mix
     "%Heading.class;
      | %List.class;
      | %BlkStruct.class;
      %BlkPhras.class;
      %BlkPres.class;
      %Table.class;
      %Block.extra;
      %Misc.class;"
>

<!-- form: Form Element ................................ -->

<!ENTITY % form.element  "INCLUDE" >
<![%form.element;[
<!ENTITY % form.content
     "( %BlkNoForm.mix;
      | %fieldset.qname; )+"
>
<!ELEMENT %form.qname;  %form.content; >
<!-- end of form.element -->]]>

<!ENTITY % form.attlist  "INCLUDE" >
<![%form.attlist;[
<!ATTLIST %form.qname;
      %Common.attrib;
      action       %URI.datatype;           #REQUIRED
      method       ( get | post )           'get'
      enctype      %ContentType.datatype;   'application/x-www-form-urlencoded'
      accept-charset %Charsets.datatype;    #IMPLIED
      accept       %ContentTypes.datatype;  #IMPLIED
>
<!-- end of form.attlist -->]]>

<!-- label: Form Field Label Text ...................... -->

<!-- Each label must not contain more than ONE field
-->

<!ENTITY % label.element  "INCLUDE" >
<![%label.element;[
<!ENTITY % label.content
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
>
<!ELEMENT %label.qname;  %label.content; >
<!-- end of label.element -->]]>

<!ENTITY % label.attlist  "INCLUDE" >
<![%label.attlist;[
<!ATTLIST %label.qname;
      %Common.attrib;
      for          IDREF                    #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
>
<!-- end of label.attlist -->]]>

<!-- input: Form Control ............................... -->

<!ENTITY % input.element  "INCLUDE" >
<![%input.element;[
<!ENTITY % input.content  "EMPTY" >
<!ELEMENT %input.qname;  %input.content; >
<!-- end of input.element -->]]>

<!ENTITY % input.attlist  "INCLUDE" >
<![%input.attlist;[
<!ENTITY % InputType.class
     "( text | password | checkbox | radio | submit
      | reset | file | hidden | image | button )"
>
<!-- attribute 'name' required for all but submit & reset
-->
<!ATTLIST %input.qname;
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
>
<!-- end of input.attlist -->]]>

<!-- select: Option Selector ........................... -->

<!ENTITY % select.element  "INCLUDE" >
<![%select.element;[
<!ENTITY % select.content
     "( %optgroup.qname; | %option.qname; )+"
>
<!ELEMENT %select.qname;  %select.content; >
<!-- end of select.element -->]]>

<!ENTITY % select.attlist  "INCLUDE" >
<![%select.attlist;[
<!ATTLIST %select.qname;
      %Common.attrib;
      name         CDATA                    #IMPLIED
      size         %Number.datatype;        #IMPLIED
      multiple     ( multiple )             #IMPLIED
      disabled     ( disabled )             #IMPLIED
      tabindex     %Number.datatype;        #IMPLIED
>
<!-- end of select.attlist -->]]>

<!-- optgroup: Option Group ............................ -->

<!ENTITY % optgroup.element  "INCLUDE" >
<![%optgroup.element;[
<!ENTITY % optgroup.content  "( %option.qname; )+" >
<!ELEMENT %optgroup.qname;  %optgroup.content; >
<!-- end of optgroup.element -->]]>

<!ENTITY % optgroup.attlist  "INCLUDE" >
<![%optgroup.attlist;[
<!ATTLIST %optgroup.qname;
      %Common.attrib;
      disabled     ( disabled )             #IMPLIED
      label        %Text.datatype;          #REQUIRED
>
<!-- end of optgroup.attlist -->]]>

<!-- option: Selectable Choice ......................... -->

<!ENTITY % option.element  "INCLUDE" >
<![%option.element;[
<!ENTITY % option.content  "( #PCDATA )" >
<!ELEMENT %option.qname;  %option.content; >
<!-- end of option.element -->]]>

<!ENTITY % option.attlist  "INCLUDE" >
<![%option.attlist;[
<!ATTLIST %option.qname;
      %Common.attrib;
      selected     ( selected )             #IMPLIED
      disabled     ( disabled )             #IMPLIED
      label        %Text.datatype;          #IMPLIED
      value        CDATA                    #IMPLIED
>
<!-- end of option.attlist -->]]>

<!-- textarea: Multi-Line Text Field ................... -->

<!ENTITY % textarea.element  "INCLUDE" >
<![%textarea.element;[
<!ENTITY % textarea.content  "( #PCDATA )" >
<!ELEMENT %textarea.qname;  %textarea.content; >
<!-- end of textarea.element -->]]>

<!ENTITY % textarea.attlist  "INCLUDE" >
<![%textarea.attlist;[
<!ATTLIST %textarea.qname;
      %Common.attrib;
      name         CDATA                    #IMPLIED
      rows         %Number.datatype;        #REQUIRED
      cols         %Number.datatype;        #REQUIRED
      disabled     ( disabled )             #IMPLIED
      readonly     ( readonly )             #IMPLIED
      tabindex     %Number.datatype;        #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
      inputmode    CDATA                    #IMPLIED
>
<!-- end of textarea.attlist -->]]>

<!-- fieldset: Form Control Group ...................... -->

<!-- #PCDATA is to solve the mixed content problem,
     per specification only whitespace is allowed
-->

<!ENTITY % fieldset.element  "INCLUDE" >
<![%fieldset.element;[
<!ENTITY % fieldset.content
     "( #PCDATA | %legend.qname; | %Flow.mix; )*"
>
<!ELEMENT %fieldset.qname;  %fieldset.content; >
<!-- end of fieldset.element -->]]>

<!ENTITY % fieldset.attlist  "INCLUDE" >
<![%fieldset.attlist;[
<!ATTLIST %fieldset.qname;
      %Common.attrib;
>
<!-- end of fieldset.attlist -->]]>

<!-- legend: Fieldset Legend ........................... -->

<!ENTITY % legend.element  "INCLUDE" >
<![%legend.element;[
<!ENTITY % legend.content
     "( #PCDATA | %Inline.mix; )*"
>
<!ELEMENT %legend.qname;  %legend.content; >
<!-- end of legend.element -->]]>

<!ENTITY % legend.attlist  "INCLUDE" >
<![%legend.attlist;[
<!ATTLIST %legend.qname;
      %Common.attrib;
      accesskey    %Character.datatype;     #IMPLIED
>
<!-- end of legend.attlist -->]]>

<!-- button: Push Button ............................... -->

<!ENTITY % button.element  "INCLUDE" >
<![%button.element;[
<!ENTITY % button.content
     "( #PCDATA
      | %BlkNoForm.mix;
      | %InlStruct.class;
      %InlPhras.class;
      %InlPres.class;
      %I18n.class;
      %InlSpecial.class;
      %Inline.extra; )*"
>
<!ELEMENT %button.qname;  %button.content; >
<!-- end of button.element -->]]>

<!ENTITY % button.attlist  "INCLUDE" >
<![%button.attlist;[
<!ATTLIST %button.qname;
      %Common.attrib;
      name         CDATA                    #IMPLIED
      value        CDATA                    #IMPLIED
      type         ( button | submit | reset ) 'submit'
      disabled     ( disabled )             #IMPLIED
      tabindex     %Number.datatype;        #IMPLIED
      accesskey    %Character.datatype;     #IMPLIED
>
<!-- end of button.attlist -->]]>

<!-- end of xhtml-form-1.mod -->
