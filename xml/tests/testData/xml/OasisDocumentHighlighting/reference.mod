<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Reference                                    -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Reference//EN"
      Delivered as file "reference.mod"                            -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Declaring the elements and specialization         -->
<!--             attributes for Reference                          -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2001                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2005.11.15 RDA: Removed old declaration for                -->
<!--                    referenceClasses entity                    -->
<!--    2005.11.15 RDA: Corrected LONG NAME for propdeschd         -->
<!--    2006.06.07 RDA: Added <abstract> element                   -->
<!--    2006.06.07 RDA: Make universal attributes universal        -->
<!--                      (DITA 1.1 proposal #12)                  -->
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


<!ENTITY % reference-info-types 
                      "%info-types;"                                 >


<!-- ============================================================= -->
<!--                   ELEMENT NAME ENTITIES                       -->
<!-- ============================================================= -->


<!ENTITY % reference   "reference"                                   >
<!ENTITY % refbody     "refbody"                                     >
<!ENTITY % refsyn      "refsyn"                                      >
<!ENTITY % properties  "properties"                                  >
<!ENTITY % property    "property"                                    >
<!ENTITY % proptype    "proptype"                                    >
<!ENTITY % propvalue   "propvalue"                                   >
<!ENTITY % propdesc    "propdesc"                                    >
<!ENTITY % prophead    "prophead"                                    >
<!ENTITY % proptypehd  "proptypehd"                                  >
<!ENTITY % propvaluehd "propvaluehd"                                 >
<!ENTITY % propdeschd  "propdeschd"                                  >


<!-- ============================================================= -->
<!--                    DOMAINS ATTRIBUTE OVERRIDE                 -->
<!-- ============================================================= -->


<!ENTITY included-domains ""                                         >


<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->


<!--                    LONG NAME: Reference                       -->
<!ELEMENT reference     ((%title;), (%titlealts;)?,
                         (%shortdesc; | %abstract;)?, 
                         (%prolog;)?, (%refbody;)?, (%related-links;)?, 
                         (%reference-info-types;)* )                 >
<!ATTLIST reference
             id         ID                               #REQUIRED
             conref     CDATA                            #IMPLIED
             %select-atts;
             %localization-atts;
             %arch-atts;
             domains    CDATA                  "&included-domains;"
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Reference Body                  -->
<!ELEMENT refbody       ((%section; | %refsyn; | %example; | %table; | 
                          %simpletable; |  %properties; | 
                          %data.elements.incl; | 
                          %foreign.unknown.incl;)* )                 >
<!ATTLIST refbody         
             %id-atts;
             %localization-atts;
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Reference Syntax                -->
<!ELEMENT refsyn        (%section.cnt;)*                             >  
<!ATTLIST refsyn          
             spectitle  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Properties                      -->
<!ELEMENT properties    ((%prophead;)?, (%property;)+)               >
<!ATTLIST properties      
             relcolwidth 
                        CDATA                           #IMPLIED
             keycol     NMTOKEN                         #IMPLIED
             refcols    NMTOKENS                        #IMPLIED
             spectitle  CDATA                           #IMPLIED
             %display-atts;
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME:  Property Head                  -->
<!ELEMENT prophead      ((%proptypehd;)?, (%propvaluehd;)?, 
                         (%propdeschd;)?)                            >
<!ATTLIST prophead       
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Property Type Head              -->
<!ELEMENT proptypehd    (%tblcell.cnt;)*                             >
<!ATTLIST proptypehd     
             specentry  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Property Value Head             -->
<!ELEMENT propvaluehd   (%tblcell.cnt;)*                             >
<!ATTLIST propvaluehd     
             specentry  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Property Description Head       -->
<!ELEMENT propdeschd    (%tblcell.cnt;)*                             >
<!ATTLIST propdeschd    
             specentry  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Property                        -->
<!ELEMENT property      ((%proptype;)?, (%propvalue;)?, 
                         (%propdesc;)?)                              >
<!ATTLIST property       
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Property Type                   -->
<!ELEMENT proptype      (%ph.cnt;)*                                  >
<!ATTLIST proptype       
             specentry  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Property Value                  -->
<!ELEMENT propvalue     (%ph.cnt;)*                                  >
<!ATTLIST propvalue      
             specentry  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Property Descrption             -->
<!ELEMENT propdesc      (%desc.cnt;)*                                >
<!ATTLIST propdesc        
             specentry  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >

             

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->


<!ATTLIST reference   %global-atts;  class  CDATA "- topic/topic       reference/reference " >
<!ATTLIST refbody     %global-atts;  class  CDATA "- topic/body        reference/refbody "   >
<!ATTLIST refsyn      %global-atts;  class  CDATA "- topic/section     reference/refsyn "    >
<!ATTLIST properties  %global-atts;  class  CDATA "- topic/simpletable reference/properties ">
<!ATTLIST property    %global-atts;  class  CDATA "- topic/strow       reference/property "  >
<!ATTLIST proptype    %global-atts;  class  CDATA "- topic/stentry     reference/proptype "  >
<!ATTLIST propvalue   %global-atts;  class  CDATA "- topic/stentry     reference/propvalue " >
<!ATTLIST propdesc    %global-atts;  class  CDATA "- topic/stentry     reference/propdesc "  >

<!ATTLIST prophead    %global-atts;  class  CDATA "- topic/sthead      reference/prophead "  >
<!ATTLIST proptypehd  %global-atts;  class  CDATA "- topic/stentry     reference/proptypehd ">
<!ATTLIST propvaluehd %global-atts;  class  CDATA "- topic/stentry     reference/propvaluehd ">
<!ATTLIST propdeschd  %global-atts;  class  CDATA "- topic/stentry     reference/propdeschd ">

 
<!-- ================== End DITA Reference  =========================== -->

