<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Common Elements                              -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Common Elements//EN"
      Delivered as file "commonElements.mod"                       -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Declaring the elements and specialization         -->
<!--             attributes for content elements used in both      -->
<!--             topics and maps.                                  -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             June 2006                                         -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2006.06.06 RDA: Add data element                           -->
<!--    2006.06.07 RDA: Add @scale to image                        -->
<!--    2006.06.07 RDA: Add index-base element                     -->
<!--    2006.06.07 RDA: Make universal attributes universal        -->
<!--                      (DITA 1.1 proposal #12)                  -->
<!--    2006.06.07 RDA: Add unknown element                        -->
<!--    2006.06.14 RDA: Add dir attribute to localization-atts     -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                    ELEMENT NAME ENTITIES                      -->
<!-- ============================================================= -->

<!ENTITY % commonDefns   PUBLIC 
                       "-//OASIS//ENTITIES DITA Common Elements//EN" 
                       "commonElements.ent"                          >
%commonDefns;

<!-- ============================================================= -->
<!--                    COMMON ATTLIST SETS                        -->
<!-- ============================================================= -->


<!--                   Phrase/inline elements of various classes   -->
<!ENTITY % basic.ph    "%ph; | %term; | %xref; | %cite; | %q; |
                        %boolean; | %state; | %keyword; | %tm;"      >

<!--                   Elements common to most body-like contexts  -->
<!ENTITY % basic.block "%p; | %lq; | %note; | %dl; | %ul; | %ol;|  
                        %sl; | %pre; | %lines; | %fig; | %image; | 
                        %object; |  %table; | %simpletable;">

<!-- class groupings to preserve in a schema -->

<!ENTITY % basic.phandblock     "%basic.ph; | %basic.block;"         >


<!-- Exclusions: models modified by removing excluded content      -->
<!ENTITY % basic.ph.noxref
                      "%ph;|%term;|              %q;|%boolean;|%state;|%keyword;|%tm;">
<!ENTITY % basic.ph.notm
                      "%ph;|%term;|%xref;|%cite;|%q;|%boolean;|%state;|%keyword;     ">


<!ENTITY % basic.block.notbl
                      "%p;|%lq;|%note;|%dl;|%ul;|%ol;|%sl;|%pre;|%lines;|%fig;|%image;|%object;">
<!ENTITY % basic.block.nonote
                      "%p;|%lq;|       %dl;|%ul;|%ol;|%sl;|%pre;|%lines;|%fig;|%image;|%object;|%table;|%simpletable;">
<!ENTITY % basic.block.nopara
                      "    %lq;|%note;|%dl;|%ul;|%ol;|%sl;|%pre;|%lines;|%fig;|%image;|%object;|%table;|%simpletable;">
<!ENTITY % basic.block.nolq
                      "%p;|     %note;|%dl;|%ul;|%ol;|%sl;|%pre;|%lines;|%fig;|%image;|%object;|%table;|%simpletable;">
<!ENTITY % basic.block.notbnofg
                      "%p;|%lq;|%note;|%dl;|%ul;|%ol;|%sl;|%pre;|%lines;|      %image;|%object;">
<!ENTITY % basic.block.notbfgobj
                      "%p;|%lq;|%note;|%dl;|%ul;|%ol;|%sl;|%pre;|%lines;|      %image;">


<!-- Inclusions: defined sets that can be added into appropriate models -->
<!ENTITY % txt.incl             '%draft-comment;|%required-cleanup;|%fn;|%indextermref;|%indexterm;'>

<!-- Metadata elements intended for specialization -->
<!ENTITY % data.elements.incl   "%data;|%data-about;"                >
<!ENTITY % foreign.unknown.incl "%foreign;|%unknown;"                >

<!-- Predefined content model groups, based on the previous, element-only categories: -->
<!-- txt.incl is appropriate for any mixed content definitions (those that have PCDATA) -->
<!-- the context for blocks is implicitly an InfoMaster "containing_division" -->
<!ENTITY % listitem.cnt         "#PCDATA | %basic.ph; | %basic.block; |%itemgroup;| %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % itemgroup.cnt        "#PCDATA | %basic.ph; | %basic.block; |             %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % title.cnt            "#PCDATA | %basic.ph.noxref; | %image; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % xreftext.cnt         "#PCDATA | %basic.ph.noxref; | %image; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % xrefph.cnt           "#PCDATA | %basic.ph.noxref; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % shortquote.cnt       "#PCDATA | %basic.ph; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % para.cnt             "#PCDATA | %basic.ph; | %basic.block.nopara; | %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % note.cnt             "#PCDATA | %basic.ph; | %basic.block.nonote; | %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % longquote.cnt        "#PCDATA | %basic.ph; | %basic.block.nolq;   | %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % tblcell.cnt          "#PCDATA | %basic.ph; | %basic.block.notbl;  | %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % desc.cnt             "#PCDATA | %basic.ph; | %basic.block.notbfgobj; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % ph.cnt               "#PCDATA | %basic.ph; | %image;              | %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % fn.cnt               "#PCDATA | %basic.ph; | %basic.block.notbl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % term.cnt             "#PCDATA | %basic.ph; | %image; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % defn.cnt             "#PCDATA | %basic.ph; | %basic.block; |%itemgroup;| %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % pre.cnt              "#PCDATA | %basic.ph; | %txt.incl; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % fig.cnt              "%basic.block.notbnofg; | %simpletable; | %xref; | %fn;| %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % words.cnt            "#PCDATA | %keyword; | %term; | %data.elements.incl; | %foreign.unknown.incl;">
<!ENTITY % data.cnt             "%words.cnt;|%image;|%object;|%ph;|%title;">

<!-- ============================================================= -->
<!--                    COMMON ATTLIST SETS                        -->
<!-- ============================================================= -->

<!-- Copied into metaDecl.mod -->
<!--<!ENTITY % date-format 'CDATA'                                       >-->

<!ENTITY % display-atts  
            'scale     (50|60|70|80|90|100|110|120|140|160|
                        180|200)                           #IMPLIED
             frame     (top | bottom |topbot | all | 
                        sides | none)                      #IMPLIED
             expanse   (page | column | textline)          #IMPLIED' >

<!-- Provide a default of no attribute extensions -->
<!ENTITY % props-attribute-extensions " ">
<!ENTITY % base-attribute-extensions  " ">

<!ENTITY % filter-atts
            'props      CDATA                              #IMPLIED
             base       CDATA                              #IMPLIED
             platform   CDATA                              #IMPLIED
             product    CDATA                              #IMPLIED
             audience   CDATA                              #IMPLIED
             otherprops CDATA                              #IMPLIED
             %props-attribute-extensions;
             %base-attribute-extensions;                           ' >

<!ENTITY % select-atts   
            '%filter-atts;
             importance 
                       (obsolete | deprecated | optional | 
                        default | low | normal | high | 
                        recommended | required | urgent )  #IMPLIED
             rev        CDATA                              #IMPLIED
             status    (new | changed | deleted | 
                        unchanged)                         #IMPLIED' >

<!ENTITY % id-atts  
            'id         NMTOKEN                            #IMPLIED
             conref     CDATA                              #IMPLIED' >

<!-- Attributes related to localization that are used everywhere   -->
<!ENTITY % localization-atts  
            'translate (yes | no)                          #IMPLIED
             xml:lang   NMTOKEN                            #IMPLIED
             dir       (ltr | rtl | lro | rlo)             #IMPLIED' >
<!-- The following entity should be used when defaulting a new
     element to translate="no", so that other (or new) localization
     attributes will always be included.   -->
<!ENTITY % localization-atts-translate-no  
            'translate (yes | no)                          "no"
             xml:lang   NMTOKEN                            #IMPLIED
             dir       (ltr | rtl | lro | rlo)             #IMPLIED' >
             
<!ENTITY % univ-atts     
            '%id-atts;
             %select-atts;
             %localization-atts;' >
<!ENTITY % univ-atts-translate-no     
            '%id-atts;
             %select-atts;
             %localization-atts-translate-no;' >

<!ENTITY % global-atts    
            'xtrc       CDATA                              #IMPLIED
             xtrf       CDATA                              #IMPLIED'>
             
<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->

<!--                    LONG NAME: Data About                      -->
<!ELEMENT data-about  ((%data;), (%data;|%data-about;)*)>
<!ATTLIST data-about  %univ-atts;
             href       CDATA                            #IMPLIED
             format     CDATA                            #IMPLIED
             type       CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass
                        CDATA                            #IMPLIED    >

<!ENTITY % data-element-atts
            '%univ-atts;
             name       CDATA                            #IMPLIED
             datatype   CDATA                            #IMPLIED
             value      CDATA                            #IMPLIED
             href       CDATA                            #IMPLIED
             format     CDATA                            #IMPLIED
             type       CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass
                        CDATA                            #IMPLIED'   >
             
<!--                    LONG NAME: Data element                    -->
<!ELEMENT data    (%data.cnt;)*>
<!ATTLIST data    %data-element-atts;                                >

<!--                    LONG NAME: Unknown element                 -->
<!ELEMENT unknown ANY>
<!ATTLIST unknown
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Foreign content element         -->
<!ELEMENT foreign ANY>
<!ATTLIST foreign
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Title                           -->
<!--                    This is referenced inside CALS table       -->
<!ELEMENT title         (%title.cnt;)*                               > 
<!ATTLIST title         
             %id-atts;
             %localization-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Short Description               -->
<!ELEMENT shortdesc     (%title.cnt;)*                               >
<!ATTLIST shortdesc    
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Description                     -->
<!--                    Desc is used in context with figure and 
                        table titles and also for content models 
                        within linkgroup and object (for 
                        accessibility)                             -->
<!ELEMENT desc          (%desc.cnt;)*                                >
<!ATTLIST desc           
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!-- ============================================================= -->
<!--                    BASIC DOCUMENT ELEMENT DECLARATIONS        -->
<!--                    (rich text)                                -->
<!-- ============================================================= -->

<!--                    LONG NAME: Paragraph                       -->
<!ELEMENT p             (%para.cnt;)*                                >
<!ATTLIST p              
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Note                            -->
<!ELEMENT note          (%note.cnt;)*                                >
<!ATTLIST note            
             type       (note | tip | fastpath | restriction |
                         important | remember| attention|
                         caution | danger| other)        #IMPLIED             
             spectitle  CDATA                            #IMPLIED
             othertype  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Long Quote (Excerpt)            -->
<!ELEMENT lq            (%longquote.cnt;)*                           >
<!ATTLIST lq              
             href       CDATA                           #IMPLIED
             keyref     CDATA                           #IMPLIED
             type       (external | internal | 
                         bibliographic)                 #IMPLIED
             reftitle   CDATA                           #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Quoted text                     -->
<!ELEMENT q             (%shortquote.cnt;)*                          >
<!ATTLIST q              
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Simple List                     -->
<!ELEMENT sl            (%sli;)+                                     >
<!ATTLIST sl            
             compact    (yes | no)                       #IMPLIED
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Simple List Item                -->
<!ELEMENT sli           (%ph.cnt;)*                                  >
<!ATTLIST sli             
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Unordered List                  -->
<!ELEMENT ul            (%li;)+                                      >
<!ATTLIST ul            
             compact    (yes | no)                       #IMPLIED
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Ordered List                    -->
<!ELEMENT ol            (%li;)+                                      >
<!ATTLIST ol              
             compact    (yes | no)                       #IMPLIED
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: List Item                       -->
<!ELEMENT li            (%listitem.cnt;)*                            >
<!ATTLIST li             
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Item Group                      -->
<!ELEMENT itemgroup     (%itemgroup.cnt;)*                           >
<!ATTLIST itemgroup       
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Definition List                 -->
<!ELEMENT dl            ((%dlhead;)?, (%dlentry;)+)                  >
<!ATTLIST dl              
             compact    (yes | no)                       #IMPLIED
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Definition List Head            -->
<!ELEMENT dlhead        ((%dthd;)?, (%ddhd;)? )                      >
<!ATTLIST dlhead        
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Term Header                     -->
<!ELEMENT dthd          (%title.cnt;)*                               >
<!ATTLIST dthd           
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Definition Header               -->
<!ELEMENT ddhd          (%title.cnt;)*                               >
<!ATTLIST ddhd           
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Definition List Entry           -->
<!ELEMENT dlentry       ((%dt;)+, (%dd;)+ )                          >
<!ATTLIST dlentry       
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >



<!--                    LONG NAME: Definition Term                 -->  
<!ELEMENT dt            (%term.cnt;)*                                >
<!ATTLIST dt            
             keyref     CDATA                           #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Definition Description          -->
<!ELEMENT dd            (%defn.cnt;)*                                >
<!ATTLIST dd           
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Figure                          -->
<!ELEMENT fig           ((%title;)?, (%desc;)?, 
                         (%figgroup; | %fig.cnt;)* )                 >
<!ATTLIST fig          
             %display-atts;
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Figure Group                    -->
<!ELEMENT figgroup      ((%title;)?, 
                         (%figgroup; | %xref; | %fn; | %ph; | 
                          %keyword;)* )                              >
<!ATTLIST figgroup     
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Preformatted Text               -->
<!ELEMENT pre           (%pre.cnt;)*                                 >                                
<!ATTLIST pre          
             %display-atts;
             spectitle  CDATA                            #IMPLIED
             xml:space  (preserve)               #FIXED 'preserve'
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Line Respecting Text            -->
<!ELEMENT lines         (%pre.cnt;)*                                 >
<!ATTLIST lines           
             %display-atts;
             spectitle  CDATA                            #IMPLIED
             xml:space  (preserve)               #FIXED 'preserve'
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!-- ============================================================= -->
<!--                   BASE FORM PHRASE TYPES                      -->
<!-- ============================================================= -->

<!--                    LONG NAME: Keyword                         -->
<!ELEMENT keyword       (#PCDATA | %tm;)*                            >
<!ATTLIST keyword       
             keyref     CDATA                           #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                           #IMPLIED     >


<!--                    LONG NAME: Term                            -->
<!ELEMENT term          (#PCDATA | %tm;)*                            >
<!ATTLIST term          
             keyref     CDATA                           #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Phrase                          -->
<!ELEMENT ph            (%ph.cnt;)*                                  >  
<!ATTLIST ph              
             keyref     CDATA                           #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Trade Mark                      -->
<!ELEMENT tm            (#PCDATA | %tm;)*                            >
<!ATTLIST tm
             %univ-atts;
             trademark  CDATA                           #IMPLIED
             tmowner    CDATA                           #IMPLIED
             tmtype     (tm | reg | service)            #REQUIRED
             tmclass    CDATA                           #IMPLIED     >


<!--                    LONG NAME: Boolean  (deprecated)           -->
<!ELEMENT boolean       EMPTY                                        >
<!ATTLIST boolean           
             state      (yes | no)                      #REQUIRED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: State                           -->
<!--                    A state can have a name and a string value, 
                        even if empty or indeterminate             -->
<!ELEMENT state         EMPTY                                        >
<!ATTLIST state          
             name       CDATA                            #REQUIRED
             value      CDATA                            #REQUIRED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Image Data                      -->
<!ELEMENT image         (%alt;)?                                     >
<!ATTLIST image           
             href       CDATA                            #REQUIRED
             keyref     NMTOKEN                          #IMPLIED
             alt        CDATA                            #IMPLIED
             longdescref 
                        CDATA                            #IMPLIED
             height     NMTOKEN                          #IMPLIED
             width      NMTOKEN                          #IMPLIED
             align      CDATA                            #IMPLIED
             scale      NMTOKEN                          #IMPLIED
             placement  (inline|break)                   "inline"
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Alternate text                  -->
<!ELEMENT alt            (%words.cnt;)*>
<!ATTLIST alt             %univ-atts;
                          outputclass CDATA #IMPLIED
>

<!--                    LONG NAME: Object (Streaming/Executable 
                                   Data)                           -->
<!ELEMENT object        ((%desc;)?, (%param;)*, 
                         (%foreign.unknown.incl;)*)                  >
<!ATTLIST object
             declare    (declare)                        #IMPLIED
             classid    CDATA                            #IMPLIED
             codebase   CDATA                            #IMPLIED
             data       CDATA                            #IMPLIED
             type       CDATA                            #IMPLIED
             codetype   CDATA                            #IMPLIED
             archive    CDATA                            #IMPLIED
             standby    CDATA                            #IMPLIED
             height     NMTOKEN                          #IMPLIED
             width      NMTOKEN                          #IMPLIED
             usemap     CDATA                            #IMPLIED
             name       CDATA                            #IMPLIED
             tabindex   NMTOKEN                          #IMPLIED
             longdescre CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Parameter                       -->
<!ELEMENT param         EMPTY>
<!ATTLIST param
             %univ-atts;
             name       CDATA                            #REQUIRED
             value      CDATA                            #IMPLIED
             valuetype  (data|ref|object)                #IMPLIED
             type       CDATA                            #IMPLIED    >  


<!--                    LONG NAME: Simple Table                    -->
<!ELEMENT simpletable   ((%sthead;)?, (%strow;)+)                    >
<!ATTLIST simpletable     
             relcolwidth 
                        CDATA                            #IMPLIED
             keycol     NMTOKEN                          #IMPLIED
             refcols    NMTOKENS                         #IMPLIED
             %display-atts;
             spectitle  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Simple Table Head               -->
<!ELEMENT sthead        (%stentry;)+                                 >
<!ATTLIST sthead     
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Simple Table Row                -->
<!ELEMENT strow         (%stentry;)*                                 >
<!ATTLIST strow        
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Simple Table Cell (entry)       -->
<!ELEMENT stentry       (%tblcell.cnt;)*                             >
<!ATTLIST stentry 
             specentry  CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Review Comments Block           -->
<!ELEMENT draft-comment (#PCDATA | %basic.phandblock; | 
                         %data.elements.incl; | 
                         %foreign.unknown.incl;)*                    >
<!ATTLIST draft-comment   
             author     CDATA                            #IMPLIED
             time       CDATA                            #IMPLIED
             disposition  
                        (issue | open | accepted | rejected |
                         deferred| duplicate | reopened|
                         unassigned | completed)         #IMPLIED
             %univ-atts-translate-no;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Required Cleanup Block          -->
<!ELEMENT required-cleanup 
                        ANY                                          >
<!ATTLIST required-cleanup 
             remap      CDATA                            #IMPLIED
             %univ-atts-translate-no;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Footnote                        -->
<!ELEMENT fn            (%fn.cnt;)*                                  >
<!ATTLIST fn              
             callout    CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Index Term                      -->
<!ELEMENT indexterm     (%words.cnt;|%indexterm;|%index-base;)*      >
<!ATTLIST indexterm
             keyref     CDATA                             #IMPLIED
             start      CDATA                             #IMPLIED
             end        CDATA                             #IMPLIED
             %univ-atts;                                             >

<!--                    LONG NAME: Index Base                      -->
<!ELEMENT index-base    (%words.cnt;|%indexterm;)*                   >
<!ATTLIST index-base
             keyref     CDATA                             #IMPLIED
             %univ-atts;                                             >

<!--                    LONG NAME: Index term reference            -->
<!ELEMENT indextermref   EMPTY>
<!ATTLIST indextermref    keyref CDATA #REQUIRED
                          %univ-atts;
>

<!--                    LONG NAME: Citation (bibliographic source) -->
<!ELEMENT cite          (%xrefph.cnt;)*                              >
<!ATTLIST cite            
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Cross Reference/Link            -->
<!ELEMENT xref          (%xreftext.cnt; | %desc;)*                   >
<!ATTLIST xref            
             href       CDATA                            #IMPLIED
             keyref     CDATA                            #IMPLIED
             type       CDATA                            #IMPLIED
             %univ-atts;
             format     CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass 
                        CDATA                            #IMPLIED    >


<!ENTITY % tableXML       PUBLIC  
"-//OASIS//ELEMENTS DITA Exchange Table Model//EN" 
"tblDecl.mod"                                                        >
%tableXML;

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->
             
<!ATTLIST alt       %global-atts;  class CDATA "- topic/alt "        >
<!ATTLIST boolean   %global-atts;  class CDATA "- topic/boolean "    >
<!ATTLIST cite      %global-atts;  class CDATA "- topic/cite "       >
<!ATTLIST dd        %global-atts;  class CDATA "- topic/dd "         >
<!ATTLIST data      %global-atts;  class CDATA "- topic/data "       >
<!ATTLIST data-about
                    %global-atts;  class CDATA "- topic/data-about ">
<!ATTLIST ddhd      %global-atts;  class CDATA "- topic/ddhd "       >
<!ATTLIST desc      %global-atts;  class CDATA "- topic/desc "       >
<!ATTLIST dl        %global-atts;  class CDATA "- topic/dl "         >
<!ATTLIST dlentry   %global-atts;  class CDATA "- topic/dlentry "    >
<!ATTLIST dlhead    %global-atts;  class CDATA "- topic/dlhead "     >
<!ATTLIST draft-comment 
                    %global-atts;  class CDATA "- topic/draft-comment ">
<!ATTLIST dt        %global-atts;  class CDATA "- topic/dt "         >
<!ATTLIST dthd      %global-atts;  class CDATA "- topic/dthd "       >
<!ATTLIST fig       %global-atts;  class CDATA "- topic/fig "        >
<!ATTLIST figgroup  %global-atts;  class CDATA "- topic/figgroup "   >
<!ATTLIST fn        %global-atts;  class CDATA "- topic/fn "         >
<!ATTLIST foreign   %global-atts;  class CDATA "- topic/foreign "    >
<!ATTLIST image     %global-atts;  class CDATA "- topic/image "      >
<!ATTLIST indexterm %global-atts;  class CDATA "- topic/indexterm "  >
<!ATTLIST index-base %global-atts;  class CDATA "- topic/index-base ">
<!ATTLIST indextermref 
                    %global-atts;  class CDATA "- topic/indextermref ">
<!ATTLIST itemgroup %global-atts;  class CDATA "- topic/itemgroup "  >
<!ATTLIST keyword   %global-atts;  class CDATA "- topic/keyword "    >
<!ATTLIST li        %global-atts;  class CDATA "- topic/li "         >
<!ATTLIST lines     %global-atts;  class CDATA "- topic/lines "      >
<!ATTLIST lq        %global-atts;  class CDATA "- topic/lq "         >
<!ATTLIST note      %global-atts;  class CDATA "- topic/note "       >
<!ATTLIST object    %global-atts;  class CDATA "- topic/object "     >
<!ATTLIST ol        %global-atts;  class CDATA "- topic/ol "         >
<!ATTLIST p         %global-atts;  class CDATA "- topic/p "          >
<!ATTLIST param     %global-atts;  class CDATA "- topic/param "      >
<!ATTLIST ph        %global-atts;  class CDATA "- topic/ph "         >
<!ATTLIST pre       %global-atts;  class CDATA "- topic/pre "        >
<!ATTLIST q         %global-atts;  class CDATA "- topic/q "          >
<!ATTLIST required-cleanup 
                    %global-atts;  class CDATA "- topic/required-cleanup ">
<!ATTLIST simpletable 
                    %global-atts;  class CDATA "- topic/simpletable ">
<!ATTLIST sl        %global-atts;  class CDATA "- topic/sl "         >
<!ATTLIST sli       %global-atts;  class CDATA "- topic/sli "        >
<!ATTLIST state     %global-atts;  class CDATA "- topic/state "      >
<!ATTLIST stentry   %global-atts;  class CDATA "- topic/stentry "    >
<!ATTLIST sthead    %global-atts;  class CDATA "- topic/sthead "     >
<!ATTLIST strow     %global-atts;  class CDATA "- topic/strow "      >
<!ATTLIST term      %global-atts;  class CDATA "- topic/term "       >
<!ATTLIST title     %global-atts;  class CDATA "- topic/title "      >
<!ATTLIST tm        %global-atts;  class CDATA "- topic/tm "         >
<!ATTLIST ul        %global-atts;  class CDATA "- topic/ul "         >
<!ATTLIST unknown   %global-atts;  class CDATA "- topic/unknown "    >
<!ATTLIST xref      %global-atts;  class CDATA "- topic/xref "       >


<!-- ================== End Common Elements Module  ============== -->