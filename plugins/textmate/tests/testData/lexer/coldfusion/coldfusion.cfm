<cfquery name="origData" datasource="Request.Site.DataSource">
    SELECT *
    FROM Whatever
    Where ID = <cfqueryparam value="1" cfsqltype="CF_QL_INTEG"/>
</cfquery>