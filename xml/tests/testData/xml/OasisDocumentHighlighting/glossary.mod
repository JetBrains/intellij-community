<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Glossary                                     -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Glossary//EN"
      Delivered as file "glossary.mod"                             -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Define elements and specialization atttributes    -->
<!--             for Glossary topics                               -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             June 2006                                         -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2006.                    -->
<!--             All Rights Reserved.                              -->
<!--  UPDATES:                                                     -->
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
<!--                   SPECIALIZATION OF DECLARED ELEMENTS         -->
<!-- ============================================================= -->


<!ENTITY % glossentry-info-types "no-topic-nesting">


<!-- ============================================================= -->
<!--                   ELEMENT NAME ENTITIES                       -->
<!-- ============================================================= -->
 

<!ENTITY % glossentry  "glossentry"                                  >
<!ENTITY % glossterm   "glossterm"                                   >
<!ENTITY % glossdef    "glossdef"                                    >


<!-- ============================================================= -->
<!--                    DOMAINS ATTRIBUTE OVERRIDE                 -->
<!-- ============================================================= -->


<!ENTITY included-domains ""                                         >


<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->


<!--                    LONG NAME: Glossary Entry                  -->
<!ELEMENT glossentry     ((%glossterm;), (%glossdef;), 
                          (%related-links;)?,
                          (%glossentry-info-types;)* )               >
<!ATTLIST glossentry        
             id         ID                               #REQUIRED
             conref     CDATA                            #IMPLIED
             %select-atts;
             %localization-atts;
             %arch-atts;
             outputclass 
                        CDATA                            #IMPLIED
             domains    CDATA                "&included-domains;"    >


<!--                    LONG NAME: Glossary Term                   -->
<!ELEMENT glossterm     (%title.cnt;)*                               >
<!ATTLIST glossterm         
             %id-atts;
             %localization-atts;
             outputclass 
                        CDATA                            #IMPLIED    >
                        
<!--                    LONG NAME: Glossary Definition             -->
<!ELEMENT glossdef      (%section.notitle.cnt; | %shortdesc;)*       >
<!ATTLIST glossdef         
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >
             

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->


<!ATTLIST glossentry  %global-atts;  class CDATA "- topic/topic concept/concept glossentry/glossentry ">
<!ATTLIST glossterm   %global-atts;  class CDATA "- topic/title concept/title glossentry/glossterm ">
<!ATTLIST glossdef    %global-atts;  class CDATA "- topic/abstract concept/abstract glossentry/glossdef ">

 
<!-- ================== End DITA Glossary ======================== -->




