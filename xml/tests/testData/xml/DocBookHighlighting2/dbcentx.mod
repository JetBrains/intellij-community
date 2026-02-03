<!-- ...................................................................... -->
<!-- DocBook character entities module V4.5 ............................... -->
<!-- File dbcentx.mod ..................................................... -->

<!-- Copyright 1992-2004 HaL Computer Systems, Inc.,
     O'Reilly & Associates, Inc., ArborText, Inc., Fujitsu Software
     Corporation, Norman Walsh, Sun Microsystems, Inc., and the
     Organization for the Advancement of Structured Information
     Standards (OASIS).

     $Id: dbcentx.mod 6340 2006-10-03 13:23:24Z nwalsh $

     Permission to use, copy, modify and distribute the DocBook DTD
     and its accompanying documentation for any purpose and without fee
     is hereby granted in perpetuity, provided that the above copyright
     notice and this paragraph appear in all copies.  The copyright
     holders make no representation about the suitability of the DTD for
     any purpose.  It is provided "as is" without expressed or implied
     warranty.

     If you modify the DocBook DTD in any way, except for declaring and
     referencing additional sets of general entities and declaring
     additional notations, label your DTD as a variant of DocBook.  See
     the maintenance documentation for more information.

     Please direct all questions, bug reports, or suggestions for
     changes to the docbook@lists.oasis-open.org mailing list. For more
     information, see http://www.oasis-open.org/docbook/.
-->

<!-- ...................................................................... -->

<!-- This module contains the entity declarations for the standard ISO
     entity sets used by DocBook.

     In DTD driver files referring to this module, please use an entity
     declaration that uses the public identifier shown below:

     <!ENTITY % dbcent PUBLIC
     "-//OASIS//ENTITIES DocBook Character Entities V4.5//EN"
     "dbcentx.mod">
     %dbcent;

     See the documentation for detailed information on the parameter
     entity and module scheme used in DocBook, customizing DocBook and
     planning for interchange, and changes made since the last release
     of DocBook.
-->

<!-- ...................................................................... -->

<![%sgml.features;[

<!ENTITY % ISOamsa.module "INCLUDE">
<![ %ISOamsa.module; [
<!ENTITY % ISOamsa PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Arrow Relations//EN">
<!--end of ISOamsa.module-->]]>

<!ENTITY % ISOamsb.module "INCLUDE">
<![ %ISOamsb.module; [
<!ENTITY % ISOamsb PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Binary Operators//EN">
<!--end of ISOamsb.module-->]]>

<!ENTITY % ISOamsc.module "INCLUDE">
<![ %ISOamsc.module; [
<!ENTITY % ISOamsc PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Delimiters//EN">
<!--end of ISOamsc.module-->]]>

<!ENTITY % ISOamsn.module "INCLUDE">
<![ %ISOamsn.module; [
<!ENTITY % ISOamsn PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Negated Relations//EN">
<!--end of ISOamsn.module-->]]>

<!ENTITY % ISOamso.module "INCLUDE">
<![ %ISOamso.module; [
<!ENTITY % ISOamso PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Ordinary//EN">
<!--end of ISOamso.module-->]]>

<!ENTITY % ISOamsr.module "INCLUDE">
<![ %ISOamsr.module; [
<!ENTITY % ISOamsr PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Relations//EN">
<!--end of ISOamsr.module-->]]>

<!ENTITY % ISObox.module "INCLUDE">
<![ %ISObox.module; [
<!ENTITY % ISObox PUBLIC
"ISO 8879:1986//ENTITIES Box and Line Drawing//EN">
<!--end of ISObox.module-->]]>

<!ENTITY % ISOcyr1.module "INCLUDE">
<![ %ISOcyr1.module; [
<!ENTITY % ISOcyr1 PUBLIC
"ISO 8879:1986//ENTITIES Russian Cyrillic//EN">
<!--end of ISOcyr1.module-->]]>

<!ENTITY % ISOcyr2.module "INCLUDE">
<![ %ISOcyr2.module; [
<!ENTITY % ISOcyr2 PUBLIC
"ISO 8879:1986//ENTITIES Non-Russian Cyrillic//EN">
<!--end of ISOcyr2.module-->]]>

<!ENTITY % ISOdia.module "INCLUDE">
<![ %ISOdia.module; [
<!ENTITY % ISOdia PUBLIC
"ISO 8879:1986//ENTITIES Diacritical Marks//EN">
<!--end of ISOdia.module-->]]>

<!ENTITY % ISOgrk1.module "INCLUDE">
<![ %ISOgrk1.module; [
<!ENTITY % ISOgrk1 PUBLIC
"ISO 8879:1986//ENTITIES Greek Letters//EN">
<!--end of ISOgrk1.module-->]]>

<!ENTITY % ISOgrk2.module "INCLUDE">
<![ %ISOgrk2.module; [
<!ENTITY % ISOgrk2 PUBLIC
"ISO 8879:1986//ENTITIES Monotoniko Greek//EN">
<!--end of ISOgrk2.module-->]]>

<!ENTITY % ISOgrk3.module "INCLUDE">
<![ %ISOgrk3.module; [
<!ENTITY % ISOgrk3 PUBLIC
"ISO 8879:1986//ENTITIES Greek Symbols//EN">
<!--end of ISOgrk3.module-->]]>

<!ENTITY % ISOgrk4.module "INCLUDE">
<![ %ISOgrk4.module; [
<!ENTITY % ISOgrk4 PUBLIC
"ISO 8879:1986//ENTITIES Alternative Greek Symbols//EN">
<!--end of ISOgrk4.module-->]]>

<!ENTITY % ISOlat1.module "INCLUDE">
<![ %ISOlat1.module; [
<!ENTITY % ISOlat1 PUBLIC
"ISO 8879:1986//ENTITIES Added Latin 1//EN">
<!--end of ISOlat1.module-->]]>

<!ENTITY % ISOlat2.module "INCLUDE">
<![ %ISOlat2.module; [
<!ENTITY % ISOlat2 PUBLIC
"ISO 8879:1986//ENTITIES Added Latin 2//EN">
<!--end of ISOlat2.module-->]]>

<!ENTITY % ISOnum.module "INCLUDE">
<![ %ISOnum.module; [
<!ENTITY % ISOnum PUBLIC
"ISO 8879:1986//ENTITIES Numeric and Special Graphic//EN">
<!--end of ISOnum.module-->]]>

<!ENTITY % ISOpub.module "INCLUDE">
<![ %ISOpub.module; [
<!ENTITY % ISOpub PUBLIC
"ISO 8879:1986//ENTITIES Publishing//EN">
<!--end of ISOpub.module-->]]>

<!ENTITY % ISOtech.module "INCLUDE">
<![ %ISOtech.module; [
<!ENTITY % ISOtech PUBLIC
"ISO 8879:1986//ENTITIES General Technical//EN">
<!--end of ISOtech.module-->]]>

<!--end of sgml.features-->]]>

<![%xml.features;[

<!ENTITY % ISOamsa.module "INCLUDE">
<![%ISOamsa.module;[
<!ENTITY % ISOamsa PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Arrow Relations//EN//XML"
"ent/isoamsa.ent">
<!--end of ISOamsa.module-->]]>

<!ENTITY % ISOamsb.module "INCLUDE">
<![%ISOamsb.module;[
<!ENTITY % ISOamsb PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Binary Operators//EN//XML"
"ent/isoamsb.ent">
<!--end of ISOamsb.module-->]]>

<!ENTITY % ISOamsc.module "INCLUDE">
<![%ISOamsc.module;[
<!ENTITY % ISOamsc PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Delimiters//EN//XML"
"ent/isoamsc.ent">
<!--end of ISOamsc.module-->]]>

<!ENTITY % ISOamsn.module "INCLUDE">
<![%ISOamsn.module;[
<!ENTITY % ISOamsn PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Negated Relations//EN//XML"
"ent/isoamsn.ent">
<!--end of ISOamsn.module-->]]>

<!ENTITY % ISOamso.module "INCLUDE">
<![%ISOamso.module;[
<!ENTITY % ISOamso PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Ordinary//EN//XML"
"ent/isoamso.ent">
<!--end of ISOamso.module-->]]>

<!ENTITY % ISOamsr.module "INCLUDE">
<![%ISOamsr.module;[
<!ENTITY % ISOamsr PUBLIC
"ISO 8879:1986//ENTITIES Added Math Symbols: Relations//EN//XML"
"ent/isoamsr.ent">
<!--end of ISOamsr.module-->]]>

<!ENTITY % ISObox.module "INCLUDE">
<![%ISObox.module;[
<!ENTITY % ISObox PUBLIC
"ISO 8879:1986//ENTITIES Box and Line Drawing//EN//XML"
"ent/isobox.ent">
<!--end of ISObox.module-->]]>

<!ENTITY % ISOcyr1.module "INCLUDE">
<![%ISOcyr1.module;[
<!ENTITY % ISOcyr1 PUBLIC
"ISO 8879:1986//ENTITIES Russian Cyrillic//EN//XML"
"ent/isocyr1.ent">
<!--end of ISOcyr1.module-->]]>

<!ENTITY % ISOcyr2.module "INCLUDE">
<![%ISOcyr2.module;[
<!ENTITY % ISOcyr2 PUBLIC
"ISO 8879:1986//ENTITIES Non-Russian Cyrillic//EN//XML"
"ent/isocyr2.ent">
<!--end of ISOcyr2.module-->]]>

<!ENTITY % ISOdia.module "INCLUDE">
<![%ISOdia.module;[
<!ENTITY % ISOdia PUBLIC
"ISO 8879:1986//ENTITIES Diacritical Marks//EN//XML"
"ent/isodia.ent">
<!--end of ISOdia.module-->]]>

<!ENTITY % ISOgrk1.module "INCLUDE">
<![%ISOgrk1.module;[
<!ENTITY % ISOgrk1 PUBLIC
"ISO 8879:1986//ENTITIES Greek Letters//EN//XML"
"ent/isogrk1.ent">
<!--end of ISOgrk1.module-->]]>

<!ENTITY % ISOgrk2.module "INCLUDE">
<![%ISOgrk2.module;[
<!ENTITY % ISOgrk2 PUBLIC
"ISO 8879:1986//ENTITIES Monotoniko Greek//EN//XML"
"ent/isogrk2.ent">
<!--end of ISOgrk2.module-->]]>

<!ENTITY % ISOgrk3.module "INCLUDE">
<![%ISOgrk3.module;[
<!ENTITY % ISOgrk3 PUBLIC
"ISO 8879:1986//ENTITIES Greek Symbols//EN//XML"
"ent/isogrk3.ent">
<!--end of ISOgrk3.module-->]]>

<!ENTITY % ISOgrk4.module "INCLUDE">
<![%ISOgrk4.module;[
<!ENTITY % ISOgrk4 PUBLIC
"ISO 8879:1986//ENTITIES Alternative Greek Symbols//EN//XML"
"ent/isogrk4.ent">
<!--end of ISOgrk4.module-->]]>

<!ENTITY % ISOlat1.module "INCLUDE">
<![%ISOlat1.module;[
<!ENTITY % ISOlat1 PUBLIC
"ISO 8879:1986//ENTITIES Added Latin 1//EN//XML"
"ent/isolat1.ent">
<!--end of ISOlat1.module-->]]>

<!ENTITY % ISOlat2.module "INCLUDE">
<![%ISOlat2.module;[
<!ENTITY % ISOlat2 PUBLIC
"ISO 8879:1986//ENTITIES Added Latin 2//EN//XML"
"ent/isolat2.ent">
<!--end of ISOlat2.module-->]]>

<!ENTITY % ISOnum.module "INCLUDE">
<![%ISOnum.module;[
<!ENTITY % ISOnum PUBLIC
"ISO 8879:1986//ENTITIES Numeric and Special Graphic//EN//XML"
"ent/isonum.ent">
<!--end of ISOnum.module-->]]>

<!ENTITY % ISOpub.module "INCLUDE">
<![%ISOpub.module;[
<!ENTITY % ISOpub PUBLIC
"ISO 8879:1986//ENTITIES Publishing//EN//XML"
"ent/isopub.ent">
<!--end of ISOpub.module-->]]>

<!ENTITY % ISOtech.module "INCLUDE">
<![%ISOtech.module;[
<!ENTITY % ISOtech PUBLIC
"ISO 8879:1986//ENTITIES General Technical//EN//XML"
"ent/isotech.ent">
<!--end of ISOtech.module-->]]>

<!--end of xml.features-->]]>

<![ %ISOamsa.module; [
%ISOamsa;
]]>

<![ %ISOamsb.module; [
%ISOamsb;
]]>

<![ %ISOamsc.module; [
%ISOamsc;
]]>

<![ %ISOamsn.module; [
%ISOamsn;
]]>

<![ %ISOamso.module; [
%ISOamso;
]]>

<![ %ISOamsr.module; [
%ISOamsr;
]]>

<![ %ISObox.module; [
%ISObox;
]]>

<![ %ISOcyr1.module; [
%ISOcyr1;
]]>

<![ %ISOcyr2.module; [
%ISOcyr2;
]]>

<![ %ISOdia.module; [
%ISOdia;
]]>

<![ %ISOgrk1.module; [
%ISOgrk1;
]]>

<![ %ISOgrk2.module; [
%ISOgrk2;
]]>

<![ %ISOgrk3.module; [
%ISOgrk3;
]]>

<![ %ISOgrk4.module; [
%ISOgrk4;
]]>

<![ %ISOlat1.module; [
%ISOlat1;
]]>

<![ %ISOlat2.module; [
%ISOlat2;
]]>

<![ %ISOnum.module; [
%ISOnum;
]]>

<![ %ISOpub.module; [
%ISOpub;
]]>

<![ %ISOtech.module; [
%ISOtech;
]]>

<!-- End of DocBook character entity sets module V4.5 ..................... -->
<!-- ...................................................................... -->
