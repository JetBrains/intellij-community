<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA DITA Topic                                   -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Topic//EN"
      Delivered as file "topic.mod"                                -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Declaring the elements and specialization         -->
<!--             attributes for the base Topic type                -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2001                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2005.11.15 RDA: Corrected the public ID for tblDecl.mod    -->
<!--    2005.11.15 RDA: Removed old declaration for topicreftypes  -->
<!--                    entity                                     -->
<!--    2005.11.15 RDA: Corrected the PURPOSE in this comment      -->
<!--    2005.11.15 RDA: Corrected Long Names for alt, indextermref -->
<!--    2006.06.06 RDA: Bug fixes:                                 -->
<!--                    Added xref and fn to fig.cnt               -->
<!--                    Remove xmlns="" from global-atts           -->
<!--    2006.06.06 RDA: Moved shared items to commonElements file  -->
<!--    2006.06.07 RDA: Added <abstract> element                   -->
<!--    2006.06.07 RDA: Make universal attributes universal        -->
<!--                      (DITA 1.1 proposal #12)                  -->
<!--    2006.06.14 RDA: Add dir attribute to localization-atts     -->
<!--    2006.06.20 RDA: defn.cnt now explicitly sets its content   -->
<!--    2006.07.06 RDA: Moved class attributes in from topicAttr   -->
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

<!--                    Definitions of declared elements           -->
<!ENTITY % topicDefns   PUBLIC 
                       "-//OASIS//ENTITIES DITA Topic Definitions//EN" 
                       "topicDefn.ent"                              >
%topicDefns;

<!--                      Content elements common to map and topic -->
<!ENTITY % commonElements      PUBLIC 
"-//OASIS//ELEMENTS DITA Common Elements//EN" "commonElements.mod"   >
%commonElements;

<!--                       MetaData Elements, plus indexterm       -->
<!ENTITY % metaXML         PUBLIC 
"-//OASIS//ELEMENTS DITA Metadata//EN" 
"metaDecl.mod"                                                       >
%metaXML;


<!-- ============================================================= -->
<!--                    ENTITY DECLARATIONS FOR ATTRIBUTE VALUES   -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                    COMMON ATTLIST SETS                        -->
<!-- ============================================================= -->


<!ENTITY % body.cnt             "%basic.block; | %required-cleanup; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % section.cnt          "#PCDATA | %basic.ph; | %basic.block; | %title; |  %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % section.notitle.cnt  "#PCDATA | %basic.ph; | %basic.block; |             %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">


<!-- ============================================================= -->
<!--                COMMON ENTITY DECLARATIONS                     -->
<!-- ============================================================= -->

<!-- for use within the DTD and supported topics; these will NOT work
     outside of this DTD or dtds that specialize from it!          -->
<!ENTITY nbsp                   "&#160;"                             >


<!-- ============================================================= -->
<!--                    NOTATION DECLARATIONS                      -->
<!-- ============================================================= -->
<!--                    DITA uses the direct reference model; 
                        notations may be added later as required   -->


<!-- ============================================================= -->
<!--                    STRUCTURAL MEMBERS                         -->
<!-- ============================================================= -->


<!ENTITY % info-types    'topic'                                     > 


<!-- ============================================================= -->
<!--                    COMMON ATTLIST SETS                        -->
<!-- ============================================================= -->

<!-- Copied into metaDecl.mod -->
<!--<!ENTITY % date-format 'CDATA'                                >-->

<!ENTITY % rel-atts      
            'type       CDATA                              #IMPLIED
             role      (parent | child | sibling | 
                        friend | next | previous | cousin | 
                        ancestor | descendant | sample | 
                        external | other)                  #IMPLIED
             otherrole  CDATA                              #IMPLIED' >

<!-- ============================================================= -->
<!--                    SPECIALIZATION OF DECLARED ELEMENTS        -->
<!-- ============================================================= -->

<!ENTITY % topic-info-types "%info-types;">


<!-- ============================================================= -->
<!--                    DOMAINS ATTRIBUTE OVERRIDE                 -->
<!-- ============================================================= -->

<!ENTITY included-domains ""                                         >
  

<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->

<!--                    LONG NAME: Topic                           -->
<!ELEMENT topic         ((%title;), (%titlealts;)?,
                         (%shortdesc; | %abstract;)?, 
                         (%prolog;)?, (%body;)?, (%related-links;)?,
                         (%topic-info-types;)* )                     >
<!ATTLIST topic           
             id         ID                                 #REQUIRED
             conref     CDATA                              #IMPLIED
             %select-atts;
             %localization-atts;
             outputclass 
                        CDATA                              #IMPLIED
             %arch-atts;
             domains    CDATA                    "&included-domains;">


<!--                    LONG NAME: Title Alternatives              -->
<!ELEMENT titlealts     ((%navtitle;)?, (%searchtitle;)?)            >
<!ATTLIST titlealts      
             %univ-atts;                                             >


<!--                    LONG NAME: Navigation Title                -->
<!ELEMENT navtitle      (%words.cnt;)*                               >
<!ATTLIST navtitle     
             %univ-atts;                                             >

<!--                    LONG NAME: Search Title                    -->
<!ELEMENT searchtitle   (%words.cnt;)*                               >
<!ATTLIST searchtitle     
             %univ-atts;                                             >


<!--                    LONG NAME: Abstract                        -->
<!ELEMENT abstract      (%section.notitle.cnt; | %shortdesc;)*       >
<!ATTLIST abstract         
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Short Description               -->
<!--
<!ELEMENT shortdesc     (%title.cnt;)*                               >
<!ATTLIST shortdesc    
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >
-->


<!--                    LONG NAME: Body                            -->
<!ELEMENT body          (%body.cnt; | %section; | %example;)*        >
<!ATTLIST body            
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: No Topic nesting                -->
<!ELEMENT no-topic-nesting EMPTY                                     >


<!--                    LONG NAME: Section                         -->
<!ELEMENT section       (%section.cnt;)*                             >
<!ATTLIST section         
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Example                         -->
<!ELEMENT example       (%section.cnt;)*                             >
<!ATTLIST example         
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Description                     -->
<!--                    Desc is used in context with figure and 
                        table titles and also for content models 
                        within linkgroup and object (for 
                        accessibility)                             -->
<!--
<!ELEMENT desc          (%desc.cnt;)*                                >
<!ATTLIST desc           
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >
-->

<!-- ============================================================= -->
<!--                    PROLOG (METADATA FOR TOPICS)               -->
<!--                    TYPED DATA ELEMENTS                        -->
<!-- ============================================================= -->
<!--                    typed content definitions                  -->
<!--                    typed, localizable content                 -->

<!--                    LONG NAME: Prolog                          -->
<!ELEMENT prolog        ((%author;)*, (%source;)?, (%publisher;)?,
                         (%copyright;)*, (%critdates;)?,
                         (%permissions;)?, (%metadata;)*, 
                         (%resourceid;)*,
                         (%data.elements.incl; | 
                          %foreign.unknown.incl;)*)                  >
<!ATTLIST prolog        
             %univ-atts;                                             >


<!--                    LONG NAME: Metadata                        -->
<!ELEMENT metadata       ((%audience;)*, (%category;)*, (%keywords;)*,
                          (%prodinfo;)*, (%othermeta;)*, 
                          (%data.elements.incl; |
                           %foreign.unknown.incl;)*)                 >
<!ATTLIST metadata
              %univ-atts;        
              mapkeyref CDATA                             #IMPLIED   >



<!-- ============================================================= -->
<!--                    BASIC DOCUMENT ELEMENT DECLARATIONS        -->
<!--                    (rich text)                                -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                   BASE FORM PHRASE TYPES                      -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                      LINKING GROUPING                         -->
<!-- ============================================================= -->


<!--                    LONG NAME: Related Links                   -->
<!ELEMENT related-links (%link; | %linklist; | %linkpool;)+          >
<!ATTLIST related-links  
             %rel-atts;
             %univ-atts;
             format     CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Link                            -->
<!ELEMENT link          ((%linktext;)?, (%desc;)?)                   >
<!ATTLIST link            
             href       CDATA                            #IMPLIED
             keyref     CDATA                            #IMPLIED
             query      CDATA                            #IMPLIED
             %rel-atts;
             %univ-atts;
             format     CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Link Text                       -->
<!ELEMENT linktext      (%words.cnt;)*                               >
<!ATTLIST linktext        
             %univ-atts;                                             >


<!--                    LONG NAME: Link List                       -->
<!ELEMENT linklist      ((%title;)?, (%desc;)?,
                         (%linklist; | %link;)*, (%linkinfo;)?)      >
<!ATTLIST linklist        
            collection-type 
                        (unordered | sequence | choice |
                         tree | family)                   #IMPLIED
             duplicates (yes | no)                        #IMPLIED
                          mapkeyref CDATA #IMPLIED
             %rel-atts;
             %univ-atts;
             spectitle  CDATA                            #IMPLIED
             format     CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Link Information                -->
<!ELEMENT linkinfo      (%desc.cnt;)*                                >
<!ATTLIST linkinfo        
             %univ-atts;                                             >


<!--                    LONG NAME: Link Pool                       -->
<!ELEMENT linkpool      (%linkpool; | %link;)*                       >
<!ATTLIST linkpool        
             collection-type 
                        (unordered | sequence | choice |
                         tree | family)                   #IMPLIED
             duplicates (yes | no)                        #IMPLIED
             mapkeyref  CDATA                             #IMPLIED
             %rel-atts;
             %univ-atts;
             format     CDATA   #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass 
                        CDATA                            #IMPLIED    >



<!-- ============================================================= -->
<!--                    MODULES CALLS                              -->
<!-- ============================================================= -->


<!--                      Table Elements                           -->
<!--    2005.11.15 RDA: Corrected the public ID for tblDecl.mod,   -->
<!--  from the old value "-//OASIS//ELEMENTS DITA CALS Tables//EN" -->
<!-- Tables are now part of commonElements -->
<!--<!ENTITY % tableXML       PUBLIC  
"-//OASIS//ELEMENTS DITA Exchange Table Model//EN" 
"tblDecl.mod"                                                        >
%tableXML;-->


<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->
            
<!ATTLIST abstract  %global-atts;  class CDATA "- topic/abstract "   >
<!ATTLIST body      %global-atts;  class CDATA "- topic/body "       >
<!ATTLIST example   %global-atts;  class CDATA "- topic/example "    >
<!ATTLIST link      %global-atts;  class CDATA "- topic/link "       >
<!ATTLIST linkinfo  %global-atts;  class CDATA "- topic/linkinfo "   >
<!ATTLIST linklist  %global-atts;  class CDATA "- topic/linklist "   >
<!ATTLIST linkpool  %global-atts;  class CDATA "- topic/linkpool "   >
<!ATTLIST linktext  %global-atts;  class CDATA "- topic/linktext "   >
<!ATTLIST metadata  %global-atts;  class CDATA "- topic/metadata "   >
<!ATTLIST navtitle  %global-atts;  class CDATA "- topic/navtitle "   >
<!ATTLIST no-topic-nesting 
                    %global-atts;  class CDATA "- topic/no-topic-nesting ">
<!ATTLIST prolog    %global-atts;  class CDATA "- topic/prolog "     >
<!ATTLIST related-links 
                    %global-atts;  class CDATA "- topic/related-links ">
<!ATTLIST searchtitle 
                    %global-atts;  class CDATA "- topic/searchtitle ">
<!ATTLIST section   %global-atts;  class CDATA "- topic/section "    >
<!ATTLIST titlealts %global-atts;  class CDATA "- topic/titlealts "  >
<!ATTLIST topic     %global-atts;  class CDATA "- topic/topic "      >

<!-- Shortdesc in map uses map/shortdesc so this one must be 
     included, even though the element is common. -->
<!ATTLIST shortdesc   %global-atts;  class CDATA "- topic/shortdesc ">

<!-- ================== End DITA Topic  ========================== -->