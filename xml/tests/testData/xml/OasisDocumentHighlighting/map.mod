<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Map                                          -->
<!--  VERSION:   1.1                                               -->
<!--  DATE:      June 2006                                         -->
<!--                                                               -->
<!-- ============================================================= -->

<!-- ============================================================= -->
<!--                    PUBLIC DOCUMENT TYPE DEFINITION            -->
<!--                    TYPICAL INVOCATION                         -->
<!--                                                               -->
<!--  Refer to this file by the following public identifier or an 
      appropriate system identifier 
PUBLIC "-//OASIS//ELEMENTS DITA Map//EN"
      Delivered as file "map.mod"                                  -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Declaring the elements and specialization         -->
<!--             attributes for the DITA Maps                      -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2001                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2005.11.15 RDA: Corrected public ID in the comment above   -->
<!--    2005.11.15 RDA: Removed old declaration for topicreftypes  -->
<!--                      entity                                   -->
<!--    2006.06.06 RDA: Removed default locktitle="yes" from       -->
<!--                      %topicref-atts-no-toc;                   -->
<!--                    Remove keyword declaration                 -->
<!--                    Add reference to commonElements            -->
<!--                    Add title element to map                   -->
<!--                    Add data element to topicmeta              -->
<!--                    Remove shortdesc declaration               -->
<!--    2006.06.07 RDA: Make universal attributes universal        -->
<!--                      (DITA 1.1 proposal #12)                  -->
<!--    2006.06.14 RDA: Add dir attribute to localization-atts     -->
<!--    2006.06.14 RDA: Add outputclass attribute to most elemetns -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                   ARCHITECTURE ENTITIES                       -->
<!-- ============================================================= -->

<!-- default namespace prefix for DITAArchVersion attribute can be
     overridden through predefinition in the document type shell   -->
<!ENTITY % DITAArchNSPrefix
                       "ditaarch"                                    >

<!-- must be instanced on each topic type                          -->
<!ENTITY % arch-atts "
             xmlns:%DITAArchNSPrefix; 
                        CDATA                              #FIXED
                       'http://dita.oasis-open.org/architecture/2005/'
             %DITAArchNSPrefix;:DITAArchVersion
                        CDATA                              #FIXED
                       '1.1'"                                        >


<!-- ============================================================= -->
<!--                   ELEMENT NAME ENTITIES                       -->
<!-- ============================================================= -->


<!ENTITY % map         "map"                                         >
<!ENTITY % anchor      "anchor"                                      >
<!ENTITY % linktext    "linktext"                                    >
<!ENTITY % navref      "navref"                                      >
<!ENTITY % relcell     "relcell"                                     >
<!ENTITY % relcolspec  "relcolspec"                                  >
<!ENTITY % relheader   "relheader"                                   >
<!ENTITY % relrow      "relrow"                                      >
<!ENTITY % reltable    "reltable"                                    >
<!ENTITY % searchtitle "searchtitle"                                 >
<!ENTITY % shortdesc   "shortdesc"                                   >
<!ENTITY % topicmeta   "topicmeta"                                   >
<!ENTITY % topicref    "topicref"                                    >


<!-- ============================================================= -->
<!--                    ENTITY DECLARATIONS FOR ATTRIBUTE VALUES   -->
<!-- ============================================================= -->


<!--                    DATE FORMAT                                -->
<!-- Copied into metaDecl.mod -->
<!--<!ENTITY % date-format  'CDATA'                               >-->


<!-- ============================================================= -->
<!--                    COMMON ATTLIST SETS                        -->
<!-- ============================================================= -->


<!ENTITY % topicref-atts 
            'collection-type 
                        (choice | unordered | sequence | 
                         family)                          #IMPLIED
             type       CDATA                             #IMPLIED
             scope      (local | peer | external)         #IMPLIED
             locktitle  (yes|no)                          #IMPLIED
             format     CDATA                             #IMPLIED
             linking    (none | normal | sourceonly | targetonly)
                                                          #IMPLIED
             toc        (yes | no)                        #IMPLIED
             print      (yes | no | printonly)            #IMPLIED
             search     (yes | no)                        #IMPLIED
             chunk      CDATA                             #IMPLIED'  >


<!ENTITY % topicref-atts-no-toc 
            'collection-type
                        (choice | unordered | sequence | 
                         family)                          #IMPLIED
             type       CDATA                             #IMPLIED
             scope      (local | peer | external)         #IMPLIED
             locktitle  (yes|no)                          #IMPLIED
             format     CDATA                             #IMPLIED
             linking    (targetonly | sourceonly | 
                         normal | none)                   #IMPLIED
             toc        (yes | no)                        "no"
             print      (yes | no | printonly)            #IMPLIED
             search     (yes | no)                        #IMPLIED
             chunk      CDATA                             #IMPLIED'  >



<!-- ============================================================= -->
<!--                    MODULES CALLS                              -->
<!-- ============================================================= -->


<!--                      Content elements common to map and topic -->
<!ENTITY % commonElements      PUBLIC 
"-//OASIS//ELEMENTS DITA Common Elements//EN" "commonElements.mod"   >
%commonElements;

<!--                      MetaData Elements                        -->
<!ENTITY % metaXML      PUBLIC 
"-//OASIS//ELEMENTS DITA Metadata//EN" "metaDecl.mod"                >
%metaXML;
  
<!-- ============================================================= -->
<!--                    DOMAINS ATTRIBUTE OVERRIDE                 -->
<!-- ============================================================= -->

<!ENTITY included-domains ""                                         >
  
<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->





<!--                    LONG NAME: Map                             -->
<!ELEMENT  map          ((%title;)?, (%topicmeta;)?, 
                         (%navref;|%anchor;|%topicref;|%reltable;|
                          %data.elements.incl;)* )                   >
<!ATTLIST  map 
             title      CDATA                             #IMPLIED
             id         ID                                #IMPLIED
             conref     CDATA                             #IMPLIED
             anchorref  CDATA                             #IMPLIED
             outputclass 
                        CDATA                             #IMPLIED
             %localization-atts;
             %arch-atts;
             domains    CDATA                  "&included-domains;" 
             %topicref-atts;
             %select-atts;                                           >


<!--                    LONG NAME: Navigation Reference            -->
<!ELEMENT  navref       EMPTY                                        >
<!ATTLIST  navref
             %univ-atts;
             outputclass 
                        CDATA                             #IMPLIED
             mapref     CDATA                             #IMPLIED   >


<!--                    LONG NAME: Topic Reference                 -->
<!ELEMENT  topicref     ((%topicmeta;)?, 
                         (%topicref; | %navref; | %anchor; |
                          %data.elements.incl;)* )                   >
<!ATTLIST  topicref
             navtitle   CDATA                             #IMPLIED
             href       CDATA                             #IMPLIED
             keyref     CDATA                             #IMPLIED
             query      CDATA                             #IMPLIED
             copy-to    CDATA                             #IMPLIED
             outputclass 
                        CDATA                             #IMPLIED
             %topicref-atts;
             %univ-atts;                                             >


<!--                    LONG NAME: Anchor                          -->
<!ELEMENT  anchor       EMPTY                                        >
<!ATTLIST  anchor
             outputclass 
                        CDATA                             #IMPLIED
             %localization-atts;
             id         ID                                #REQUIRED   
             conref     CDATA                             #IMPLIED    
             %select-atts;                                           >


<!--                    LONG NAME: Relationship Table              -->
<!ELEMENT  reltable     ((%topicmeta;)?, (%relheader;)?, 
                         (%relrow;)+)                                >
<!ATTLIST  reltable        
             title      CDATA                             #IMPLIED
             outputclass 
                        CDATA                             #IMPLIED
             %topicref-atts-no-toc;
             %univ-atts;                                             >


<!--                    LONG NAME: Relationship Header             -->
<!ELEMENT  relheader    (%relcolspec;)+                              >
<!ATTLIST  relheader
             %univ-atts;                                             >        


<!--                    LONG NAME: Relationship Column Specification
                                                                   -->
<!ELEMENT  relcolspec   (%topicmeta;)?                               >
<!ATTLIST  relcolspec
             outputclass 
                        CDATA                             #IMPLIED
             %topicref-atts;
             %univ-atts;                                             >


<!--                    LONG NAME: Relationship Table Row          -->
<!ELEMENT relrow        (%relcell;)*                                 >
<!ATTLIST relrow
             outputclass 
                        CDATA                             #IMPLIED
             %univ-atts;                                             >


<!--                    LONG NAME: Relationship Table Cell         -->
<!ELEMENT relcell         ((%topicref;|%data.elements.incl;)*)>
<!ATTLIST relcell
             outputclass 
                        CDATA                             #IMPLIED
             %topicref-atts;
             %univ-atts;                                             >


<!--                    LONG NAME: Topic Metadata                  -->
<!ELEMENT  topicmeta    ((%linktext;)?, (%searchtitle;)?, 
                         (%shortdesc;)?, (%author;)*, (%source;)?, 
                         (%publisher;)?, (%copyright;)*, 
                         (%critdates;)?, (%permissions;)?, 
                         (%audience;)*, (%category;)*, 
                         (%keywords;)*, (%prodinfo;)*, (%othermeta;)*, 
                         (%resourceid;)*, 
                         (%data.elements.incl; | 
                          %foreign.unknown.incl;)*)                  >
<!ATTLIST  topicmeta
             lockmeta   (yes | no)                        #IMPLIED
             %univ-atts;                                             >


<!--                    LONG NAME: Link Text                       -->
<!ELEMENT  linktext     (%words.cnt;)*                               >
<!ATTLIST  linktext
             outputclass 
                        CDATA                             #IMPLIED
             %univ-atts;                                             >


<!--                    LONG NAME: Search Title                    -->
<!ELEMENT  searchtitle  (%words.cnt;)*                               >
<!ATTLIST  searchtitle
             outputclass 
                        CDATA                             #IMPLIED
             %univ-atts;                                             >


<!--                    LONG NAME: Short Description               -->
<!--<!ELEMENT  shortdesc    (%words.cnt;)*                        >-->
         

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->


<!ATTLIST map         %global-atts;  class CDATA "- map/map "        >
<!ATTLIST navref      %global-atts;  class CDATA "- map/navref "     >
<!ATTLIST topicref    %global-atts;  class CDATA "- map/topicref "   >
<!ATTLIST anchor      %global-atts;  class CDATA "- map/anchor "     >
<!ATTLIST reltable    %global-atts;  class CDATA "- map/reltable "   >
<!ATTLIST relheader   %global-atts;  class CDATA "- map/relheader "  >
<!ATTLIST relcolspec  %global-atts;  class CDATA "- map/relcolspec " >
<!ATTLIST relrow      %global-atts;  class CDATA "- map/relrow "     >
<!ATTLIST relcell     %global-atts;  class CDATA "- map/relcell "    >
<!ATTLIST topicmeta   %global-atts;  class CDATA "- map/topicmeta "  >
<!ATTLIST linktext    %global-atts;  class CDATA "- map/linktext "   >
<!ATTLIST searchtitle %global-atts;  class CDATA "- map/searchtitle ">

<!-- Shortdesc in topic uses topic/shortdesc so this one must be 
     included, even though the element is common. -->
<!ATTLIST shortdesc   %global-atts;  class CDATA "- map/shortdesc "  >


<!-- ================== End DITA Map ============================= -->