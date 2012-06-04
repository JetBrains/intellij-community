<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Highlight Domain                             -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Highlight Domain//EN"
      Delivered as file "highlightDomain.mod"                      -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Define elements and specialization attributes     -->
<!--             for Highlight Domain                              -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2001                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2005.11.15 RDA: Corrected descriptive names for all        -->
<!--                    elements except bold                       -->
<!--    2005.11.15 RDA: Corrected the "Delivered as" system ID     -->
<!-- ============================================================= -->

<!-- ============================================================= -->
<!--                   ELEMENT NAME ENTITIES                       -->
<!-- ============================================================= -->

<!ENTITY % b           "b"                                           >
<!ENTITY % i           "i"                                           >
<!ENTITY % u           "u"                                           >     
<!ENTITY % tt          "tt"                                          >
<!ENTITY % sup         "sup"                                         >
<!ENTITY % sub         "sub"                                         >


<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->


<!--                    LONG NAME: Bold                            -->
<!ELEMENT b             (#PCDATA | %basic.ph; | %data.elements.incl; |
                         %foreign.unknown.incl;)*                    >
<!ATTLIST b              
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Underlined                      -->
<!ELEMENT u             (#PCDATA | %basic.ph; | %data.elements.incl; |
                         %foreign.unknown.incl;)*                    >
<!ATTLIST u              
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Italic                          -->
<!ELEMENT i             (#PCDATA | %basic.ph; | %data.elements.incl; |
                         %foreign.unknown.incl;)*                    >
<!ATTLIST i             
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Teletype (monospaced)           -->
<!ELEMENT tt            (#PCDATA | %basic.ph; | %data.elements.incl; |
                         %foreign.unknown.incl;)*                    >
<!ATTLIST tt            
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Superscript                     -->
<!ELEMENT sup           (#PCDATA | %basic.ph; | %data.elements.incl; |
                         %foreign.unknown.incl;)*                    >
<!ATTLIST sup          
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Subscript                       -->
<!ELEMENT sub           (#PCDATA | %basic.ph; | %data.elements.incl; |
                         %foreign.unknown.incl;)*                    >
<!ATTLIST sub           
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >
             

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->


<!ATTLIST b           %global-atts;  class CDATA "+ topic/ph hi-d/b "  >
<!ATTLIST i           %global-atts;  class CDATA "+ topic/ph hi-d/i "  >
<!ATTLIST sub         %global-atts;  class CDATA "+ topic/ph hi-d/sub ">
<!ATTLIST sup         %global-atts;  class CDATA "+ topic/ph hi-d/sup ">
<!ATTLIST tt          %global-atts;  class CDATA "+ topic/ph hi-d/tt " >
<!ATTLIST u           %global-atts;  class CDATA "+ topic/ph hi-d/u "  >


<!-- ================== DITA Highlight Domain ==================== -->