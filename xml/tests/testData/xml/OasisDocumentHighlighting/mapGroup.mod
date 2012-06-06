<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Map Group Domain                             -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Map Group Domain//EN"
      Delivered as file "mapGroup.mod"                             -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Define elements and specialization attributes     -->
<!--             for Map Group Domain                              -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2001                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2005.11.15 RDA: Corrected the "Delivered as" system ID     -->
<!--    2006.06.07 RDA: Make universal attributes universal        -->
<!--                      (DITA 1.1 proposal #12)                  -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                    ELEMENT NAME ENTITIES                      -->
<!-- ============================================================= -->

<!ENTITY % topichead    "topichead"                                  >
<!ENTITY % topicgroup   "topicgroup"                                 >


<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->


<!--                    LONG NAME: Topic Head                      -->
<!ELEMENT topichead     ((%topicmeta;)?, 
                          (%topicref; | %navref; | %anchor; | 
                           %data.elements.incl;)* )                  >
<!ATTLIST topichead
             navtitle   CDATA                            #REQUIRED
             outputclass 
                        CDATA                            #IMPLIED
             %topicref-atts;
             %univ-atts;                                             >


<!--                    LONG NAME: Topic Group                     -->
<!ELEMENT topicgroup    ((%topicmeta;)?, 
                         (%topicref; | %navref; | %anchor; | 
                          %data.elements.incl;)* )                   >
<!ATTLIST topicgroup
             outputclass 
                        CDATA                            #IMPLIED
             %topicref-atts;
             %univ-atts;                                             >


<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->

<!ATTLIST topichead     %global-atts;  class CDATA "+ map/topicref mapgroup-d/topichead ">
<!ATTLIST topicgroup    %global-atts;  class CDATA "+ map/topicref mapgroup-d/topicgroup ">


<!-- ================== DITA Map Group Domain  =================== -->