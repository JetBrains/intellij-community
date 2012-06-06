<!-- ...................................................................... -->
<!-- DocBook document hierarchy module V4.4 ............................... -->
<!-- File dbhierx.mod ..................................................... -->

<!-- Copyright 1992-2004 HaL Computer Systems, Inc.,
     O'Reilly & Associates, Inc., ArborText, Inc., Fujitsu Software
     Corporation, Norman Walsh, Sun Microsystems, Inc., and the
     Organization for the Advancement of Structured Information
     Standards (OASIS).

     $Id: dbhierx.mod,v 1.38 2005/01/27 13:52:00 nwalsh Exp $

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

<!-- This module contains the definitions for the overall document
     hierarchies of DocBook documents.  It covers computer documentation
     manuals and manual fragments, as well as reference entries (such as
     man pages) and technical journals or anthologies containing
     articles.

     This module depends on the DocBook information pool module.  All
     elements and entities referenced but not defined here are assumed
     to be defined in the information pool module.

     In DTD driver files referring to this module, please use an entity
     declaration that uses the public identifier shown below:

     <!ENTITY % dbhier PUBLIC
     "-//OASIS//ELEMENTS DocBook Document Hierarchy V4.4//EN"
     "dbhierx.mod">
     %dbhier;

     See the documentation for detailed information on the parameter
     entity and module scheme used in DocBook, customizing DocBook and
     planning for interchange, and changes made since the last release
     of DocBook.
-->

<!-- ...................................................................... -->
<!-- Entities for module inclusions ....................................... -->

<!ENTITY % dbhier.redecl.module		"IGNORE">
<!ENTITY % dbhier.redecl2.module	"IGNORE">

<!-- ...................................................................... -->
<!-- Entities for element classes ......................................... -->

<!ENTITY % local.appendix.class "">
<!ENTITY % appendix.class	"appendix %local.appendix.class;">

<!ENTITY % local.article.class "">
<!ENTITY % article.class	"article %local.article.class;">

<!ENTITY % local.book.class "">
<!ENTITY % book.class		"book %local.book.class;">

<!ENTITY % local.chapter.class "">
<!ENTITY % chapter.class	"chapter %local.chapter.class;">

<!ENTITY % local.index.class "">
<!ENTITY % index.class		"index|setindex %local.index.class;">

<!ENTITY % local.refentry.class "">
<!ENTITY % refentry.class	"refentry %local.refentry.class;">

<!ENTITY % local.section.class "">
<!ENTITY % section.class	"section %local.section.class;">

<!ENTITY % local.nav.class "">
<!ENTITY % nav.class		"toc|lot|index|glossary|bibliography
				%local.nav.class;">

<!-- Redeclaration placeholder ............................................ -->

<!-- For redeclaring entities that are declared after this point while
     retaining their references to the entities that are declared before
     this point -->

<![%dbhier.redecl.module;[
<!-- Defining rdbhier here makes some buggy XML parsers happy. -->
<!ENTITY % rdbhier "">
%rdbhier;
<!--end of dbhier.redecl.module-->]]>

<!-- ...................................................................... -->
<!-- Entities for element mixtures ........................................ -->

<!ENTITY % local.divcomponent.mix "">
<!ENTITY % divcomponent.mix
		"%list.class;		|%admon.class;
		|%linespecific.class;	|%synop.class;
		|%para.class;		|%informal.class;
		|%formal.class;		|%compound.class;
		|%genobj.class;		|%descobj.class;
		|%ndxterm.class;        |beginpage
                %forms.hook;
		%local.divcomponent.mix;">

<!ENTITY % local.refcomponent.mix "">
<!ENTITY % refcomponent.mix
		"%list.class;		|%admon.class;
		|%linespecific.class;	|%synop.class;
		|%para.class;		|%informal.class;
		|%formal.class;		|%compound.class;
		|%genobj.class;		|%descobj.class;
		|%ndxterm.class;        |beginpage
		%forms.hook;
                %local.refcomponent.mix;">

<!ENTITY % local.indexdivcomponent.mix "">
<!ENTITY % indexdivcomponent.mix
		"itemizedlist|orderedlist|variablelist|simplelist
		|%linespecific.class;	|%synop.class;
		|%para.class;		|%informal.class;
		|anchor|remark
		|%link.char.class;
 		                        |beginpage
		%local.indexdivcomponent.mix;">

<!ENTITY % local.refname.char.mix "">
<!ENTITY % refname.char.mix
		"#PCDATA
		|%tech.char.class;
		%local.refname.char.mix;">

<!ENTITY % local.partcontent.mix "">
<!ENTITY % partcontent.mix
		"%appendix.class;|%chapter.class;|%nav.class;|%article.class;
		|preface|%refentry.class;|reference %local.partcontent.mix;">

<!ENTITY % local.refinline.char.mix "">
<!ENTITY % refinline.char.mix
		"#PCDATA
		|%xref.char.class;	|%gen.char.class;
		|%link.char.class;	|%tech.char.class;
		|%base.char.class;	|%docinfo.char.class;
		|%other.char.class;
		|%ndxterm.class;        |beginpage
		%local.refinline.char.mix;">

<!ENTITY % local.refclass.char.mix "">
<!ENTITY % refclass.char.mix
		"#PCDATA
		|application
		%local.refclass.char.mix;">

<!-- Redeclaration placeholder 2 .......................................... -->

<!-- For redeclaring entities that are declared after this point while
     retaining their references to the entities that are declared before
     this point -->

<![%dbhier.redecl2.module;[
<!-- Defining rdbhier2 here makes some buggy XML parsers happy. -->
<!ENTITY % rdbhier2 "">
%rdbhier2;
<!--end of dbhier.redecl2.module-->]]>

<!-- ...................................................................... -->
<!-- Entities for content models .......................................... -->

<!ENTITY % div.title.content
	"title, subtitle?, titleabbrev?">

<!ENTITY % bookcomponent.title.content
	"title, subtitle?, titleabbrev?">

<!ENTITY % sect.title.content
	"title, subtitle?, titleabbrev?">

<!ENTITY % refsect.title.content
	"title, subtitle?, titleabbrev?">

<!ENTITY % bookcomponent.content
	"((%divcomponent.mix;)+,
	(sect1*|(%refentry.class;)*|simplesect*|(%section.class;)*))
	| (sect1+|(%refentry.class;)+|simplesect+|(%section.class;)+)">

<!-- ...................................................................... -->
<!-- Set and SetInfo ...................................................... -->

<!ENTITY % set.content.module "INCLUDE">
<![%set.content.module;[
<!ENTITY % set.module "INCLUDE">
<![%set.module;[
<!ENTITY % local.set.attrib "">
<!ENTITY % set.role.attrib "%role.attrib;">

<!ENTITY % set.element "INCLUDE">
<![%set.element;[
<!ELEMENT set %ho; ((%div.title.content;)?, setinfo?, toc?, (set|%book.class;)+,
		setindex?)
		%ubiq.inclusion;>
<!--end of set.element-->]]>

<!-- FPI: SGML formal public identifier -->


<!ENTITY % set.attlist "INCLUDE">
<![%set.attlist;[
<!ATTLIST set
		fpi		CDATA		#IMPLIED
		%status.attrib;
		%common.attrib;
		%set.role.attrib;
		%local.set.attrib;
>
<!--end of set.attlist-->]]>
<!--end of set.module-->]]>

<!ENTITY % setinfo.module "INCLUDE">
<![%setinfo.module;[
<!ENTITY % local.setinfo.attrib "">
<!ENTITY % setinfo.role.attrib "%role.attrib;">

<!ENTITY % setinfo.element "INCLUDE">
<![%setinfo.element;[
<!ELEMENT setinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of setinfo.element-->]]>

<!-- Contents: IDs of the ToC, Books, and SetIndex that comprise
		the set, in the order of their appearance -->


<!ENTITY % setinfo.attlist "INCLUDE">
<![%setinfo.attlist;[
<!ATTLIST setinfo
		contents	IDREFS		#IMPLIED
		%common.attrib;
		%setinfo.role.attrib;
		%local.setinfo.attrib;
>
<!--end of setinfo.attlist-->]]>
<!--end of setinfo.module-->]]>
<!--end of set.content.module-->]]>

<!-- ...................................................................... -->
<!-- Book and BookInfo .................................................... -->

<!ENTITY % book.content.module "INCLUDE">
<![%book.content.module;[
<!ENTITY % book.module "INCLUDE">
<![%book.module;[

<!ENTITY % local.book.attrib "">
<!ENTITY % book.role.attrib "%role.attrib;">

<!ENTITY % book.element "INCLUDE">
<![%book.element;[
<!ELEMENT book %ho; ((%div.title.content;)?, bookinfo?,
 		(dedication | toc | lot
 		| glossary | bibliography | preface
		| %chapter.class; | reference | part
		| %article.class;
 		| %appendix.class;
		| %index.class;
		| colophon)*)
		%ubiq.inclusion;>
<!--end of book.element-->]]>

<!-- FPI: SGML formal public identifier -->


<!ENTITY % book.attlist "INCLUDE">
<![%book.attlist;[
<!ATTLIST book		fpi		CDATA		#IMPLIED
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%book.role.attrib;
		%local.book.attrib;
>
<!--end of book.attlist-->]]>
<!--end of book.module-->]]>

<!ENTITY % bookinfo.module "INCLUDE">
<![%bookinfo.module;[
<!ENTITY % local.bookinfo.attrib "">
<!ENTITY % bookinfo.role.attrib "%role.attrib;">

<!ENTITY % bookinfo.element "INCLUDE">
<![%bookinfo.element;[
<!ELEMENT bookinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of bookinfo.element-->]]>

<!-- Contents: IDs of the ToC, LoTs, Prefaces, Parts, Chapters,
		Appendixes, References, GLossary, Bibliography, and indexes
		comprising the Book, in the order of their appearance -->


<!ENTITY % bookinfo.attlist "INCLUDE">
<![%bookinfo.attlist;[
<!ATTLIST bookinfo
		contents	IDREFS		#IMPLIED
		%common.attrib;
		%bookinfo.role.attrib;
		%local.bookinfo.attrib;
>
<!--end of bookinfo.attlist-->]]>
<!--end of bookinfo.module-->]]>
<!--end of book.content.module-->]]>

<!-- ...................................................................... -->
<!-- Dedication, ToC, and LoT ............................................. -->

<!ENTITY % dedication.module "INCLUDE">
<![%dedication.module;[
<!ENTITY % local.dedication.attrib "">
<!ENTITY % dedication.role.attrib "%role.attrib;">

<!ENTITY % dedication.element "INCLUDE">
<![%dedication.element;[
<!ELEMENT dedication %ho; ((%sect.title.content;)?, (%legalnotice.mix;)+)>
<!--end of dedication.element-->]]>

<!ENTITY % dedication.attlist "INCLUDE">
<![%dedication.attlist;[
<!ATTLIST dedication
		%status.attrib;
		%common.attrib;
		%dedication.role.attrib;
		%local.dedication.attrib;
>
<!--end of dedication.attlist-->]]>
<!--end of dedication.module-->]]>

<!ENTITY % colophon.module "INCLUDE">
<![ %colophon.module; [
<!ENTITY % local.colophon.attrib "">
<!ENTITY % colophon.role.attrib "%role.attrib;">

<!ENTITY % colophon.element "INCLUDE">
<![ %colophon.element; [
<!ELEMENT colophon %ho; ((%sect.title.content;)?, (%textobject.mix;)+)>
<!--end of colophon.element-->]]>

<!ENTITY % colophon.attlist "INCLUDE">
<![ %colophon.attlist; [
<!ATTLIST colophon
		%status.attrib;
		%common.attrib;
		%colophon.role.attrib;
		%local.colophon.attrib;>
<!--end of colophon.attlist-->]]>
<!--end of colophon.module-->]]>

<!ENTITY % toc.content.module "INCLUDE">
<![%toc.content.module;[
<!ENTITY % toc.module "INCLUDE">
<![%toc.module;[
<!ENTITY % local.toc.attrib "">
<!ENTITY % toc.role.attrib "%role.attrib;">

<!ENTITY % toc.element "INCLUDE">
<![%toc.element;[
<!ELEMENT toc %ho; (beginpage?,
		(%bookcomponent.title.content;)?,
		tocfront*,
		(tocpart | tocchap)*, tocback*)>
<!--end of toc.element-->]]>

<!ENTITY % toc.attlist "INCLUDE">
<![%toc.attlist;[
<!ATTLIST toc
		%pagenum.attrib;
		%common.attrib;
		%toc.role.attrib;
		%local.toc.attrib;
>
<!--end of toc.attlist-->]]>
<!--end of toc.module-->]]>

<!ENTITY % tocfront.module "INCLUDE">
<![%tocfront.module;[
<!ENTITY % local.tocfront.attrib "">
<!ENTITY % tocfront.role.attrib "%role.attrib;">

<!ENTITY % tocfront.element "INCLUDE">
<![%tocfront.element;[
<!ELEMENT tocfront %ho; (%para.char.mix;)*>
<!--end of tocfront.element-->]]>

<!-- to element that this entry represents -->


<!ENTITY % tocfront.attlist "INCLUDE">
<![%tocfront.attlist;[
<!ATTLIST tocfront
		%label.attrib;
		%linkend.attrib;		%pagenum.attrib;
		%common.attrib;
		%tocfront.role.attrib;
		%local.tocfront.attrib;
>
<!--end of tocfront.attlist-->]]>
<!--end of tocfront.module-->]]>

<!ENTITY % tocentry.module "INCLUDE">
<![%tocentry.module;[
<!ENTITY % local.tocentry.attrib "">
<!ENTITY % tocentry.role.attrib "%role.attrib;">

<!ENTITY % tocentry.element "INCLUDE">
<![%tocentry.element;[
<!ELEMENT tocentry %ho; (%para.char.mix;)*>
<!--end of tocentry.element-->]]>

<!-- to element that this entry represents -->


<!ENTITY % tocentry.attlist "INCLUDE">
<![%tocentry.attlist;[
<!ATTLIST tocentry
		%linkend.attrib;		%pagenum.attrib;
		%common.attrib;
		%tocentry.role.attrib;
		%local.tocentry.attrib;
>
<!--end of tocentry.attlist-->]]>
<!--end of tocentry.module-->]]>

<!ENTITY % tocpart.module "INCLUDE">
<![%tocpart.module;[
<!ENTITY % local.tocpart.attrib "">
<!ENTITY % tocpart.role.attrib "%role.attrib;">

<!ENTITY % tocpart.element "INCLUDE">
<![%tocpart.element;[
<!ELEMENT tocpart %ho; (tocentry+, tocchap*)>
<!--end of tocpart.element-->]]>

<!ENTITY % tocpart.attlist "INCLUDE">
<![%tocpart.attlist;[
<!ATTLIST tocpart
		%common.attrib;
		%tocpart.role.attrib;
		%local.tocpart.attrib;
>
<!--end of tocpart.attlist-->]]>
<!--end of tocpart.module-->]]>

<!ENTITY % tocchap.module "INCLUDE">
<![%tocchap.module;[
<!ENTITY % local.tocchap.attrib "">
<!ENTITY % tocchap.role.attrib "%role.attrib;">

<!ENTITY % tocchap.element "INCLUDE">
<![%tocchap.element;[
<!ELEMENT tocchap %ho; (tocentry+, toclevel1*)>
<!--end of tocchap.element-->]]>

<!ENTITY % tocchap.attlist "INCLUDE">
<![%tocchap.attlist;[
<!ATTLIST tocchap
		%label.attrib;
		%common.attrib;
		%tocchap.role.attrib;
		%local.tocchap.attrib;
>
<!--end of tocchap.attlist-->]]>
<!--end of tocchap.module-->]]>

<!ENTITY % toclevel1.module "INCLUDE">
<![%toclevel1.module;[
<!ENTITY % local.toclevel1.attrib "">
<!ENTITY % toclevel1.role.attrib "%role.attrib;">

<!ENTITY % toclevel1.element "INCLUDE">
<![%toclevel1.element;[
<!ELEMENT toclevel1 %ho; (tocentry+, toclevel2*)>
<!--end of toclevel1.element-->]]>

<!ENTITY % toclevel1.attlist "INCLUDE">
<![%toclevel1.attlist;[
<!ATTLIST toclevel1
		%common.attrib;
		%toclevel1.role.attrib;
		%local.toclevel1.attrib;
>
<!--end of toclevel1.attlist-->]]>
<!--end of toclevel1.module-->]]>

<!ENTITY % toclevel2.module "INCLUDE">
<![%toclevel2.module;[
<!ENTITY % local.toclevel2.attrib "">
<!ENTITY % toclevel2.role.attrib "%role.attrib;">

<!ENTITY % toclevel2.element "INCLUDE">
<![%toclevel2.element;[
<!ELEMENT toclevel2 %ho; (tocentry+, toclevel3*)>
<!--end of toclevel2.element-->]]>

<!ENTITY % toclevel2.attlist "INCLUDE">
<![%toclevel2.attlist;[
<!ATTLIST toclevel2
		%common.attrib;
		%toclevel2.role.attrib;
		%local.toclevel2.attrib;
>
<!--end of toclevel2.attlist-->]]>
<!--end of toclevel2.module-->]]>

<!ENTITY % toclevel3.module "INCLUDE">
<![%toclevel3.module;[
<!ENTITY % local.toclevel3.attrib "">
<!ENTITY % toclevel3.role.attrib "%role.attrib;">

<!ENTITY % toclevel3.element "INCLUDE">
<![%toclevel3.element;[
<!ELEMENT toclevel3 %ho; (tocentry+, toclevel4*)>
<!--end of toclevel3.element-->]]>

<!ENTITY % toclevel3.attlist "INCLUDE">
<![%toclevel3.attlist;[
<!ATTLIST toclevel3
		%common.attrib;
		%toclevel3.role.attrib;
		%local.toclevel3.attrib;
>
<!--end of toclevel3.attlist-->]]>
<!--end of toclevel3.module-->]]>

<!ENTITY % toclevel4.module "INCLUDE">
<![%toclevel4.module;[
<!ENTITY % local.toclevel4.attrib "">
<!ENTITY % toclevel4.role.attrib "%role.attrib;">

<!ENTITY % toclevel4.element "INCLUDE">
<![%toclevel4.element;[
<!ELEMENT toclevel4 %ho; (tocentry+, toclevel5*)>
<!--end of toclevel4.element-->]]>

<!ENTITY % toclevel4.attlist "INCLUDE">
<![%toclevel4.attlist;[
<!ATTLIST toclevel4
		%common.attrib;
		%toclevel4.role.attrib;
		%local.toclevel4.attrib;
>
<!--end of toclevel4.attlist-->]]>
<!--end of toclevel4.module-->]]>

<!ENTITY % toclevel5.module "INCLUDE">
<![%toclevel5.module;[
<!ENTITY % local.toclevel5.attrib "">
<!ENTITY % toclevel5.role.attrib "%role.attrib;">

<!ENTITY % toclevel5.element "INCLUDE">
<![%toclevel5.element;[
<!ELEMENT toclevel5 %ho; (tocentry+)>
<!--end of toclevel5.element-->]]>

<!ENTITY % toclevel5.attlist "INCLUDE">
<![%toclevel5.attlist;[
<!ATTLIST toclevel5
		%common.attrib;
		%toclevel5.role.attrib;
		%local.toclevel5.attrib;
>
<!--end of toclevel5.attlist-->]]>
<!--end of toclevel5.module-->]]>

<!ENTITY % tocback.module "INCLUDE">
<![%tocback.module;[
<!ENTITY % local.tocback.attrib "">
<!ENTITY % tocback.role.attrib "%role.attrib;">

<!ENTITY % tocback.element "INCLUDE">
<![%tocback.element;[
<!ELEMENT tocback %ho; (%para.char.mix;)*>
<!--end of tocback.element-->]]>

<!-- to element that this entry represents -->


<!ENTITY % tocback.attlist "INCLUDE">
<![%tocback.attlist;[
<!ATTLIST tocback
		%label.attrib;
		%linkend.attrib;		%pagenum.attrib;
		%common.attrib;
		%tocback.role.attrib;
		%local.tocback.attrib;
>
<!--end of tocback.attlist-->]]>
<!--end of tocback.module-->]]>
<!--end of toc.content.module-->]]>

<!ENTITY % lot.content.module "INCLUDE">
<![%lot.content.module;[
<!ENTITY % lot.module "INCLUDE">
<![%lot.module;[
<!ENTITY % local.lot.attrib "">
<!ENTITY % lot.role.attrib "%role.attrib;">

<!ENTITY % lot.element "INCLUDE">
<![%lot.element;[
<!ELEMENT lot %ho; (beginpage?, (%bookcomponent.title.content;)?, lotentry*)>
<!--end of lot.element-->]]>

<!ENTITY % lot.attlist "INCLUDE">
<![%lot.attlist;[
<!ATTLIST lot
		%label.attrib;
		%common.attrib;
		%lot.role.attrib;
		%local.lot.attrib;
>
<!--end of lot.attlist-->]]>
<!--end of lot.module-->]]>

<!ENTITY % lotentry.module "INCLUDE">
<![%lotentry.module;[
<!ENTITY % local.lotentry.attrib "">
<!ENTITY % lotentry.role.attrib "%role.attrib;">

<!ENTITY % lotentry.element "INCLUDE">
<![%lotentry.element;[
<!ELEMENT lotentry %ho; (%para.char.mix;)*>
<!--end of lotentry.element-->]]>

<!-- SrcCredit: Information about the source of the entry,
		as for a list of illustrations -->
<!-- linkend: to element that this entry represents-->
<!ENTITY % lotentry.attlist "INCLUDE">
<![%lotentry.attlist;[
<!ATTLIST lotentry
		%linkend.attrib;
		%pagenum.attrib;
		srccredit	CDATA		#IMPLIED
		%common.attrib;
		%lotentry.role.attrib;
		%local.lotentry.attrib;
>
<!--end of lotentry.attlist-->]]>
<!--end of lotentry.module-->]]>
<!--end of lot.content.module-->]]>

<!-- ...................................................................... -->
<!-- Appendix, Chapter, Part, Preface, Reference, PartIntro ............... -->

<!ENTITY % appendix.module "INCLUDE">
<![%appendix.module;[
<!ENTITY % local.appendix.attrib "">
<!ENTITY % appendix.role.attrib "%role.attrib;">

<!ENTITY % appendix.element "INCLUDE">
<![%appendix.element;[
<!ELEMENT appendix %ho; (beginpage?,
                     appendixinfo?,
                     (%bookcomponent.title.content;),
                     (%nav.class;)*,
                     tocchap?,
                     (%bookcomponent.content;),
                     (%nav.class;)*)
		%ubiq.inclusion;>
<!--end of appendix.element-->]]>

<!ENTITY % appendix.attlist "INCLUDE">
<![%appendix.attlist;[
<!ATTLIST appendix
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%appendix.role.attrib;
		%local.appendix.attrib;
>
<!--end of appendix.attlist-->]]>
<!--end of appendix.module-->]]>

<!ENTITY % chapter.module "INCLUDE">
<![%chapter.module;[
<!ENTITY % local.chapter.attrib "">
<!ENTITY % chapter.role.attrib "%role.attrib;">

<!ENTITY % chapter.element "INCLUDE">
<![%chapter.element;[
<!ELEMENT chapter %ho; (beginpage?,
                    chapterinfo?,
                    (%bookcomponent.title.content;),
                    (%nav.class;)*,
                    tocchap?,
                    (%bookcomponent.content;),
                    (%nav.class;)*)
		%ubiq.inclusion;>
<!--end of chapter.element-->]]>

<!ENTITY % chapter.attlist "INCLUDE">
<![%chapter.attlist;[
<!ATTLIST chapter
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%chapter.role.attrib;
		%local.chapter.attrib;
>
<!--end of chapter.attlist-->]]>
<!--end of chapter.module-->]]>

<!ENTITY % part.module "INCLUDE">
<![%part.module;[

<!-- Note that Part was to have its content model reduced in V4.4.  This
change will not be made after all. -->

<!ENTITY % local.part.attrib "">
<!ENTITY % part.role.attrib "%role.attrib;">

<!ENTITY % part.element "INCLUDE">
<![%part.element;[
<!ELEMENT part %ho; (beginpage?,
                partinfo?, (%bookcomponent.title.content;), partintro?,
		(%partcontent.mix;)+)
		%ubiq.inclusion;>
<!--end of part.element-->]]>

<!ENTITY % part.attlist "INCLUDE">
<![%part.attlist;[
<!ATTLIST part
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%part.role.attrib;
		%local.part.attrib;
>
<!--end of part.attlist-->]]>
<!--ELEMENT PartIntro (defined below)-->
<!--end of part.module-->]]>

<!ENTITY % preface.module "INCLUDE">
<![%preface.module;[
<!ENTITY % local.preface.attrib "">
<!ENTITY % preface.role.attrib "%role.attrib;">

<!ENTITY % preface.element "INCLUDE">
<![%preface.element;[
<!ELEMENT preface %ho; (beginpage?,
                    prefaceinfo?,
                    (%bookcomponent.title.content;),
                    (%nav.class;)*,
                    tocchap?,
                    (%bookcomponent.content;),
                    (%nav.class;)*)
		%ubiq.inclusion;>
<!--end of preface.element-->]]>

<!ENTITY % preface.attlist "INCLUDE">
<![%preface.attlist;[
<!ATTLIST preface
		%status.attrib;
		%common.attrib;
		%preface.role.attrib;
		%local.preface.attrib;
>
<!--end of preface.attlist-->]]>
<!--end of preface.module-->]]>

<!ENTITY % reference.module "INCLUDE">
<![%reference.module;[
<!ENTITY % local.reference.attrib "">
<!ENTITY % reference.role.attrib "%role.attrib;">

<!ENTITY % reference.element "INCLUDE">
<![%reference.element;[
<!ELEMENT reference %ho; (beginpage?,
                     referenceinfo?,
                     (%bookcomponent.title.content;), partintro?,
                     (%refentry.class;)+)
		%ubiq.inclusion;>
<!--end of reference.element-->]]>

<!ENTITY % reference.attlist "INCLUDE">
<![%reference.attlist;[
<!ATTLIST reference
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%reference.role.attrib;
		%local.reference.attrib;
>
<!--end of reference.attlist-->]]>
<!--ELEMENT PartIntro (defined below)-->
<!--end of reference.module-->]]>

<!ENTITY % partintro.module "INCLUDE">
<![%partintro.module;[
<!ENTITY % local.partintro.attrib "">
<!ENTITY % partintro.role.attrib "%role.attrib;">

<!ENTITY % partintro.element "INCLUDE">
<![%partintro.element;[
<!ELEMENT partintro %ho; ((%div.title.content;)?, (%bookcomponent.content;))
		%ubiq.inclusion;>
<!--end of partintro.element-->]]>

<!ENTITY % partintro.attlist "INCLUDE">
<![%partintro.attlist;[
<!ATTLIST partintro
		%label.attrib;
		%common.attrib;
		%partintro.role.attrib;
		%local.partintro.attrib;
>
<!--end of partintro.attlist-->]]>
<!--end of partintro.module-->]]>

<!-- ...................................................................... -->
<!-- Other Info elements .................................................. -->

<!ENTITY % appendixinfo.module "INCLUDE">
<![ %appendixinfo.module; [
<!ENTITY % local.appendixinfo.attrib "">
<!ENTITY % appendixinfo.role.attrib "%role.attrib;">

<!ENTITY % appendixinfo.element "INCLUDE">
<![ %appendixinfo.element; [
<!ELEMENT appendixinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of appendixinfo.element-->]]>

<!ENTITY % appendixinfo.attlist "INCLUDE">
<![ %appendixinfo.attlist; [
<!ATTLIST appendixinfo
		%common.attrib;
		%appendixinfo.role.attrib;
		%local.appendixinfo.attrib;
>
<!--end of appendixinfo.attlist-->]]>
<!--end of appendixinfo.module-->]]>

<!ENTITY % bibliographyinfo.module "INCLUDE">
<![ %bibliographyinfo.module; [
<!ENTITY % local.bibliographyinfo.attrib "">
<!ENTITY % bibliographyinfo.role.attrib "%role.attrib;">

<!ENTITY % bibliographyinfo.element "INCLUDE">
<![ %bibliographyinfo.element; [
<!ELEMENT bibliographyinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of bibliographyinfo.element-->]]>

<!ENTITY % bibliographyinfo.attlist "INCLUDE">
<![ %bibliographyinfo.attlist; [
<!ATTLIST bibliographyinfo
		%common.attrib;
		%bibliographyinfo.role.attrib;
		%local.bibliographyinfo.attrib;
>
<!--end of bibliographyinfo.attlist-->]]>
<!--end of bibliographyinfo.module-->]]>

<!ENTITY % chapterinfo.module "INCLUDE">
<![ %chapterinfo.module; [
<!ENTITY % local.chapterinfo.attrib "">
<!ENTITY % chapterinfo.role.attrib "%role.attrib;">

<!ENTITY % chapterinfo.element "INCLUDE">
<![ %chapterinfo.element; [
<!ELEMENT chapterinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of chapterinfo.element-->]]>

<!ENTITY % chapterinfo.attlist "INCLUDE">
<![ %chapterinfo.attlist; [
<!ATTLIST chapterinfo
		%common.attrib;
		%chapterinfo.role.attrib;
		%local.chapterinfo.attrib;
>
<!--end of chapterinfo.attlist-->]]>
<!--end of chapterinfo.module-->]]>

<!ENTITY % glossaryinfo.module "INCLUDE">
<![ %glossaryinfo.module; [
<!ENTITY % local.glossaryinfo.attrib "">
<!ENTITY % glossaryinfo.role.attrib "%role.attrib;">

<!ENTITY % glossaryinfo.element "INCLUDE">
<![ %glossaryinfo.element; [
<!ELEMENT glossaryinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of glossaryinfo.element-->]]>

<!ENTITY % glossaryinfo.attlist "INCLUDE">
<![ %glossaryinfo.attlist; [
<!ATTLIST glossaryinfo
		%common.attrib;
		%glossaryinfo.role.attrib;
		%local.glossaryinfo.attrib;
>
<!--end of glossaryinfo.attlist-->]]>
<!--end of glossaryinfo.module-->]]>

<!ENTITY % indexinfo.module "INCLUDE">
<![ %indexinfo.module; [
<!ENTITY % local.indexinfo.attrib "">
<!ENTITY % indexinfo.role.attrib "%role.attrib;">

<!ENTITY % indexinfo.element "INCLUDE">
<![ %indexinfo.element; [
<!ELEMENT indexinfo %ho; ((%info.class;)+)>
<!--end of indexinfo.element-->]]>

<!ENTITY % indexinfo.attlist "INCLUDE">
<![ %indexinfo.attlist; [
<!ATTLIST indexinfo
		%common.attrib;
		%indexinfo.role.attrib;
		%local.indexinfo.attrib;
>
<!--end of indexinfo.attlist-->]]>
<!--end of indexinfo.module-->]]>

<!ENTITY % setindexinfo.module "INCLUDE">
<![ %setindexinfo.module; [
<!ENTITY % local.setindexinfo.attrib "">
<!ENTITY % setindexinfo.role.attrib "%role.attrib;">

<!ENTITY % setindexinfo.element "INCLUDE">
<![ %setindexinfo.element; [
<!ELEMENT setindexinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of setindexinfo.element-->]]>

<!ENTITY % setindexinfo.attlist "INCLUDE">
<![ %setindexinfo.attlist; [
<!ATTLIST setindexinfo
		%common.attrib;
		%setindexinfo.role.attrib;
		%local.setindexinfo.attrib;
>
<!--end of setindexinfo.attlist-->]]>
<!--end of setindexinfo.module-->]]>

<!ENTITY % partinfo.module "INCLUDE">
<![ %partinfo.module; [
<!ENTITY % local.partinfo.attrib "">
<!ENTITY % partinfo.role.attrib "%role.attrib;">

<!ENTITY % partinfo.element "INCLUDE">
<![ %partinfo.element; [
<!ELEMENT partinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of partinfo.element-->]]>

<!ENTITY % partinfo.attlist "INCLUDE">
<![ %partinfo.attlist; [
<!ATTLIST partinfo
		%common.attrib;
		%partinfo.role.attrib;
		%local.partinfo.attrib;
>
<!--end of partinfo.attlist-->]]>
<!--end of partinfo.module-->]]>

<!ENTITY % prefaceinfo.module "INCLUDE">
<![ %prefaceinfo.module; [
<!ENTITY % local.prefaceinfo.attrib "">
<!ENTITY % prefaceinfo.role.attrib "%role.attrib;">

<!ENTITY % prefaceinfo.element "INCLUDE">
<![ %prefaceinfo.element; [
<!ELEMENT prefaceinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of prefaceinfo.element-->]]>

<!ENTITY % prefaceinfo.attlist "INCLUDE">
<![ %prefaceinfo.attlist; [
<!ATTLIST prefaceinfo
		%common.attrib;
		%prefaceinfo.role.attrib;
		%local.prefaceinfo.attrib;
>
<!--end of prefaceinfo.attlist-->]]>
<!--end of prefaceinfo.module-->]]>

<!ENTITY % refentryinfo.module "INCLUDE">
<![ %refentryinfo.module; [
<!ENTITY % local.refentryinfo.attrib "">
<!ENTITY % refentryinfo.role.attrib "%role.attrib;">

<!ENTITY % refentryinfo.element "INCLUDE">
<![ %refentryinfo.element; [
<!ELEMENT refentryinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of refentryinfo.element-->]]>

<!ENTITY % refentryinfo.attlist "INCLUDE">
<![ %refentryinfo.attlist; [
<!ATTLIST refentryinfo
		%common.attrib;
		%refentryinfo.role.attrib;
		%local.refentryinfo.attrib;
>
<!--end of refentryinfo.attlist-->]]>
<!--end of refentryinfo.module-->]]>

<!ENTITY % refsectioninfo.module "INCLUDE">
<![ %refsectioninfo.module; [
<!ENTITY % local.refsectioninfo.attrib "">
<!ENTITY % refsectioninfo.role.attrib "%role.attrib;">

<!ENTITY % refsectioninfo.element "INCLUDE">
<![ %refsectioninfo.element; [
<!ELEMENT refsectioninfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of refsectioninfo.element-->]]>

<!ENTITY % refsectioninfo.attlist "INCLUDE">
<![ %refsectioninfo.attlist; [
<!ATTLIST refsectioninfo
		%common.attrib;
		%refsectioninfo.role.attrib;
		%local.refsectioninfo.attrib;
>
<!--end of refsectioninfo.attlist-->]]>
<!--end of refsectioninfo.module-->]]>

<!ENTITY % refsect1info.module "INCLUDE">
<![ %refsect1info.module; [
<!ENTITY % local.refsect1info.attrib "">
<!ENTITY % refsect1info.role.attrib "%role.attrib;">

<!ENTITY % refsect1info.element "INCLUDE">
<![ %refsect1info.element; [
<!ELEMENT refsect1info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of refsect1info.element-->]]>

<!ENTITY % refsect1info.attlist "INCLUDE">
<![ %refsect1info.attlist; [
<!ATTLIST refsect1info
		%common.attrib;
		%refsect1info.role.attrib;
		%local.refsect1info.attrib;
>
<!--end of refsect1info.attlist-->]]>
<!--end of refsect1info.module-->]]>

<!ENTITY % refsect2info.module "INCLUDE">
<![ %refsect2info.module; [
<!ENTITY % local.refsect2info.attrib "">
<!ENTITY % refsect2info.role.attrib "%role.attrib;">

<!ENTITY % refsect2info.element "INCLUDE">
<![ %refsect2info.element; [
<!ELEMENT refsect2info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of refsect2info.element-->]]>

<!ENTITY % refsect2info.attlist "INCLUDE">
<![ %refsect2info.attlist; [
<!ATTLIST refsect2info
		%common.attrib;
		%refsect2info.role.attrib;
		%local.refsect2info.attrib;
>
<!--end of refsect2info.attlist-->]]>
<!--end of refsect2info.module-->]]>

<!ENTITY % refsect3info.module "INCLUDE">
<![ %refsect3info.module; [
<!ENTITY % local.refsect3info.attrib "">
<!ENTITY % refsect3info.role.attrib "%role.attrib;">

<!ENTITY % refsect3info.element "INCLUDE">
<![ %refsect3info.element; [
<!ELEMENT refsect3info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of refsect3info.element-->]]>

<!ENTITY % refsect3info.attlist "INCLUDE">
<![ %refsect3info.attlist; [
<!ATTLIST refsect3info
		%common.attrib;
		%refsect3info.role.attrib;
		%local.refsect3info.attrib;
>
<!--end of refsect3info.attlist-->]]>
<!--end of refsect3info.module-->]]>

<!ENTITY % refsynopsisdivinfo.module "INCLUDE">
<![ %refsynopsisdivinfo.module; [
<!ENTITY % local.refsynopsisdivinfo.attrib "">
<!ENTITY % refsynopsisdivinfo.role.attrib "%role.attrib;">

<!ENTITY % refsynopsisdivinfo.element "INCLUDE">
<![ %refsynopsisdivinfo.element; [
<!ELEMENT refsynopsisdivinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of refsynopsisdivinfo.element-->]]>

<!ENTITY % refsynopsisdivinfo.attlist "INCLUDE">
<![ %refsynopsisdivinfo.attlist; [
<!ATTLIST refsynopsisdivinfo
		%common.attrib;
		%refsynopsisdivinfo.role.attrib;
		%local.refsynopsisdivinfo.attrib;
>
<!--end of refsynopsisdivinfo.attlist-->]]>
<!--end of refsynopsisdivinfo.module-->]]>

<!ENTITY % referenceinfo.module "INCLUDE">
<![ %referenceinfo.module; [
<!ENTITY % local.referenceinfo.attrib "">
<!ENTITY % referenceinfo.role.attrib "%role.attrib;">

<!ENTITY % referenceinfo.element "INCLUDE">
<![ %referenceinfo.element; [
<!ELEMENT referenceinfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of referenceinfo.element-->]]>

<!ENTITY % referenceinfo.attlist "INCLUDE">
<![ %referenceinfo.attlist; [
<!ATTLIST referenceinfo
		%common.attrib;
		%referenceinfo.role.attrib;
		%local.referenceinfo.attrib;
>
<!--end of referenceinfo.attlist-->]]>
<!--end of referenceinfo.module-->]]>

<!ENTITY % local.sect1info.attrib "">
<!ENTITY % sect1info.role.attrib "%role.attrib;">

<!ENTITY % sect1info.element "INCLUDE">
<![%sect1info.element;[
<!ELEMENT sect1info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of sect1info.element-->]]>

<!ENTITY % sect1info.attlist "INCLUDE">
<![%sect1info.attlist;[
<!ATTLIST sect1info
		%common.attrib;
		%sect1info.role.attrib;
		%local.sect1info.attrib;
>
<!--end of sect1info.attlist-->]]>

<!ENTITY % local.sect2info.attrib "">
<!ENTITY % sect2info.role.attrib "%role.attrib;">

<!ENTITY % sect2info.element "INCLUDE">
<![%sect2info.element;[
<!ELEMENT sect2info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of sect2info.element-->]]>

<!ENTITY % sect2info.attlist "INCLUDE">
<![%sect2info.attlist;[
<!ATTLIST sect2info
		%common.attrib;
		%sect2info.role.attrib;
		%local.sect2info.attrib;
>
<!--end of sect2info.attlist-->]]>

<!ENTITY % local.sect3info.attrib "">
<!ENTITY % sect3info.role.attrib "%role.attrib;">

<!ENTITY % sect3info.element "INCLUDE">
<![%sect3info.element;[
<!ELEMENT sect3info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of sect3info.element-->]]>

<!ENTITY % sect3info.attlist "INCLUDE">
<![%sect3info.attlist;[
<!ATTLIST sect3info
		%common.attrib;
		%sect3info.role.attrib;
		%local.sect3info.attrib;
>
<!--end of sect3info.attlist-->]]>

<!ENTITY % local.sect4info.attrib "">
<!ENTITY % sect4info.role.attrib "%role.attrib;">

<!ENTITY % sect4info.element "INCLUDE">
<![%sect4info.element;[
<!ELEMENT sect4info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of sect4info.element-->]]>

<!ENTITY % sect4info.attlist "INCLUDE">
<![%sect4info.attlist;[
<!ATTLIST sect4info
		%common.attrib;
		%sect4info.role.attrib;
		%local.sect4info.attrib;
>
<!--end of sect4info.attlist-->]]>

<!ENTITY % local.sect5info.attrib "">
<!ENTITY % sect5info.role.attrib "%role.attrib;">

<!ENTITY % sect5info.element "INCLUDE">
<![%sect5info.element;[
<!ELEMENT sect5info %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of sect5info.element-->]]>

<!ENTITY % sect5info.attlist "INCLUDE">
<![%sect5info.attlist;[
<!ATTLIST sect5info
		%common.attrib;
		%sect5info.role.attrib;
		%local.sect5info.attrib;
>
<!--end of sect5info.attlist-->]]>

<!-- ...................................................................... -->
<!-- Section (parallel to Sect*) ......................................... -->

<!ENTITY % section.content.module "INCLUDE">
<![ %section.content.module; [
<!ENTITY % section.module "INCLUDE">
<![ %section.module; [
<!ENTITY % local.section.attrib "">
<!ENTITY % section.role.attrib "%role.attrib;">

<!ENTITY % section.element "INCLUDE">
<![ %section.element; [
<!ELEMENT section %ho; (sectioninfo?,
			(%sect.title.content;),
			(%nav.class;)*,
			(((%divcomponent.mix;)+,
 			  ((%refentry.class;)*|(%section.class;)*|simplesect*))
			 | (%refentry.class;)+|(%section.class;)+|simplesect+),
			(%nav.class;)*)
		%ubiq.inclusion;>
<!--end of section.element-->]]>

<!ENTITY % section.attlist "INCLUDE">
<![ %section.attlist; [
<!ATTLIST section
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%section.role.attrib;
		%local.section.attrib;
>
<!--end of section.attlist-->]]>
<!--end of section.module-->]]>

<!ENTITY % sectioninfo.module "INCLUDE">
<![ %sectioninfo.module; [
<!ENTITY % sectioninfo.role.attrib "%role.attrib;">
<!ENTITY % local.sectioninfo.attrib "">

<!ENTITY % sectioninfo.element "INCLUDE">
<![ %sectioninfo.element; [
<!ELEMENT sectioninfo %ho; ((%info.class;)+)
		%beginpage.exclusion;>
<!--end of sectioninfo.element-->]]>

<!ENTITY % sectioninfo.attlist "INCLUDE">
<![ %sectioninfo.attlist; [
<!ATTLIST sectioninfo
		%common.attrib;
		%sectioninfo.role.attrib;
		%local.sectioninfo.attrib;
>
<!--end of sectioninfo.attlist-->]]>
<!--end of sectioninfo.module-->]]>
<!--end of section.content.module-->]]>

<!-- ...................................................................... -->
<!-- Sect1, Sect2, Sect3, Sect4, Sect5 .................................... -->

<!ENTITY % sect1.module "INCLUDE">
<![%sect1.module;[
<!ENTITY % local.sect1.attrib "">
<!ENTITY % sect1.role.attrib "%role.attrib;">

<!ENTITY % sect1.element "INCLUDE">
<![%sect1.element;[
<!ELEMENT sect1 %ho; (sect1info?, (%sect.title.content;), (%nav.class;)*,
		(((%divcomponent.mix;)+,
		((%refentry.class;)* | sect2* | simplesect*))
		| (%refentry.class;)+ | sect2+ | simplesect+), (%nav.class;)*)
		%ubiq.inclusion;>
<!--end of sect1.element-->]]>

<!-- Renderas: Indicates the format in which the heading should
		appear -->


<!ENTITY % sect1.attlist "INCLUDE">
<![%sect1.attlist;[
<!ATTLIST sect1
		renderas	(sect2
				|sect3
				|sect4
				|sect5)		#IMPLIED
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%sect1.role.attrib;
		%local.sect1.attrib;
>
<!--end of sect1.attlist-->]]>
<!--end of sect1.module-->]]>

<!ENTITY % sect2.module "INCLUDE">
<![%sect2.module;[
<!ENTITY % local.sect2.attrib "">
<!ENTITY % sect2.role.attrib "%role.attrib;">

<!ENTITY % sect2.element "INCLUDE">
<![%sect2.element;[
<!ELEMENT sect2 %ho; (sect2info?, (%sect.title.content;), (%nav.class;)*,
		(((%divcomponent.mix;)+,
		((%refentry.class;)* | sect3* | simplesect*))
		| (%refentry.class;)+ | sect3+ | simplesect+), (%nav.class;)*)>
<!--end of sect2.element-->]]>

<!-- Renderas: Indicates the format in which the heading should
		appear -->


<!ENTITY % sect2.attlist "INCLUDE">
<![%sect2.attlist;[
<!ATTLIST sect2
		renderas	(sect1
				|sect3
				|sect4
				|sect5)		#IMPLIED
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%sect2.role.attrib;
		%local.sect2.attrib;
>
<!--end of sect2.attlist-->]]>
<!--end of sect2.module-->]]>

<!ENTITY % sect3.module "INCLUDE">
<![%sect3.module;[
<!ENTITY % local.sect3.attrib "">
<!ENTITY % sect3.role.attrib "%role.attrib;">

<!ENTITY % sect3.element "INCLUDE">
<![%sect3.element;[
<!ELEMENT sect3 %ho; (sect3info?, (%sect.title.content;), (%nav.class;)*,
		(((%divcomponent.mix;)+,
		((%refentry.class;)* | sect4* | simplesect*))
		| (%refentry.class;)+ | sect4+ | simplesect+), (%nav.class;)*)>
<!--end of sect3.element-->]]>

<!-- Renderas: Indicates the format in which the heading should
		appear -->


<!ENTITY % sect3.attlist "INCLUDE">
<![%sect3.attlist;[
<!ATTLIST sect3
		renderas	(sect1
				|sect2
				|sect4
				|sect5)		#IMPLIED
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%sect3.role.attrib;
		%local.sect3.attrib;
>
<!--end of sect3.attlist-->]]>
<!--end of sect3.module-->]]>

<!ENTITY % sect4.module "INCLUDE">
<![%sect4.module;[
<!ENTITY % local.sect4.attrib "">
<!ENTITY % sect4.role.attrib "%role.attrib;">

<!ENTITY % sect4.element "INCLUDE">
<![%sect4.element;[
<!ELEMENT sect4 %ho; (sect4info?, (%sect.title.content;), (%nav.class;)*,
		(((%divcomponent.mix;)+,
		((%refentry.class;)* | sect5* | simplesect*))
		| (%refentry.class;)+ | sect5+ | simplesect+), (%nav.class;)*)>
<!--end of sect4.element-->]]>

<!-- Renderas: Indicates the format in which the heading should
		appear -->


<!ENTITY % sect4.attlist "INCLUDE">
<![%sect4.attlist;[
<!ATTLIST sect4
		renderas	(sect1
				|sect2
				|sect3
				|sect5)		#IMPLIED
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%sect4.role.attrib;
		%local.sect4.attrib;
>
<!--end of sect4.attlist-->]]>
<!--end of sect4.module-->]]>

<!ENTITY % sect5.module "INCLUDE">
<![%sect5.module;[
<!ENTITY % local.sect5.attrib "">
<!ENTITY % sect5.role.attrib "%role.attrib;">

<!ENTITY % sect5.element "INCLUDE">
<![%sect5.element;[
<!ELEMENT sect5 %ho; (sect5info?, (%sect.title.content;), (%nav.class;)*,
		(((%divcomponent.mix;)+, ((%refentry.class;)* | simplesect*))
		| (%refentry.class;)+ | simplesect+), (%nav.class;)*)>
<!--end of sect5.element-->]]>

<!-- Renderas: Indicates the format in which the heading should
		appear -->


<!ENTITY % sect5.attlist "INCLUDE">
<![%sect5.attlist;[
<!ATTLIST sect5
		renderas	(sect1
				|sect2
				|sect3
				|sect4)		#IMPLIED
		%label.attrib;
		%status.attrib;
		%common.attrib;
		%sect5.role.attrib;
		%local.sect5.attrib;
>
<!--end of sect5.attlist-->]]>
<!--end of sect5.module-->]]>

<!ENTITY % simplesect.module "INCLUDE">
<![%simplesect.module;[
<!ENTITY % local.simplesect.attrib "">
<!ENTITY % simplesect.role.attrib "%role.attrib;">

<!ENTITY % simplesect.element "INCLUDE">
<![%simplesect.element;[
<!ELEMENT simplesect %ho; ((%sect.title.content;), (%divcomponent.mix;)+)
		%ubiq.inclusion;>
<!--end of simplesect.element-->]]>

<!ENTITY % simplesect.attlist "INCLUDE">
<![%simplesect.attlist;[
<!ATTLIST simplesect
		%common.attrib;
		%simplesect.role.attrib;
		%local.simplesect.attrib;
>
<!--end of simplesect.attlist-->]]>
<!--end of simplesect.module-->]]>

<!-- ...................................................................... -->
<!-- Bibliography ......................................................... -->

<!ENTITY % bibliography.content.module "INCLUDE">
<![%bibliography.content.module;[
<!ENTITY % bibliography.module "INCLUDE">
<![%bibliography.module;[
<!ENTITY % local.bibliography.attrib "">
<!ENTITY % bibliography.role.attrib "%role.attrib;">

<!ENTITY % bibliography.element "INCLUDE">
<![%bibliography.element;[
<!ELEMENT bibliography %ho; (bibliographyinfo?,
                        (%bookcomponent.title.content;)?,
                        (%component.mix;)*,
                        (bibliodiv+ | (biblioentry|bibliomixed)+))>
<!--end of bibliography.element-->]]>

<!ENTITY % bibliography.attlist "INCLUDE">
<![%bibliography.attlist;[
<!ATTLIST bibliography
		%status.attrib;
		%common.attrib;
		%bibliography.role.attrib;
		%local.bibliography.attrib;
>
<!--end of bibliography.attlist-->]]>
<!--end of bibliography.module-->]]>

<!ENTITY % bibliodiv.module "INCLUDE">
<![%bibliodiv.module;[
<!ENTITY % local.bibliodiv.attrib "">
<!ENTITY % bibliodiv.role.attrib "%role.attrib;">

<!ENTITY % bibliodiv.element "INCLUDE">
<![%bibliodiv.element;[
<!ELEMENT bibliodiv %ho; ((%sect.title.content;)?, (%component.mix;)*,
		(biblioentry|bibliomixed)+)>
<!--end of bibliodiv.element-->]]>

<!ENTITY % bibliodiv.attlist "INCLUDE">
<![%bibliodiv.attlist;[
<!ATTLIST bibliodiv
		%status.attrib;
		%common.attrib;
		%bibliodiv.role.attrib;
		%local.bibliodiv.attrib;
>
<!--end of bibliodiv.attlist-->]]>
<!--end of bibliodiv.module-->]]>
<!--end of bibliography.content.module-->]]>

<!-- ...................................................................... -->
<!-- Glossary ............................................................. -->

<!ENTITY % glossary.content.module "INCLUDE">
<![%glossary.content.module;[
<!ENTITY % glossary.module "INCLUDE">
<![%glossary.module;[
<!ENTITY % local.glossary.attrib "">
<!ENTITY % glossary.role.attrib "%role.attrib;">

<!ENTITY % glossary.element "INCLUDE">
<![%glossary.element;[
<!ELEMENT glossary %ho; (glossaryinfo?,
                    (%bookcomponent.title.content;)?,
                    (%component.mix;)*,
                    (glossdiv+ | glossentry+), bibliography?)>
<!--end of glossary.element-->]]>

<!ENTITY % glossary.attlist "INCLUDE">
<![%glossary.attlist;[
<!ATTLIST glossary
		%status.attrib;
		%common.attrib;
		%glossary.role.attrib;
		%local.glossary.attrib;
>
<!--end of glossary.attlist-->]]>
<!--end of glossary.module-->]]>

<!ENTITY % glossdiv.module "INCLUDE">
<![%glossdiv.module;[
<!ENTITY % local.glossdiv.attrib "">
<!ENTITY % glossdiv.role.attrib "%role.attrib;">

<!ENTITY % glossdiv.element "INCLUDE">
<![%glossdiv.element;[
<!ELEMENT glossdiv %ho; ((%sect.title.content;), (%component.mix;)*,
		glossentry+)>
<!--end of glossdiv.element-->]]>

<!ENTITY % glossdiv.attlist "INCLUDE">
<![%glossdiv.attlist;[
<!ATTLIST glossdiv
		%status.attrib;
		%common.attrib;
		%glossdiv.role.attrib;
		%local.glossdiv.attrib;
>
<!--end of glossdiv.attlist-->]]>
<!--end of glossdiv.module-->]]>
<!--end of glossary.content.module-->]]>

<!-- ...................................................................... -->
<!-- Index and SetIndex ................................................... -->

<!ENTITY % index.content.module "INCLUDE">
<![%index.content.module;[
<!ENTITY % indexes.module "INCLUDE">
<![%indexes.module;[
<!ENTITY % local.indexes.attrib "">
<!ENTITY % indexes.role.attrib "%role.attrib;">

<!ENTITY % index.element "INCLUDE">
<![%index.element;[
<!ELEMENT index %ho; (indexinfo?,
                 (%bookcomponent.title.content;)?,
                 (%component.mix;)*,
                 (indexdiv* | indexentry*))
		%ndxterm.exclusion;>
<!--end of index.element-->]]>

<!ENTITY % index.attlist "INCLUDE">
<![%index.attlist;[
<!ATTLIST index
		type		CDATA		#IMPLIED
		%common.attrib;
		%indexes.role.attrib;
		%local.indexes.attrib;
>
<!--end of index.attlist-->]]>

<!ENTITY % setindex.element "INCLUDE">
<![%setindex.element;[
<!ELEMENT setindex %ho; (setindexinfo?,
                    (%bookcomponent.title.content;)?,
                    (%component.mix;)*,
                    (indexdiv* | indexentry*))
		%ndxterm.exclusion;>
<!--end of setindex.element-->]]>

<!ENTITY % setindex.attlist "INCLUDE">
<![%setindex.attlist;[
<!ATTLIST setindex
		%common.attrib;
		%indexes.role.attrib;
		%local.indexes.attrib;
>
<!--end of setindex.attlist-->]]>
<!--end of indexes.module-->]]>

<!ENTITY % indexdiv.module "INCLUDE">
<![%indexdiv.module;[

<!-- SegmentedList in this content is useful for marking up permuted
     indices. -->

<!ENTITY % local.indexdiv.attrib "">
<!ENTITY % indexdiv.role.attrib "%role.attrib;">

<!ENTITY % indexdiv.element "INCLUDE">
<![%indexdiv.element;[
<!ELEMENT indexdiv %ho; ((%sect.title.content;)?, ((%indexdivcomponent.mix;)*,
		(indexentry+ | segmentedlist)))>
<!--end of indexdiv.element-->]]>

<!ENTITY % indexdiv.attlist "INCLUDE">
<![%indexdiv.attlist;[
<!ATTLIST indexdiv
		%common.attrib;
		%indexdiv.role.attrib;
		%local.indexdiv.attrib;
>
<!--end of indexdiv.attlist-->]]>
<!--end of indexdiv.module-->]]>

<!ENTITY % indexentry.module "INCLUDE">
<![%indexentry.module;[
<!-- Index entries appear in the index, not the text. -->

<!ENTITY % local.indexentry.attrib "">
<!ENTITY % indexentry.role.attrib "%role.attrib;">

<!ENTITY % indexentry.element "INCLUDE">
<![%indexentry.element;[
<!ELEMENT indexentry %ho; (primaryie, (seeie|seealsoie)*,
		(secondaryie, (seeie|seealsoie|tertiaryie)*)*)>
<!--end of indexentry.element-->]]>

<!ENTITY % indexentry.attlist "INCLUDE">
<![%indexentry.attlist;[
<!ATTLIST indexentry
		%common.attrib;
		%indexentry.role.attrib;
		%local.indexentry.attrib;
>
<!--end of indexentry.attlist-->]]>
<!--end of indexentry.module-->]]>

<!ENTITY % primsecterie.module "INCLUDE">
<![%primsecterie.module;[
<!ENTITY % local.primsecterie.attrib "">
<!ENTITY % primsecterie.role.attrib "%role.attrib;">

<!ENTITY % primaryie.element "INCLUDE">
<![%primaryie.element;[
<!ELEMENT primaryie %ho; (%ndxterm.char.mix;)*>
<!--end of primaryie.element-->]]>

<!-- to IndexTerms that these entries represent -->

<!ENTITY % primaryie.attlist "INCLUDE">
<![%primaryie.attlist;[
<!ATTLIST primaryie
		%linkends.attrib;		%common.attrib;
		%primsecterie.role.attrib;
		%local.primsecterie.attrib;
>
<!--end of primaryie.attlist-->]]>

<!ENTITY % secondaryie.element "INCLUDE">
<![%secondaryie.element;[
<!ELEMENT secondaryie %ho; (%ndxterm.char.mix;)*>
<!--end of secondaryie.element-->]]>

<!-- to IndexTerms that these entries represent -->

<!ENTITY % secondaryie.attlist "INCLUDE">
<![%secondaryie.attlist;[
<!ATTLIST secondaryie
		%linkends.attrib;		%common.attrib;
		%primsecterie.role.attrib;
		%local.primsecterie.attrib;
>
<!--end of secondaryie.attlist-->]]>

<!ENTITY % tertiaryie.element "INCLUDE">
<![%tertiaryie.element;[
<!ELEMENT tertiaryie %ho; (%ndxterm.char.mix;)*>
<!--end of tertiaryie.element-->]]>

<!-- to IndexTerms that these entries represent -->

<!ENTITY % tertiaryie.attlist "INCLUDE">
<![%tertiaryie.attlist;[
<!ATTLIST tertiaryie
		%linkends.attrib;		%common.attrib;
		%primsecterie.role.attrib;
		%local.primsecterie.attrib;
>
<!--end of tertiaryie.attlist-->]]>

<!--end of primsecterie.module-->]]>

<!ENTITY % seeie.module "INCLUDE">
<![%seeie.module;[
<!ENTITY % local.seeie.attrib "">
<!ENTITY % seeie.role.attrib "%role.attrib;">

<!ENTITY % seeie.element "INCLUDE">
<![%seeie.element;[
<!ELEMENT seeie %ho; (%ndxterm.char.mix;)*>
<!--end of seeie.element-->]]>

<!-- to IndexEntry to look up -->


<!ENTITY % seeie.attlist "INCLUDE">
<![%seeie.attlist;[
<!ATTLIST seeie
		%linkend.attrib;		%common.attrib;
		%seeie.role.attrib;
		%local.seeie.attrib;
>
<!--end of seeie.attlist-->]]>
<!--end of seeie.module-->]]>

<!ENTITY % seealsoie.module "INCLUDE">
<![%seealsoie.module;[
<!ENTITY % local.seealsoie.attrib "">
<!ENTITY % seealsoie.role.attrib "%role.attrib;">

<!ENTITY % seealsoie.element "INCLUDE">
<![%seealsoie.element;[
<!ELEMENT seealsoie %ho; (%ndxterm.char.mix;)*>
<!--end of seealsoie.element-->]]>

<!-- to related IndexEntries -->


<!ENTITY % seealsoie.attlist "INCLUDE">
<![%seealsoie.attlist;[
<!ATTLIST seealsoie
		%linkends.attrib;		%common.attrib;
		%seealsoie.role.attrib;
		%local.seealsoie.attrib;
>
<!--end of seealsoie.attlist-->]]>
<!--end of seealsoie.module-->]]>
<!--end of index.content.module-->]]>

<!-- ...................................................................... -->
<!-- RefEntry ............................................................. -->

<!ENTITY % refentry.content.module "INCLUDE">
<![%refentry.content.module;[
<!ENTITY % refentry.module "INCLUDE">
<![%refentry.module;[
<!ENTITY % local.refentry.attrib "">
<!ENTITY % refentry.role.attrib "%role.attrib;">

<!ENTITY % refentry.element "INCLUDE">
<![%refentry.element;[
<!ELEMENT refentry %ho; (beginpage?,
                    (%ndxterm.class;)*,
                    refentryinfo?, refmeta?, (remark|%link.char.class;)*,
                    refnamediv+, refsynopsisdiv?, (refsect1+|refsection+))
		%ubiq.inclusion;>
<!--end of refentry.element-->]]>

<!ENTITY % refentry.attlist "INCLUDE">
<![%refentry.attlist;[
<!ATTLIST refentry
		%status.attrib;
		%common.attrib;
		%refentry.role.attrib;
		%local.refentry.attrib;
>
<!--end of refentry.attlist-->]]>
<!--end of refentry.module-->]]>

<!ENTITY % refmeta.module "INCLUDE">
<![%refmeta.module;[
<!ENTITY % local.refmeta.attrib "">
<!ENTITY % refmeta.role.attrib "%role.attrib;">

<!ENTITY % refmeta.element "INCLUDE">
<![%refmeta.element;[
<!ELEMENT refmeta %ho; ((%ndxterm.class;)*,
                   refentrytitle, manvolnum?, refmiscinfo*,
                   (%ndxterm.class;)*)
		%beginpage.exclusion;>
<!--end of refmeta.element-->]]>

<!ENTITY % refmeta.attlist "INCLUDE">
<![%refmeta.attlist;[
<!ATTLIST refmeta
		%common.attrib;
		%refmeta.role.attrib;
		%local.refmeta.attrib;
>
<!--end of refmeta.attlist-->]]>
<!--end of refmeta.module-->]]>

<!ENTITY % refmiscinfo.module "INCLUDE">
<![%refmiscinfo.module;[
<!ENTITY % local.refmiscinfo.attrib "">
<!ENTITY % refmiscinfo.role.attrib "%role.attrib;">

<!ENTITY % refmiscinfo.element "INCLUDE">
<![%refmiscinfo.element;[
<!ELEMENT refmiscinfo %ho; (%docinfo.char.mix;)*>
<!--end of refmiscinfo.element-->]]>

<!-- Class: Freely assignable parameter; no default -->


<!ENTITY % refmiscinfo.attlist "INCLUDE">
<![%refmiscinfo.attlist;[
<!ATTLIST refmiscinfo
		class		CDATA		#IMPLIED
		%common.attrib;
		%refmiscinfo.role.attrib;
		%local.refmiscinfo.attrib;
>
<!--end of refmiscinfo.attlist-->]]>
<!--end of refmiscinfo.module-->]]>

<!ENTITY % refnamediv.module "INCLUDE">
<![%refnamediv.module;[
<!ENTITY % local.refnamediv.attrib "">
<!ENTITY % refnamediv.role.attrib "%role.attrib;">

<!ENTITY % refnamediv.element "INCLUDE">
<![%refnamediv.element;[
<!ELEMENT refnamediv %ho; (refdescriptor?, refname+, refpurpose, refclass*,
		(remark|%link.char.class;)*)>
<!--end of refnamediv.element-->]]>

<!ENTITY % refnamediv.attlist "INCLUDE">
<![%refnamediv.attlist;[
<!ATTLIST refnamediv
		%common.attrib;
		%refnamediv.role.attrib;
		%local.refnamediv.attrib;
>
<!--end of refnamediv.attlist-->]]>
<!--end of refnamediv.module-->]]>

<!ENTITY % refdescriptor.module "INCLUDE">
<![%refdescriptor.module;[
<!ENTITY % local.refdescriptor.attrib "">
<!ENTITY % refdescriptor.role.attrib "%role.attrib;">

<!ENTITY % refdescriptor.element "INCLUDE">
<![%refdescriptor.element;[
<!ELEMENT refdescriptor %ho; (%refname.char.mix;)*>
<!--end of refdescriptor.element-->]]>

<!ENTITY % refdescriptor.attlist "INCLUDE">
<![%refdescriptor.attlist;[
<!ATTLIST refdescriptor
		%common.attrib;
		%refdescriptor.role.attrib;
		%local.refdescriptor.attrib;
>
<!--end of refdescriptor.attlist-->]]>
<!--end of refdescriptor.module-->]]>

<!ENTITY % refname.module "INCLUDE">
<![%refname.module;[
<!ENTITY % local.refname.attrib "">
<!ENTITY % refname.role.attrib "%role.attrib;">

<!ENTITY % refname.element "INCLUDE">
<![%refname.element;[
<!ELEMENT refname %ho; (%refname.char.mix;)*>
<!--end of refname.element-->]]>

<!ENTITY % refname.attlist "INCLUDE">
<![%refname.attlist;[
<!ATTLIST refname
		%common.attrib;
		%refname.role.attrib;
		%local.refname.attrib;
>
<!--end of refname.attlist-->]]>
<!--end of refname.module-->]]>

<!ENTITY % refpurpose.module "INCLUDE">
<![%refpurpose.module;[
<!ENTITY % local.refpurpose.attrib "">
<!ENTITY % refpurpose.role.attrib "%role.attrib;">

<!ENTITY % refpurpose.element "INCLUDE">
<![%refpurpose.element;[
<!ELEMENT refpurpose %ho; (%refinline.char.mix;)*>
<!--end of refpurpose.element-->]]>

<!ENTITY % refpurpose.attlist "INCLUDE">
<![%refpurpose.attlist;[
<!ATTLIST refpurpose
		%common.attrib;
		%refpurpose.role.attrib;
		%local.refpurpose.attrib;
>
<!--end of refpurpose.attlist-->]]>
<!--end of refpurpose.module-->]]>

<!ENTITY % refclass.module "INCLUDE">
<![%refclass.module;[
<!ENTITY % local.refclass.attrib "">
<!ENTITY % refclass.role.attrib "%role.attrib;">

<!ENTITY % refclass.element "INCLUDE">
<![%refclass.element;[
<!ELEMENT refclass %ho; (%refclass.char.mix;)*>
<!--end of refclass.element-->]]>

<!ENTITY % refclass.attlist "INCLUDE">
<![%refclass.attlist;[
<!ATTLIST refclass
		%common.attrib;
		%refclass.role.attrib;
		%local.refclass.attrib;
>
<!--end of refclass.attlist-->]]>
<!--end of refclass.module-->]]>

<!ENTITY % refsynopsisdiv.module "INCLUDE">
<![%refsynopsisdiv.module;[
<!ENTITY % local.refsynopsisdiv.attrib "">
<!ENTITY % refsynopsisdiv.role.attrib "%role.attrib;">

<!ENTITY % refsynopsisdiv.element "INCLUDE">
<![%refsynopsisdiv.element;[
<!ELEMENT refsynopsisdiv %ho; (refsynopsisdivinfo?, (%refsect.title.content;)?,
		(((%refcomponent.mix;)+, refsect2*) | (refsect2+)))>
<!--end of refsynopsisdiv.element-->]]>

<!ENTITY % refsynopsisdiv.attlist "INCLUDE">
<![%refsynopsisdiv.attlist;[
<!ATTLIST refsynopsisdiv
		%common.attrib;
		%refsynopsisdiv.role.attrib;
		%local.refsynopsisdiv.attrib;
>
<!--end of refsynopsisdiv.attlist-->]]>
<!--end of refsynopsisdiv.module-->]]>

<!ENTITY % refsection.module "INCLUDE">
<![%refsection.module;[
<!ENTITY % local.refsection.attrib "">
<!ENTITY % refsection.role.attrib "%role.attrib;">

<!ENTITY % refsection.element "INCLUDE">
<![%refsection.element;[
<!ELEMENT refsection %ho; (refsectioninfo?, (%refsect.title.content;),
		(((%refcomponent.mix;)+, refsection*) | refsection+))>
<!--end of refsection.element-->]]>

<!ENTITY % refsection.attlist "INCLUDE">
<![%refsection.attlist;[
<!ATTLIST refsection
		%status.attrib;
		%common.attrib;
		%refsection.role.attrib;
		%local.refsection.attrib;
>
<!--end of refsection.attlist-->]]>
<!--end of refsection.module-->]]>

<!ENTITY % refsect1.module "INCLUDE">
<![%refsect1.module;[
<!ENTITY % local.refsect1.attrib "">
<!ENTITY % refsect1.role.attrib "%role.attrib;">

<!ENTITY % refsect1.element "INCLUDE">
<![%refsect1.element;[
<!ELEMENT refsect1 %ho; (refsect1info?, (%refsect.title.content;),
		(((%refcomponent.mix;)+, refsect2*) | refsect2+))>
<!--end of refsect1.element-->]]>

<!ENTITY % refsect1.attlist "INCLUDE">
<![%refsect1.attlist;[
<!ATTLIST refsect1
		%status.attrib;
		%common.attrib;
		%refsect1.role.attrib;
		%local.refsect1.attrib;
>
<!--end of refsect1.attlist-->]]>
<!--end of refsect1.module-->]]>

<!ENTITY % refsect2.module "INCLUDE">
<![%refsect2.module;[
<!ENTITY % local.refsect2.attrib "">
<!ENTITY % refsect2.role.attrib "%role.attrib;">

<!ENTITY % refsect2.element "INCLUDE">
<![%refsect2.element;[
<!ELEMENT refsect2 %ho; (refsect2info?, (%refsect.title.content;),
	(((%refcomponent.mix;)+, refsect3*) | refsect3+))>
<!--end of refsect2.element-->]]>

<!ENTITY % refsect2.attlist "INCLUDE">
<![%refsect2.attlist;[
<!ATTLIST refsect2
		%status.attrib;
		%common.attrib;
		%refsect2.role.attrib;
		%local.refsect2.attrib;
>
<!--end of refsect2.attlist-->]]>
<!--end of refsect2.module-->]]>

<!ENTITY % refsect3.module "INCLUDE">
<![%refsect3.module;[
<!ENTITY % local.refsect3.attrib "">
<!ENTITY % refsect3.role.attrib "%role.attrib;">

<!ENTITY % refsect3.element "INCLUDE">
<![%refsect3.element;[
<!ELEMENT refsect3 %ho; (refsect3info?, (%refsect.title.content;),
	(%refcomponent.mix;)+)>
<!--end of refsect3.element-->]]>

<!ENTITY % refsect3.attlist "INCLUDE">
<![%refsect3.attlist;[
<!ATTLIST refsect3
		%status.attrib;
		%common.attrib;
		%refsect3.role.attrib;
		%local.refsect3.attrib;
>
<!--end of refsect3.attlist-->]]>
<!--end of refsect3.module-->]]>
<!--end of refentry.content.module-->]]>

<!-- ...................................................................... -->
<!-- Article .............................................................. -->

<!ENTITY % article.module "INCLUDE">
<![%article.module;[
<!-- An Article is a chapter-level, stand-alone document that is often,
     but need not be, collected into a Book. -->

<!ENTITY % local.article.attrib "">
<!ENTITY % article.role.attrib "%role.attrib;">

<!ENTITY % article.element "INCLUDE">
<![%article.element;[
<!ELEMENT article %ho; ((%div.title.content;)?, articleinfo?, tocchap?, lot*,
			(%bookcomponent.content;),
			((%nav.class;) | (%appendix.class;) | ackno)*)
		%ubiq.inclusion;>
<!--end of article.element-->]]>

<!-- Class: Indicates the type of a particular article;
		all articles have the same structure and general purpose.
		No default. -->
<!-- ParentBook: ID of the enclosing Book -->


<!ENTITY % article.attlist "INCLUDE">
<![%article.attlist;[
<!ATTLIST article
		class		(journalarticle
				|productsheet
				|whitepaper
				|techreport
                                |specification
				|faq)		#IMPLIED
		parentbook	IDREF		#IMPLIED
		%status.attrib;
		%common.attrib;
		%article.role.attrib;
		%local.article.attrib;
>
<!--end of article.attlist-->]]>
<!--end of article.module-->]]>

<!-- End of DocBook document hierarchy module V4.4 ........................ -->
<!-- ...................................................................... -->
