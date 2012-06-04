<!-- ...................................................................... -->
<!-- XHTML Inputmode Module  .............................................. -->
<!-- file: xhtml-inputmode-1.mod

     This is XHTML, a reformulation of HTML as a modular XML application.
     Copyright 1998-2005 W3C (MIT, ERCIM, Keio), All Rights Reserved.
     Revision: $Id: xhtml-inputmode-1.mod,v 1.1 2006/07/05 16:29:09 matthieu Exp $

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Inputmode 1.0//EN"
       SYSTEM "xhtml-inputmode-1.mod"

     Revisions:
     (none)
     ....................................................................... -->

<!-- Inputmode 

        inputmode

     This module declares the 'inputmode' attribute used for suggesting the
     input mode associated with an input or textarea element.
-->

<!-- render in this frame --> 
<!ENTITY % Inputmode.datatype "CDATA" >

<!-- add 'inputmode' attribute to 'input' element -->
<!ATTLIST %input.qname;
      inputmode       %Inputmode.datatype;   #IMPLIED
>

<!-- add 'inputmode' attribute to 'textarea' element -->
<!ATTLIST %textarea.qname;
      inputmode       %Inputmode.datatype;   #IMPLIED
>

<!-- end of xhtml-inputmode-1.mod -->
