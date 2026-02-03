<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Bookmap                                      -->
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
PUBLIC "-//OASIS//ELEMENTS DITA BookMap//EN" 
      Delivered as file "bookmap.mod"                              -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Define elements and specialization atttributes    -->
<!--             for Book Maps                                     -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2004                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2004, 2005.         -->
<!--             All Rights Reserved.                              -->
<!--  UPDATES:                                                     -->
<!-- ============================================================= -->

<!-- ============================================================= -->
<!--                   ELEMENT NAME ENTITIES                       -->
<!-- ============================================================= -->
                               
<!ENTITY % bookmap         "bookmap"                                 >

<!ENTITY % abbrevlist      "abbrevlist"                              >
<!ENTITY % bookabstract    "bookabstract"                            >
<!ENTITY % amendments      "amendments"                              >
<!ENTITY % appendix        "appendix"                                >
<!ENTITY % approved        "approved"                                >
<!ENTITY % backmatter      "backmatter"                              >
<!ENTITY % bibliolist      "bibliolist"                              >
<!ENTITY % bookchangehistory "bookchangehistory"                     >
<!ENTITY % bookevent       "bookevent"                               >
<!ENTITY % bookeventtype   "bookeventtype"                           >
<!ENTITY % bookid          "bookid"                                  >
<!ENTITY % booklibrary     "booklibrary"                             >
<!ENTITY % booklist        "booklist"                                >
<!ENTITY % booklists       "booklists"                               >
<!ENTITY % bookmeta        "bookmeta"                                >
<!ENTITY % booknumber      "booknumber"                              >
<!ENTITY % bookowner       "bookowner"                               >
<!ENTITY % bookpartno      "bookpartno"                              >
<!ENTITY % bookrestriction "bookrestriction"                         >
<!ENTITY % bookrights      "bookrights"                              >
<!ENTITY % booktitle       "booktitle"                               >
<!ENTITY % booktitlealt    "booktitlealt"                            >
<!ENTITY % chapter         "chapter"                                 >
<!ENTITY % colophon        "colophon"                                >
<!ENTITY % completed       "completed"                               >
<!ENTITY % copyrfirst      "copyrfirst"                              >
<!ENTITY % copyrlast       "copyrlast"                               >
<!ENTITY % day             "day"                                     >
<!ENTITY % dedication      "dedication"                              >
<!ENTITY % draftintro      "draftintro"                              >
<!ENTITY % edited          "edited"                                  >
<!ENTITY % edition         "edition"                                 >
<!ENTITY % figurelist      "figurelist"                              >
<!ENTITY % frontmatter     "frontmatter"                             >
<!ENTITY % glossarylist    "glossarylist"                            >
<!ENTITY % indexlist       "indexlist"                               >
<!ENTITY % isbn            "isbn"                                    >
<!ENTITY % mainbooktitle   "mainbooktitle"                           >
<!ENTITY % maintainer      "maintainer"                              >
<!ENTITY % month           "month"                                   >
<!ENTITY % notices         "notices"                                 >
<!ENTITY % organization    "organization"                            >
<!ENTITY % part            "part"                                    >
<!ENTITY % person          "person"                                  >
<!ENTITY % preface         "preface"                                 >
<!ENTITY % printlocation   "printlocation"                           >
<!ENTITY % published       "published"                               >
<!ENTITY % publisherinformation "publisherinformation"               >
<!ENTITY % publishtype     "publishtype"                             >
<!ENTITY % reviewed        "reviewed"                                >
<!ENTITY % revisionid      "revisionid"                              >
<!ENTITY % started         "started"                                 >
<!ENTITY % summary         "summary"                                 >
<!ENTITY % tablelist       "tablelist"                               >
<!ENTITY % tested          "tested"                                  >
<!ENTITY % trademarklist   "trademarklist"                           >
<!ENTITY % toc             "toc"                                     >
<!ENTITY % volume          "volume"                                  >
<!ENTITY % year            "year"                                    >


<!-- ============================================================= -->
<!--                    DOMAINS ATTRIBUTE OVERRIDE                 -->
<!-- ============================================================= -->


<!ENTITY included-domains ""                                         >

<!-- ============================================================= -->
<!--                    COMMON ATTLIST SETS                        -->
<!-- ============================================================= -->

<!-- Currently: same as topicref, minus @query -->
<!ENTITY % chapter-atts 
            'navtitle   CDATA                             #IMPLIED
             href       CDATA                             #IMPLIED
             keyref     CDATA                             #IMPLIED
             copy-to    CDATA                             #IMPLIED
             outputclass 
                        CDATA                             #IMPLIED
             %topicref-atts;
             %univ-atts;'                                            >


<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->


<!--                    LONG NAME: Book Map                        -->
<!ELEMENT bookmap       (((%title;) | (%booktitle;))?,
                         (%bookmeta;)?, 
                         (%frontmatter;)?,
                         (%chapter;)*, (%part;)*, (%appendix;)*,
                         (%backmatter;)?,
                         (%reltable;)*)                              >
<!ATTLIST bookmap
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
             

<!--                    LONG NAME: Book Metadata                   -->
<!ELEMENT  bookmeta    ((%linktext;)?, (%searchtitle;)?, 
                         (%shortdesc;)?, (%author;)*, (%source;)?, 
                         (%publisherinformation;)*,
                         (%critdates;)?, (%permissions;)?, 
                         (%audience;)*, (%category;)*, 
                         (%keywords;)*, (%prodinfo;)*, (%othermeta;)*, 
                         (%resourceid;)*, (%bookid;)?,
                         (%bookchangehistory;)*,
                         (%bookrights;)*,
                         (%data;)*)                                  >
<!ATTLIST  bookmeta
             lockmeta   (yes | no)                        #IMPLIED
             %univ-atts;                                             >

<!--                    LONG NAME: Front Matter                    -->
<!ELEMENT  frontmatter  (%booklists; | %notices; | %dedication; | 
                         %colophon; | %bookabstract; | %draftintro; | 
                         %preface; | %topicref;)*                    >
<!ATTLIST  frontmatter
             keyref     CDATA                             #IMPLIED
             query      CDATA                             #IMPLIED
             outputclass 
                        CDATA                             #IMPLIED
             %topicref-atts;
             %univ-atts;                                             >

<!--                    LONG NAME: Back Matter                     -->
<!ELEMENT  backmatter   (%booklists; | %notices; | %dedication; | 
                         %colophon; | %amendments; | %topicref;)*    >
<!ATTLIST  backmatter
             keyref     CDATA                             #IMPLIED
             query      CDATA                             #IMPLIED
             outputclass 
                        CDATA                             #IMPLIED
             %topicref-atts;
             %univ-atts;                                             >

<!--                    LONG NAME: Publisher Information           -->
<!ELEMENT publisherinformation   
                        (((%person;) | (%organization;))*, 
                         (%printlocation;)*, (%published;)*, 
                         (%data;)*)                                  >
<!ATTLIST publisherinformation
             href       CDATA                            #IMPLIED
             keyref     CDATA                            #IMPLIED
             %univ-atts;                                             >

<!--                    LONG NAME: Person                          -->
<!ELEMENT person        (%words.cnt;)*                               >
<!ATTLIST person
             %data-element-atts;                                     >

<!--                    LONG NAME: Organization                    -->
<!ELEMENT organization  (%words.cnt;)*                               >
<!ATTLIST organization
             %data-element-atts;                                     >

<!--                    LONG NAME: Book Change History             -->
<!ELEMENT bookchangehistory
                        ((%reviewed;)*, (%edited;)*, (%tested;)*, 
                         (%approved;)*, (%bookevent;)*)              >
<!ATTLIST bookchangehistory
             %data-element-atts;                                     >

<!--                    LONG NAME: Book ID                         -->
<!ELEMENT bookid        ((%bookpartno;)*, (%edition;)?, (%isbn;)?, 
                         (%booknumber;)?, (%volume;)*, 
                         (%maintainer;)?)                            >
<!ATTLIST bookid
             %data-element-atts;                                     >

<!--                    LONG NAME: Summary                         -->
<!ELEMENT summary       (%words.cnt;)*                               >
<!ATTLIST summary
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Print Location                  -->
<!ELEMENT printlocation (%words.cnt;)*                               >
<!ATTLIST printlocation
             %data-element-atts;                                     >

<!--                    LONG NAME: Published                       -->
<!ELEMENT published     (((%person;) | (%organization;))*,
                         (%publishtype;)?, (%revisionid;)?,
                         (%started;)?, (%completed;)?, (%summary;)?, 
                         (%data;)*)                                  >
<!ATTLIST published
             %data-element-atts;                                     >

<!--                    LONG NAME: Publish Type                    -->
<!ELEMENT publishtype   EMPTY                                        >
<!ATTLIST publishtype
             %univ-atts;
             name       CDATA                            #IMPLIED
             datatype   CDATA                            #IMPLIED
             href       CDATA                            #IMPLIED
             format     CDATA                            #IMPLIED
             type       CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass
                        CDATA                            #IMPLIED
             value      (beta|limited|general)           #REQUIRED   >
             
<!--                    LONG NAME: Revision ID                     -->
<!ELEMENT revisionid    (#PCDATA)*>
<!ATTLIST revisionid
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Start Date                      -->
<!ELEMENT started       ( ((%year;), ((%month;), (%day;)?)?) | 
                          ((%month;), (%day;)?, (%year;)) | 
                          ((%day;), (%month;), (%year;)))            >
<!ATTLIST started         
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Completion Date                 -->
<!ELEMENT completed     ( ((%year;), ((%month;), (%day;)?)?) | 
                          ((%month;), (%day;)?, (%year;)) | 
                          ((%day;), (%month;), (%year;)))            >
<!ATTLIST completed       
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Year                            -->
<!ELEMENT year          (#PCDATA)*                                   >
<!ATTLIST year
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Month                           -->
<!ELEMENT month         (#PCDATA)*                                   >
<!ATTLIST month
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Day                             -->
<!ELEMENT day           (#PCDATA)*                                   >
<!ATTLIST day
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Reviewed                        -->
<!ELEMENT reviewed      (((%person;) | (%organization;))*, 
                         (%revisionid;)?, (%started;)?, 
                         (%completed;)?, (%summary;)?, (%data;)*)    >
<!ATTLIST reviewed
             %data-element-atts;                                     >

<!--                    LONG NAME: Editeded                        -->
<!ELEMENT edited        (((%person;) | (%organization;))*, 
                         (%revisionid;)?, (%started;)?, 
                         (%completed;)?, (%summary;)?, (%data;)*)    >
<!ATTLIST edited
             %data-element-atts;                                     >

<!--                    LONG NAME: Tested                          -->
<!ELEMENT tested        (((%person;) | (%organization;))*, 
                         (%revisionid;)?, (%started;)?,
                         (%completed;)?, (%summary;)?, (%data;)*)    >
<!ATTLIST tested
             %data-element-atts;                                     >

<!--                    LONG NAME: Approved                        -->
<!ELEMENT approved      (((%person;) | (%organization;))*,
                         (%revisionid;)?, (%started;)?, 
                         (%completed;)?, (%summary;)?, (%data;)*)    >
<!ATTLIST approved
             %data-element-atts;                                     >

<!--                    LONG NAME: Book Event                      -->
<!ELEMENT bookevent     ((%bookeventtype;)?, 
                         ((%person;) | (%organization;))*,
                         (%revisionid;)?, (%started;)?, 
                         (%completed;)?, (%summary;)?, (%data;)*)    >
<!ATTLIST bookevent 
             %data-element-atts;                                     >

<!--                    LONG NAME: Book Event Type                 -->
<!ELEMENT bookeventtype EMPTY                                        >
<!-- Attributes are the same as on <data> except that @name is required -->
<!ATTLIST bookeventtype 
             %univ-atts;
             datatype   CDATA                            #IMPLIED
             value      CDATA                            #IMPLIED
             href       CDATA                            #IMPLIED
             format     CDATA                            #IMPLIED
             type       CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass
                        CDATA                            #IMPLIED
             name       CDATA                            #REQUIRED   >

<!--                    LONG NAME: Book Part Number                -->
<!ELEMENT bookpartno    (%words.cnt;)*                               >
<!ATTLIST bookpartno
             %data-element-atts;                                     >

<!--                    LONG NAME: Edition                         -->
<!ELEMENT edition       (#PCDATA)*                                   >
<!ATTLIST edition
             %data-element-atts;                                     >

<!--                    LONG NAME: ISBN Number                     -->
<!ELEMENT isbn          (#PCDATA)*                                   >
<!ATTLIST isbn    
             %data-element-atts;                                     >

<!--                    LONG NAME: Book Number                     -->
<!ELEMENT booknumber    (%words.cnt;)*                               >
<!ATTLIST booknumber   
             %data-element-atts;                                     >

<!--                    LONG NAME: Volume                          -->
<!ELEMENT volume        (#PCDATA)*                                   >
<!ATTLIST volume
             %data-element-atts;                                     >

<!--                    LONG NAME: Maintainer                      -->
<!ELEMENT maintainer    (((%person;) | (%organization;))*, (%data;)*)>
<!ATTLIST maintainer
             %data-element-atts;                                     >

<!--                    LONG NAME: Book Rights                     -->
<!ELEMENT bookrights    ((%copyrfirst;)?, (%copyrlast;)?,
                         (%bookowner;), (%bookrestriction;)?, 
                         (%summary;)?)                               >
<!ATTLIST bookrights
             %data-element-atts;                                     >

<!--                    LONG NAME: First Copyright                 -->
<!ELEMENT copyrfirst    (%year;)                                     >
<!ATTLIST copyrfirst
             %data-element-atts;                                     >
                        
<!--                    LONG NAME: Last Copyright                  -->
<!ELEMENT copyrlast     (%year;)                                     >
<!ATTLIST copyrlast
             %data-element-atts;                                     >

<!--                    LONG NAME: Book Owner                      -->
<!ELEMENT bookowner     ((%person;) | (%organization;))*             >
<!ATTLIST bookowner 
             %data-element-atts;                                     >

<!--                    LONG NAME: Book Restriction                -->
<!ELEMENT bookrestriction   EMPTY                                        >
<!-- Same attributes as data, except for @value -->
<!ATTLIST bookrestriction
             %univ-atts;
             name       CDATA                            #IMPLIED
             datatype   CDATA                            #IMPLIED
             href       CDATA                            #IMPLIED
             format     CDATA                            #IMPLIED
             type       CDATA                            #IMPLIED
             scope      (local | peer | external)        #IMPLIED
             outputclass
                        CDATA                            #IMPLIED
             value      (confidential|restricted|licensed|unclassified) #REQUIRED>

<!--                    LONG NAME: Book Title                      -->
<!ELEMENT booktitle     ((%booklibrary;)?,(%mainbooktitle;),
                         (%booktitlealt;)*)                          >
<!ATTLIST booktitle
             %id-atts;
             %localization-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!-- The following three elements are specialized from <ph>. They are
     titles, which have a more limited content model than phrases. The
     content model here matches title.cnt; that entity is not reused
     in case it is expanded in the future to include something not
     allowed in a phrase.                                          -->
<!--                    LONG NAME: Library Title                   -->
<!ELEMENT booklibrary   (#PCDATA | %basic.ph.noxref; | %image;)*     >  
<!ATTLIST booklibrary              
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Main Book Title                 -->
<!ELEMENT mainbooktitle (#PCDATA | %basic.ph.noxref; | %image;)*     >
<!ATTLIST mainbooktitle              
             keyref     CDATA                           #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                           #IMPLIED     >
                        
<!--                    LONG NAME: Alternate Book Title            -->
<!ELEMENT booktitlealt  (#PCDATA | %basic.ph.noxref; | %image;)*     >
<!ATTLIST booktitlealt              
             keyref     CDATA                           #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                           #IMPLIED     >

<!--                    LONG NAME: Draft Introduction              -->
<!ELEMENT draftintro    ((%topicmeta;)?, (%topicref;)*)              >
<!ATTLIST draftintro
             %chapter-atts;                                          >

<!--                    LONG NAME: Book Abstract                   -->
<!ELEMENT bookabstract  EMPTY                                        >
<!ATTLIST bookabstract
             %chapter-atts;                                          >

<!--                    LONG NAME: Dedication                      -->
<!ELEMENT dedication    EMPTY                                        >
<!ATTLIST dedication
             %chapter-atts;                                          >

<!--                    LONG NAME: Preface                         -->
<!ELEMENT preface       ((%topicmeta;)?, (%topicref;)*)              >
<!ATTLIST preface
             %chapter-atts;                                          >

<!--                    LONG NAME: Chapter                         -->
<!ELEMENT chapter       ((%topicmeta;)?, (%topicref;)*)              >
<!ATTLIST chapter
             %chapter-atts;                                          >

<!--                    LONG NAME: Part                            -->
<!ELEMENT part          ((%topicmeta;)?,
                         ((%topicref;) | (%chapter;))* )             >
<!ATTLIST part
             %chapter-atts;                                          >

<!--                    LONG NAME: Appendix                        -->
<!ELEMENT appendix      ((%topicmeta;)?, (%topicref;)*)              >
<!ATTLIST appendix
             %chapter-atts;                                          >

<!--                    LONG NAME: Notices                         -->
<!ELEMENT notices       ((%topicmeta;)?, (%topicref;)*)              >
<!ATTLIST notices
             %chapter-atts;                                          >

<!--                    LONG NAME: Amendments                      -->
<!ELEMENT amendments    EMPTY                                        >
<!ATTLIST amendments
             %chapter-atts;                                          >

<!--                    LONG NAME: Colophon                        -->
<!ELEMENT colophon      EMPTY                                        >
<!ATTLIST colophon
             %chapter-atts;                                          >

<!--                    LONG NAME: Book Lists                      -->
<!ELEMENT booklists     ((%toc;) |
                         (%figurelist;) |
                         (%tablelist;) |
                         (%abbrevlist;) |
                         (%trademarklist;) |
                         (%bibliolist;) |
                         (%glossarylist;) |
                         (%indexlist;) |
                         (%booklist;))*                              >
<!ATTLIST booklists
             keyref     CDATA                             #IMPLIED
             %topicref-atts;
             %id-atts;
             %select-atts;
             %localization-atts;                                     >

<!--                    LONG NAME: Table of Contents               -->
<!ELEMENT toc           EMPTY                                        >
<!ATTLIST toc
             %chapter-atts;                                          >

<!--                    LONG NAME: Figure List                     -->
<!ELEMENT figurelist    EMPTY                                        >
<!ATTLIST figurelist
             %chapter-atts;                                          >

<!--                    LONG NAME: Table List                      -->
<!ELEMENT tablelist     EMPTY                                        >
<!ATTLIST tablelist
             %chapter-atts;                                          >

<!--                    LONG NAME: Abbreviation List               -->
<!ELEMENT abbrevlist    EMPTY                                        >
<!ATTLIST abbrevlist
             %chapter-atts;                                          >

<!--                    LONG NAME: Trademark List                  -->
<!ELEMENT trademarklist EMPTY                                        >
<!ATTLIST trademarklist
             %chapter-atts;                                          >

<!--                    LONG NAME: Bibliography List               -->
<!ELEMENT bibliolist    EMPTY                                        >
<!ATTLIST bibliolist
             %chapter-atts;                                          >

<!--                    LONG NAME: Glossary List                   -->
<!ELEMENT glossarylist  ((%topicmeta;)?, (%topicref;)*)              >
<!ATTLIST glossarylist
             %chapter-atts;                                          >

<!--                    LONG NAME: Index List                      -->
<!ELEMENT indexlist     EMPTY                                        >
<!ATTLIST indexlist
             %chapter-atts;                                          >

<!--                    LONG NAME: Book List                       -->
<!ELEMENT booklist      EMPTY                                        >
<!ATTLIST booklist
             %chapter-atts;                                          >

                     
<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->

<!ATTLIST bookmap     %global-atts; class CDATA "- map/map bookmap/bookmap ">
<!ATTLIST abbrevlist  %global-atts; class CDATA "- map/topicref bookmap/abbrevlist ">
<!ATTLIST amendments  %global-atts; class CDATA "- map/topicref bookmap/amendments ">
<!ATTLIST appendix    %global-atts; class CDATA "- map/topicref bookmap/appendix ">
<!ATTLIST approved    %global-atts; class CDATA "- topic/data bookmap/approved ">
<!ATTLIST backmatter  %global-atts; class CDATA "- map/topicref bookmap/backmatter ">
<!ATTLIST bibliolist  %global-atts; class CDATA "- map/topicref bookmap/bibliolist ">
<!ATTLIST bookabstract %global-atts; class CDATA "- map/topicref bookmap/bookabstract ">
<!ATTLIST bookchangehistory %global-atts; class CDATA "- topic/data bookmap/bookchangehistory ">
<!ATTLIST bookevent   %global-atts; class CDATA "- topic/data bookmap/bookevent ">
<!ATTLIST bookeventtype %global-atts; class CDATA "- topic/data bookmap/bookeventtype ">
<!ATTLIST bookid      %global-atts; class CDATA "- topic/data bookmap/bookid ">
<!ATTLIST booklibrary %global-atts;  class CDATA "- topic/ph bookmap/booklibrary ">
<!ATTLIST booklist    %global-atts; class CDATA "- map/topicref bookmap/booklist ">
<!ATTLIST booklists   %global-atts; class CDATA "- map/topicref bookmap/booklists ">
<!ATTLIST bookmeta    %global-atts; class CDATA "- map/topicmeta bookmap/bookmeta ">
<!ATTLIST booknumber  %global-atts; class CDATA "- topic/data bookmap/booknumber ">
<!ATTLIST bookowner   %global-atts; class CDATA "- topic/data bookmap/bookowner ">
<!ATTLIST bookpartno  %global-atts; class CDATA "- topic/data bookmap/bookpartno ">
<!ATTLIST bookrestriction %global-atts; class CDATA "- topic/data bookmap/bookrestriction ">
<!ATTLIST bookrights  %global-atts; class CDATA "- topic/data bookmap/bookrights ">
<!ATTLIST booktitle   %global-atts;  class CDATA "- topic/title bookmap/booktitle ">
<!ATTLIST booktitlealt %global-atts;  class CDATA "- topic/ph bookmap/booktitlealt ">
<!ATTLIST chapter     %global-atts; class CDATA "- map/topicref bookmap/chapter ">
<!ATTLIST colophon    %global-atts; class CDATA "- map/topicref bookmap/colophon ">
<!ATTLIST completed   %global-atts; class CDATA "- topic/ph bookmap/completed ">
<!ATTLIST copyrfirst  %global-atts; class CDATA "- topic/data bookmap/copyrfirst ">
<!ATTLIST copyrlast   %global-atts; class CDATA "- topic/data bookmap/copyrlast ">
<!ATTLIST day         %global-atts; class CDATA "- topic/ph bookmap/day ">
<!ATTLIST dedication  %global-atts; class CDATA "- map/topicref bookmap/dedication ">
<!ATTLIST draftintro  %global-atts; class CDATA "- map/topicref bookmap/draftintro ">
<!ATTLIST edited      %global-atts; class CDATA "- topic/data bookmap/edited ">
<!ATTLIST edition     %global-atts; class CDATA "- topic/data bookmap/edition ">
<!ATTLIST figurelist  %global-atts; class CDATA "- map/topicref bookmap/figurelist ">
<!ATTLIST frontmatter %global-atts; class CDATA "- map/topicref bookmap/frontmatter ">
<!ATTLIST glossarylist %global-atts; class CDATA "- map/topicref bookmap/glossarylist ">
<!ATTLIST indexlist   %global-atts; class CDATA "- map/topicref bookmap/indexlist ">
<!ATTLIST isbn        %global-atts; class CDATA "- topic/data bookmap/isbn ">
<!ATTLIST mainbooktitle %global-atts;  class CDATA "- topic/ph bookmap/mainbooktitle ">
<!ATTLIST maintainer  %global-atts; class CDATA "- topic/data bookmap/maintainer ">
<!ATTLIST month       %global-atts; class CDATA "- topic/ph bookmap/month ">
<!ATTLIST notices     %global-atts; class CDATA "- map/topicref bookmap/notices ">
<!ATTLIST organization %global-atts; class CDATA "- topic/data bookmap/organization ">
<!ATTLIST part        %global-atts; class CDATA "- map/topicref bookmap/part ">
<!ATTLIST person      %global-atts; class CDATA "- topic/data bookmap/person ">
<!ATTLIST preface     %global-atts; class CDATA "- map/topicref bookmap/preface ">
<!ATTLIST printlocation %global-atts; class CDATA "- topic/data bookmap/printlocation ">
<!ATTLIST published   %global-atts; class CDATA "- topic/data bookmap/published ">
<!ATTLIST publisherinformation %global-atts; class CDATA "- topic/publisher bookmap/publisherinformation ">
<!ATTLIST publishtype %global-atts; class CDATA "- topic/data bookmap/publishtype ">
<!ATTLIST reviewed    %global-atts; class CDATA "- topic/data bookmap/reviewed ">
<!ATTLIST revisionid  %global-atts; class CDATA "- topic/ph bookmap/revisionid ">
<!ATTLIST started     %global-atts; class CDATA "- topic/ph bookmap/started ">
<!ATTLIST summary     %global-atts; class CDATA "- topic/ph bookmap/summary ">
<!ATTLIST tablelist   %global-atts; class CDATA "- map/topicref bookmap/tablelist ">
<!ATTLIST tested      %global-atts; class CDATA "- topic/data bookmap/tested ">
<!ATTLIST toc         %global-atts; class CDATA "- map/topicref bookmap/toc ">
<!ATTLIST trademarklist %global-atts; class CDATA "- map/topicref bookmap/trademarklist ">
<!ATTLIST volume      %global-atts; class CDATA "- topic/data bookmap/volume ">
<!ATTLIST year        %global-atts; class CDATA "- topic/ph bookmap/year ">



