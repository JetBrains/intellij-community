<%-- Sample comment --%>
<%@ page import="com.intellij.*" %>
<jsp:useBean id="info" class="com.intellij.Info" />
<%!
  int i = 0;
%>
<html>
<body>
Welcome to IntelliJ IDEA
${ (info['version'] le 12 )? "Evaluate latest version":"" }
Release timestamp: ${ info.getReleaseDate(majorVersion, minorVersion).time }
<% for (i = 1; i < 5; i++) { %>
<h<%= i %>>Try it, it's cool!</h<%= i %>>
<% } %>
</body>
</html>
<!-- HTML comments are seen by clients, JSP aren't -->