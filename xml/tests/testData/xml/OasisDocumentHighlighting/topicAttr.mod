<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Topic Class Entities                         -->
<!--  VERSION:   1.0.1                                             -->
<!--  DATE:      November 2005                                     -->
<!--                                                               -->
<!-- ============================================================= -->

<!-- ============================================================= -->
<!--                    PUBLIC DOCUMENT TYPE DEFINITION            -->
<!--                    TYPICAL INVOCATION                         -->
<!--                                                               -->
<!--  Refer to this file by the following public identifier or an 
      appropriate system identifier 
PUBLIC "-//OASIS//ENTITIES DITA Topic Class//EN"
      Delivered as file "topicAttr.mod"                            -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Declaring the Topic specialization attributes     -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2001                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005.                    -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2005.11.15 RDA: Removed extra declaration for <title>      -->
<!--                    attributes                                 -->
<!--    2005.11.15 RDA: Corrected the "Delivered as" system ID     -->
<!--    2006.05.05 RDA: Moved shared items to commonElements file  --> 
<!--    2006.06.07 RDA: Added <abstract> element                   -->
<!-- ============================================================= -->


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
     included, even though it is common. -->
<!ATTLIST shortdesc   %global-atts;  class CDATA "- topic/shortdesc "  >

<!-- ================== End Topic Class Entities ================= -->