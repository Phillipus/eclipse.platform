<%@ page import="org.eclipse.help.servlet.Tocs,org.w3c.dom.*"%>

<% 
	// calls the utility class to initialize the application
	application.getRequestDispatcher("/servlet/org.eclipse.help.servlet.InitServlet").include(request,response);
%>


<html>
<head>
    <link rel="stylesheet" TYPE="text/css" HREF="toc.css" TITLE="nav">
      
<script language="JavaScript">

/**
 * Loads the specified table of contents
 */		
function selectTOC(tocId)
{
	// navigate to this toc
	window.location.href = "toc.jsp?toc="+tocId;
}

/**
 * This method is called when synchronizing the toc
 */
function selectTopic(topic)
{
	return false;
}

</script>

</head>

<body onload="parent.parent.ToolbarFrame.setToc('Bookshelf')">

<ul class='expanded'>
<% 
	Tocs tocs = (Tocs)application.getAttribute("org.eclipse.help.tocs");
	if (tocs == null)
		return;
	
	// Populate the combo box and select the appropriate infoset.
	// If this is the first time, pick the first one, else the one from request
	Element selectedTOC = (Element)session.getAttribute("org.eclipse.help.selectedTOC");
	Element[] tocNodes = tocs.getTocs();
	
	for (int i=0; i<tocNodes.length; i++)
	{
		String label = tocNodes[i].getAttribute("label");
		String id = tocNodes[i].getAttribute("href");
		
		if (tocNodes[i] == selectedTOC)
		{
%>
		<li class='node'><a class="selected" href='javascript:selectTOC("<%=id%>")' onmouseover='window.status="<%=label%>"'> <nobr> <%=label%> </nobr> </a></li>
<%
		}else{
%>
		<li class='node'><a href='javascript:selectTOC("<%=id%>");' onmouseover='window.status="<%=label%>"'><nobr> <%=label%> </nobr> </a></li>
<%
		}		
	}

%>
</ul>


</body>
</html>
