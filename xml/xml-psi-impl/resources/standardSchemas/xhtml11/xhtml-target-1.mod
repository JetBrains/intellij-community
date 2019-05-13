<!-- ...................................................................... -->
<!-- XHTML Target Module  ................................................. -->
<!-- file: xhtml-target-1.mod

     This is XHTML, a reformulation of HTML as a modular XML application.
     Copyright 1998-2005 W3C (MIT, ERCIM, Keio), All Rights Reserved.
     Revision: $Id: xhtml-target-1.mod,v 4.0 2001/04/02 22:42:49 altheim Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Target 1.0//EN"
       SYSTEM "http://www.w3.org/MarkUp/DTD/xhtml-target-1.mod"

     Revisions:
     (none)
     ....................................................................... -->

<!-- Target 

        target

     This module declares the 'target' attribute used for opening windows
-->

<!-- render in this frame --> 
<!ENTITY % FrameTarget.datatype "CDATA" >

<!-- add 'target' attribute to 'a' element -->
<!ATTLIST %a.qname;
      target       %FrameTarget.datatype;   #IMPLIED
>

<!-- add 'target' attribute to 'area' element -->
<!ATTLIST %area.qname;
      target       %FrameTarget.datatype;   #IMPLIED
>

<!-- add 'target' attribute to 'link' element -->
<!ATTLIST %link.qname;
      target       %FrameTarget.datatype;   #IMPLIED
>

<!-- add 'target' attribute to 'form' element -->
<!ATTLIST %form.qname;
      target       %FrameTarget.datatype;   #IMPLIED
>

<!-- add 'target' attribute to 'base' element -->
<!ATTLIST %base.qname;
      target       %FrameTarget.datatype;   #IMPLIED
>

<!-- end of xhtml-target-1.mod -->
