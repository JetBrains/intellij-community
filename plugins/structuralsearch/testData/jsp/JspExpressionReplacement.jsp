<%@ page contentType="text/xml; charset=UTF-8" language="java" session="false" %>
<%
  example.JSPHelper jspHelper = new example.JSPHelper();
%>
<c>
  <%=jspHelper.composeTag("t1", "a")%>
  <%=jspHelper.composeTag("t1", "a")%>
  <%=jspHelper.composeTag("t1", "a")%>
</c>