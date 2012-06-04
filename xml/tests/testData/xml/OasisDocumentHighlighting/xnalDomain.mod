<!-- ============================================================= -->
<!--                    HEADER                                     -->
<!-- ============================================================= -->
<!--  MODULE:    XNAL Domain                                       -->
<!--  VERSION:   1.1                                               -->
<!--  DATE:      June 2006                                         -->
<!--                                                               -->
<!-- ============================================================= -->

<!-- ============================================================= -->
<!--                    PUBLIC DOCUMENT TYPE DEFINITION            -->
<!--                    TYPICAL INVOCATION                         -->
<!--                                                               -->
<!--  Refer to this file by the following public identfier or an 
      appropriate system identifier 
PUBLIC "-//OASIS//ELEMENTS DITA XNAL Domain//EN"
      Delivered as file "xnalDomain.mod"                           -->

<!-- ============================================================= -->
<!-- SYSTEM:     Darwin Information Typing Architecture (DITA)     -->
<!--                                                               -->
<!-- PURPOSE:    Define elements and specialization atttributed    -->
<!--             for the XNAL Domain                               -->
<!--                                                               -->
<!-- ORIGINAL CREATION DATE:                                       -->
<!--             June 2006                                         -->
<!--                                                               -->
<!--             (C) Copyright OASIS Open 2006.                    -->
<!--             All Rights Reserved.                              -->
<!-- ============================================================= -->


<!-- ============================================================= -->
<!--                    ELEMENT NAME ENTITIES                      -->
<!-- ============================================================= -->


<!ENTITY % authorinformation "authorinformation"                     >
<!ENTITY % addressdetails  "addressdetails"                          >
<!ENTITY % administrativearea "administrativearea"                   >
<!ENTITY % contactnumber   "contactnumber"                           >
<!ENTITY % contactnumbers  "contactnumbers"                          >
<!ENTITY % country         "country"                                 >
<!ENTITY % emailaddress    "emailaddress"                            >
<!ENTITY % emailaddresses  "emailaddresses"                          >
<!ENTITY % firstname       "firstname"                               >
<!ENTITY % generationidentifier "generationidentifier"               >
<!ENTITY % honorific       "honorific"                               >
<!ENTITY % lastname        "lastname"                                >
<!ENTITY % locality        "locality"                                >
<!ENTITY % localityname    "localityname"                            >
<!ENTITY % middlename      "middlename"                              >
<!ENTITY % namedetails     "namedetails"                             >
<!ENTITY % organizationinfo "organizationinfo"                       >
<!ENTITY % organizationname "organizationname"                       >
<!ENTITY % organizationnamedetails "organizationnamedetails"         >
<!ENTITY % otherinfo       "otherinfo"                               >
<!ENTITY % personinfo      "personinfo"                              >
<!ENTITY % personname      "personname"                              >
<!ENTITY % postalcode      "postalcode"                              >
<!ENTITY % thoroughfare    "thoroughfare"                            >
<!ENTITY % url             "url"                                     >
<!ENTITY % urls            "urls"                                    >

<!-- ============================================================= -->
<!--                    ELEMENT DECLARATIONS                       -->
<!-- ============================================================= -->
                      
<!--                    LONG NAME: Author Information              -->
<!ELEMENT authorinformation
                        ((%personinfo; | %organizationinfo;)*)       >
<!ATTLIST authorinformation     
             %univ-atts;
             href       CDATA                            #IMPLIED
             keyref     CDATA                            #IMPLIED
             type       (creator | contributor)          #IMPLIED    >

<!--                    LONG NAME: Name Details                    -->
<!ELEMENT namedetails   ((%personname; | %organizationnamedetails;)*)>
<!ATTLIST namedetails
             %data-element-atts;                                     >

<!--                    LONG NAME: Organization Details            -->
<!ELEMENT organizationnamedetails      
                        ((%organizationname;)?, (%otherinfo;)*)      >
<!ATTLIST organizationnamedetails              
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Organization Name               -->
<!ELEMENT organizationname
                        (%ph.cnt;)*                                  >
<!ATTLIST organizationname
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Person Name                     -->
<!ELEMENT personname    ((%honorific;)?, 
                         (%firstname;)*,(%middlename;)*,(%lastname;)*,
                         (%generationidentifier;)?, (%otherinfo;)*)  >
<!ATTLIST personname
             %data-element-atts;                                     >

<!--                    LONG NAME: Honorific                       -->
<!ELEMENT honorific     (#PCDATA)*                                   >
<!ATTLIST honorific
             %data-element-atts;                                     >

<!--                    LONG NAME: First Name                      -->
<!ELEMENT firstname     (#PCDATA)*                                   >
<!ATTLIST firstname
             %data-element-atts;                                     >

<!--                    LONG NAME: Middle Name                     -->
<!ELEMENT middlename    (#PCDATA)*                                   >
<!ATTLIST middlename
             %data-element-atts;                                     >

<!--                    LONG NAME: Last Name                       -->
<!ELEMENT lastname      (#PCDATA)*                                   >
<!ATTLIST lastname
             %data-element-atts;                                     >

<!--                    LONG NAME: Generation Identifier           -->
<!ELEMENT generationidentifier
                        (#PCDATA)*                                   >
<!ATTLIST generationidentifier
             %data-element-atts;                                     >

<!--                    LONG NAME: Other Information               -->
<!ELEMENT otherinfo     (%words.cnt;)*>
<!ATTLIST otherinfo
             %data-element-atts;                                     >

<!--                    LONG NAME: Address Details                 -->
<!ELEMENT addressdetails
                        (%words.cnt;|%locality;|%administrativearea;|
                         %thoroughfare;|%country;)*                  >
<!ATTLIST addressdetails              
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Locality                        -->
<!ELEMENT locality      (%words.cnt;|%localityname;|%postalcode;)*   >
<!ATTLIST locality
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Locality Name                   -->
<!ELEMENT localityname  (%words.cnt;)*                               >
<!ATTLIST localityname
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Administrative Area             -->
<!ELEMENT administrativearea
                        (%words.cnt;)*                               >
<!ATTLIST administrativearea
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass 
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Thoroughfare                    -->
<!ELEMENT thoroughfare  (%words.cnt;)*                               >
<!ATTLIST thoroughfare
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Postal Code                     -->
<!ELEMENT postalcode    (#PCDATA)*                                   >
<!ATTLIST postalcode
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Country                         -->
<!ELEMENT country       (#PCDATA)*                                   >
<!ATTLIST country
             keyref     CDATA                            #IMPLIED
             %univ-atts;
             outputclass
                        CDATA                            #IMPLIED    >

<!--                    LONG NAME: Person Information              -->
<!ELEMENT personinfo    ((%namedetails;)?, (%addressdetails;)?,
                         (%contactnumbers;)?, (%emailaddresses;)?)   >
<!ATTLIST personinfo
             %data-element-atts;                                     >

<!--                    LONG NAME: Organization Information        -->
<!ELEMENT organizationinfo
                        ((%namedetails;)?, (%addressdetails;)?, 
                         (%contactnumbers;)?, (%emailaddresses;)?,
                         (%urls;)?)                                  >  
<!ATTLIST organizationinfo 
             %data-element-atts;                                     >

<!--                    LONG NAME: Contact Numbers                 -->
<!ELEMENT contactnumbers
                        (%contactnumber;)*                           >
<!ATTLIST contactnumbers
             %data-element-atts;                                     >
                        
<!--                    LONG NAME: Contact Number                  -->
<!--                    Note: set the type of number using @type   -->
<!ELEMENT contactnumber (#PCDATA)*                                   >  
<!ATTLIST contactnumber
             %data-element-atts;                                     >            
                        
<!--                    LONG NAME: Email Addresses                 -->
<!ELEMENT emailaddresses
                        (%emailaddress;)*                            >
<!ATTLIST emailaddresses
             %data-element-atts;                                     >

<!--                    LONG NAME: Email Address                   -->
<!ELEMENT emailaddress  (%words.cnt;)*                               >
<!ATTLIST emailaddress
             %data-element-atts;                                     >

<!--                    LONG NAME: URLs                            -->
<!ELEMENT urls          (%url;)*                                     >  
<!ATTLIST urls
             %data-element-atts;                                     >

<!--                    LONG NAME: URL                             -->
<!ELEMENT url           (%words.cnt;)*                               >  
<!ATTLIST url
             %data-element-atts;                                     >

<!-- ============================================================= -->
<!--                    SPECIALIZATION ATTRIBUTE DECLARATIONS      -->
<!-- ============================================================= -->

<!ATTLIST addressdetails %global-atts; class CDATA "+ topic/ph xnal-d/addressdetails ">
<!ATTLIST administrativearea %global-atts; class CDATA "+ topic/ph xnal-d/administrativearea ">
<!ATTLIST authorinformation %global-atts; class CDATA "+ topic/author xnal-d/authorinformation ">
<!ATTLIST contactnumber %global-atts; class CDATA "+ topic/data xnal-d/contactnumber ">
<!ATTLIST contactnumbers %global-atts; class CDATA "+ topic/data xnal-d/contactnumbers ">
<!ATTLIST country     %global-atts; class CDATA "+ topic/ph xnal-d/country ">
<!ATTLIST emailaddress %global-atts; class CDATA "+ topic/data xnal-d/emailaddress ">
<!ATTLIST emailaddresses %global-atts; class CDATA "+ topic/data xnal-d/emailaddresses ">
<!ATTLIST firstname   %global-atts; class CDATA "+ topic/data xnal-d/firstname ">
<!ATTLIST generationidentifier %global-atts; class CDATA "+ topic/data xnal-d/generationidentifier ">
<!ATTLIST honorific   %global-atts; class CDATA "+ topic/data xnal-d/honorific ">
<!ATTLIST lastname    %global-atts; class CDATA "+ topic/data xnal-d/lastname ">
<!ATTLIST locality    %global-atts; class CDATA "+ topic/ph xnal-d/locality ">
<!ATTLIST localityname %global-atts; class CDATA "+ topic/ph xnal-d/localityname ">
<!ATTLIST middlename  %global-atts; class CDATA "+ topic/data xnal-d/middlename ">
<!ATTLIST namedetails %global-atts; class CDATA "+ topic/data xnal-d/namedetails ">
<!ATTLIST organizationinfo %global-atts; class CDATA "+ topic/data xnal-d/organizationinfo ">
<!ATTLIST organizationname %global-atts;  class CDATA "+ topic/ph xnal-d/organizationname ">
<!ATTLIST organizationnamedetails %global-atts; class CDATA "+ topic/ph xnal-d/organizationnamedetails ">
<!ATTLIST otherinfo   %global-atts; class CDATA "+ topic/data xnal-d/otherinfo ">
<!ATTLIST personinfo  %global-atts; class CDATA "+ topic/data xnal-d/personinfo ">
<!ATTLIST personname  %global-atts; class CDATA "+ topic/data xnal-d/personname ">
<!ATTLIST postalcode  %global-atts; class CDATA "+ topic/ph xnal-d/postalcode ">
<!ATTLIST thoroughfare %global-atts; class CDATA "+ topic/ph xnal-d/thoroughfare ">
<!ATTLIST url         %global-atts; class CDATA "+ topic/data xnal-d/url ">
<!ATTLIST urls        %global-atts; class CDATA "+ topic/data xnal-d/urls ">

<!-- ================== End DITA XNAL Domain  =================== -->