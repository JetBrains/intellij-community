<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    DITA Software Domain                              -->
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
PUBLIC "-//OASIS//ELEMENTS DITA Software Domain//EN"
      Delivered as file "softwareDomain.mod"                       -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Declaring the elements and specialization         -->
<!--             attributes for the Software Domain                -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             March 2001                                        -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2005, 2006.              -->
<!--             (C) Copyright IBM Corporation 2001, 2004.         -->
<!--             All Rights Reserved.                              -->
<!--                                                               -->
<!--  UPDATES:                                                     -->
<!--    2005.11.15 RDA: Corrected the PURPOSE in this comment      -->
<!--    2005.11.15 RDA: Corrected the "Delivered as" system ID     -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                   ELEMENT NAME ENTITIES                       -->
<!-- ============================================================= -->


<!ENTITY % msgph       "msgph"                                       >
<!ENTITY % msgblock    "msgblock"                                    >
<!ENTITY % msgnum      "msgnum"                                      >
<!ENTITY % cmdname     "cmdname"                                     >
<!ENTITY % varname     "varname"                                     >
<!ENTITY % filepath    "filepath"                                    >
<!ENTITY % userinput   "userinput"                                   >
<!ENTITY % systemoutput 
                       "systemoutput"                                >


<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->


<!--                    LONG NAME: Message Phrase                  -->
<!ELEMENT msgph         (%words.cnt;)*                               >
<!ATTLIST msgph          
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Message Block                   -->
<!ELEMENT msgblock      (%words.cnt;)*                               >
<!ATTLIST msgblock        
             %display-atts;
             spectitle  CDATA                            #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    
             xml:space  (preserve)                #FIXED "preserve"  >


<!--                    LONG NAME: Message Number                  -->
<!ELEMENT msgnum         (#PCDATA)                                   >
<!ATTLIST msgnum          
             keyref      CDATA                           #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Command Name                    -->
<!ELEMENT cmdname       (#PCDATA)                                    >
<!ATTLIST cmdname       
             keyref      CDATA                           #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: Variable Name                   -->
<!ELEMENT varname       (#PCDATA)                                    >
<!ATTLIST varname         
             keyref      CDATA                           #IMPLIED
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: File Path                       -->
<!ELEMENT filepath      (%words.cnt;)*                               >
<!ATTLIST filepath       
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: User Input                      -->
<!ELEMENT userinput     (%words.cnt;)*                               >
<!ATTLIST userinput      
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >


<!--                    LONG NAME: System Output                   -->
<!ELEMENT systemoutput  (%words.cnt;)*                               >
<!ATTLIST systemoutput   
             %univ-atts;                                  
             outputclass 
                        CDATA                            #IMPLIED    >
             

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->
             

<!ATTLIST cmdname     %global-atts;  class CDATA "+ topic/keyword sw-d/cmdname ">
<!ATTLIST filepath    %global-atts;  class CDATA "+ topic/ph sw-d/filepath "    >
<!ATTLIST msgblock    %global-atts;  class CDATA "+ topic/pre sw-d/msgblock "   >
<!ATTLIST msgnum      %global-atts;  class CDATA "+ topic/keyword sw-d/msgnum " >
<!ATTLIST msgph       %global-atts;  class CDATA "+ topic/ph sw-d/msgph "       >
<!ATTLIST systemoutput
                      %global-atts;  class CDATA "+ topic/ph sw-d/systemoutput ">
<!ATTLIST userinput   %global-atts;  class CDATA "+ topic/ph sw-d/userinput "   >
<!ATTLIST varname     %global-atts;  class CDATA "+ topic/keyword sw-d/varname ">

 
<!-- ================== End Software Domain ====================== -->