<%@ page contentType="text/xml; charset=UTF-8" language="java" session="false" %>
<%
  example.JSPHelper jspHelper = new example.JSPHelper();
%>
<c>
  <%=jspHelper.composeTag("t1", "a", Boolean.getBoolean("a"))%>
  <%=jspHelper.composeTag("t1", "a", Boolean.getBoolean("a"))%>
  <%=jspHelper.composeTag("t1", "a", Boolean.getBoolean("a"))%>
</c>