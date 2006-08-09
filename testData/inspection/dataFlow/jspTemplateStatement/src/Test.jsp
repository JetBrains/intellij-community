<%@ page import="java.util.ArrayList" %>
<jsp:useBean id="user_prefs"
             class="com.devexperts.rems.commandbeans.UserPrefsCmd"
             scope="request">
</jsp:useBean>
<html>
<head>
    <title>
        User preferences
    </title>
</head>
<link href="<%=request.getContextPath()%>/css/site.css" rel="stylesheet" type="text/css"/>
<body>
<%@ include file="header.jsp" %>
<p>
<table class='userprefs'>
    <tr>
        <th colspan='2'>
            Client information
        </th>
    </tr>
    <tr>
        <td>
            Name:
        </td>
        <td>
            <%=user_menu.getUserData().getName()%>
        </td>
    </tr>
    <tr>
        <td>
            Login:
        </td>
        <td>
            <%=user_menu.getUserData().getLogin()%>
        </td>
    </tr>
</table>
<p>

<form id='change_pass' action='<%=request.getContextPath()%>/UserPrefs' method='POST'>
    <table class='userprefs'>
        <tr>
            <th colspan='2'>
                Change login password
            </th>
        </tr>
        <tr>
            <td>
                New password:
            </td>
            <td>
                <input type='password' size='20' maxlength='20' name='p1'>
            </td>
        </tr>
        <tr>
            <td>
                New password again:
            </td>
            <td>
                <input type='password' size='20' maxlength='20' name='p2'>
            </td>
        </tr>
        <tr>
            <td>
            </td>
            <td>
                <input name='action' value='Change password' type='submit'>
            </td>
        </tr>
    </table>
</form>
<p>

<form id='change_timezone' action='<%=request.getContextPath()%>/UserPrefs' method='POST'>
    <table class='userprefs'>
        <tr>
            <th colspan='2'>
                Change timezone for reports
            </th>
        </tr>
        <tr>
            <td>
                Select TimeZone:
            </td>
            <td>
                <select name='tz'>
                    <%
     Object o = null;
     o.hashCode();
     String[] timezones = user_prefs.getAvailableTimezones();

     for (int i = 0; i < timezones.length; i++) { String tz = timezones[i]; if (tz.equals(user_menu.getUserData().getTzName()))
                    %> ("
                    <option selected
                            value='"); <%else %> sb.append("<option value='"); <%sb.append(tz); sb.append("'>"); sb.append(tz); sb.append("</option>"); } %>
                                    </ select>
            </td>
        </tr>
        <tr>
            <td>
            </td>
            <td>
                <input name='action' value='Change timezone' type='submit'>
            </td>
        </tr>
    </table>
</form>
</body>
</html>

