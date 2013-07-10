<!--
    FILE INFORMATION

    OMA Permanent Document
       File: OMA-SUP-MOD_xhtml_mobile12-model-1-V1_2-20080331-A
       Type: Text

    Public Reachable Information
       Path: http://www.openmobilealliance.org/tech/DTD
       Name: xhtml-mobile12-model-1.mod

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
<!-- XHTML Mobile Document Model

     This module describes the groupings of elements that make up
     common content models for XHTML elements.
-->

<!-- Optional Elements in head  .............. -->

<!ENTITY % HeadOpts.mix

     "( %script.qname; | %meta.qname; | %link.qname; | %object.qname; | %style.qname;
     )*" >


<!-- Miscellaneous Elements  ................. -->

<!ENTITY % Script.class "| %script.qname; | %noscript.qname;" >

<!ENTITY % Misc.extra "" >

<!ENTITY % Misc.class
      "%Script.class;
      %Misc.extra;"
>

<!-- Inline Elements  ........................ -->

<!ENTITY % InlStruct.class "%br.qname; | %span.qname;" >

<!ENTITY % InlPhras.class
     "| %em.qname; | %strong.qname; | %dfn.qname; | %code.qname;
      | %samp.qname; | %kbd.qname; | %var.qname; | %cite.qname;
      | %abbr.qname; | %acronym.qname; | %q.qname;" >

<!ENTITY % InlPres.class
     "| %i.qname; | %b.qname; | %big.qname; | %small.qname; " >


<!ENTITY % I18n.class "" >

<!ENTITY % Anchor.class "| %a.qname;" >

<!ENTITY % InlSpecial.class "| %img.qname; | %object.qname;" >

<!ENTITY % InlForm.class
     "| %input.qname; | %select.qname; | %textarea.qname;
      | %label.qname; | %button.qname;"
>
<!-- yam added button.qname 060612 -->

<!ENTITY % Inline.extra "" >

<!ENTITY % Inline.class
     "%InlStruct.class;
      %InlPhras.class;
      %InlPres.class;
      %Anchor.class;
      %InlSpecial.class;
      %InlForm.class;
      %Inline.extra;"
>

<!ENTITY % InlNoAnchor.class
     "%InlStruct.class;
      %InlPhras.class;
      %InlPres.class;      
      %InlSpecial.class;
      %InlForm.class;
      %Inline.extra;"
>

<!ENTITY % InlNoAnchor.mix
     "%InlNoAnchor.class;
      %Misc.class;"
>

<!ENTITY % Inline.mix
     "%Inline.class;
      %Misc.class;"
>

<!-- Block Elements  ......................... -->

<!ENTITY % Heading.class
     "%h1.qname; | %h2.qname; | %h3.qname;
      | %h4.qname; | %h5.qname; | %h6.qname;"
>
<!ENTITY % List.class  "%ul.qname; | %ol.qname; | %dl.qname;" >

<!ENTITY % Table.class "| %table.qname;" >

<!ENTITY % Form.class  "| %form.qname;" >

<!ENTITY % Fieldset.class  "| %fieldset.qname;" >

<!ENTITY % BlkStruct.class "%p.qname; | %div.qname;" >

<!ENTITY % BlkPhras.class
     "| %pre.qname; | %blockquote.qname; | %address.qname;"
>

<!ENTITY % BlkPres.class "| %hr.qname;" >

<!ENTITY % BlkSpecial.class
     "%Table.class;
      %Form.class;
      %Fieldset.class;"
>

<!ENTITY % Block.extra "" >

<!ENTITY % Block.class
     "%BlkStruct.class;
      %BlkPhras.class;
      %BlkPres.class;
      %BlkSpecial.class;
      %Block.extra;"
>

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

<!ENTITY % Block.mix
     "%Heading.class;
      | %List.class;
      | %Block.class;
      %Misc.class;"
>

<!-- All Content Elements  ................... -->

<!-- declares all content except tables
-->
<!ENTITY % FlowNoTable.mix
     "%Heading.class;
      | %List.class;
      | %BlkStruct.class;
      %BlkPhras.class;
      %Form.class;
      %Block.extra;
      | %Inline.class;
      %Misc.class;"
>


<!ENTITY % Flow.mix
     "%Heading.class;
      | %List.class;
      | %Block.class;
      | %Inline.class;
      %Misc.class;"
>

<!-- end of xhtml-mobile12-model-1.mod -->

