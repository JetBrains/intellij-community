<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Indexing  Domain                             -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Indexing Domain//EN"
      Delivered as file "indexingDomain.mod"                       -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Declaring the elements and specialization         -->
<!--             attributes for the DITA Indexing Domain           -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             June 2006                                         -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2006.                    -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                   ELEMENT NAME ENTITIES                       -->
<!-- ============================================================= -->

<!ENTITY % index-see       "index-see"                               >
<!ENTITY % index-see-also  "index-see-also"                          >
<!ENTITY % index-sort-as   "index-sort-as"                           >


<!-- ============================================================= -->
<!--                    COMMON ATTLIST SETS                        -->
<!-- ============================================================= -->




<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS for IMAGEMAP          -->
<!-- ============================================================= -->

<!--                    LONG NAME: Index See                       -->
<!ELEMENT index-see     (%words.cnt;|%indexterm;)*                   >
<!ATTLIST index-see
             keyref     CDATA                            #IMPLIED
             %univ-atts;                                             >

<!--                    LONG NAME: Index See Also                  -->
<!ELEMENT index-see-also
                        (%words.cnt;|%indexterm;)*                   >
<!ATTLIST index-see-also
             keyref     CDATA                            #IMPLIED
             %univ-atts;                                             >

<!--                    LONG NAME: Index Sort As                   -->
<!ELEMENT index-sort-as (%words.cnt;)*                               >
<!ATTLIST index-sort-as
             keyref     CDATA                            #IMPLIED
             %univ-atts;                                             >

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->


<!ATTLIST index-see       %global-atts; class CDATA "+ topic/index-base indexing-d/index-see ">
<!ATTLIST index-see-also  %global-atts; class CDATA "+ topic/index-base indexing-d/index-see-also ">
<!ATTLIST index-sort-as   %global-atts; class CDATA "+ topic/index-base indexing-d/index-sort-as ">
 
<!-- ================== End Indexing Domain ====================== -->