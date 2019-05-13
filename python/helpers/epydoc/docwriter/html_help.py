#
# epydoc.css: default help page
# Edward Loper
#
# Created [01/30/01 05:18 PM]
# $Id: html_help.py 1239 2006-07-05 11:29:50Z edloper $
#

"""
Default help file for the HTML outputter (L{epydoc.docwriter.html}).

@type HTML_HELP: C{string}
@var HTML_HELP: The contents of the HTML body for the default
help page.
"""
__docformat__ = 'epytext en'

# Expects: {'this_project': name}
HTML_HELP = '''
<h1 class="epydoc"> API Documentation </h1>

<p> This document contains the API (Application Programming Interface)
documentation for %(this_project)s.  Documentation for the Python
objects defined by the project is divided into separate pages for each
package, module, and class.  The API documentation also includes two
pages containing information about the project as a whole: a trees
page, and an index page.  </p>

<h2> Object Documentation </h2>

  <p>Each <strong>Package Documentation</strong> page contains: </p>
  <ul>
    <li> A description of the package. </li>
    <li> A list of the modules and sub-packages contained by the
    package.  </li>
    <li> A summary of the classes defined by the package. </li>
    <li> A summary of the functions defined by the package. </li>
    <li> A summary of the variables defined by the package. </li>
    <li> A detailed description of each function defined by the
    package. </li>
    <li> A detailed description of each variable defined by the
    package. </li>
  </ul>
  
  <p>Each <strong>Module Documentation</strong> page contains:</p>
  <ul>
    <li> A description of the module. </li>
    <li> A summary of the classes defined by the module. </li>
    <li> A summary of the functions defined by the module. </li>
    <li> A summary of the variables defined by the module. </li>
    <li> A detailed description of each function defined by the
    module. </li>
    <li> A detailed description of each variable defined by the
    module. </li>
  </ul>
  
  <p>Each <strong>Class Documentation</strong> page contains: </p>
  <ul>
    <li> A class inheritance diagram. </li>
    <li> A list of known subclasses. </li>
    <li> A description of the class. </li>
    <li> A summary of the methods defined by the class. </li>
    <li> A summary of the instance variables defined by the class. </li>
    <li> A summary of the class (static) variables defined by the
    class. </li> 
    <li> A detailed description of each method defined by the
    class. </li>
    <li> A detailed description of each instance variable defined by the
    class. </li> 
    <li> A detailed description of each class (static) variable defined
    by the class. </li> 
  </ul>

<h2> Project Documentation </h2>

  <p> The <strong>Trees</strong> page contains the module and class hierarchies: </p>
  <ul>
    <li> The <em>module hierarchy</em> lists every package and module, with
    modules grouped into packages.  At the top level, and within each
    package, modules and sub-packages are listed alphabetically. </li>
    <li> The <em>class hierarchy</em> lists every class, grouped by base
    class.  If a class has more than one base class, then it will be
    listed under each base class.  At the top level, and under each base
    class, classes are listed alphabetically. </li>
  </ul>
  
  <p> The <strong>Index</strong> page contains indices of terms and
  identifiers: </p>
  <ul>
    <li> The <em>term index</em> lists every term indexed by any object\'s
    documentation.  For each term, the index provides links to each
    place where the term is indexed. </li>
    <li> The <em>identifier index</em> lists the (short) name of every package,
    module, class, method, function, variable, and parameter.  For each
    identifier, the index provides a short description, and a link to
    its documentation. </li>
  </ul>

<h2> The Table of Contents </h2>

<p> The table of contents occupies the two frames on the left side of
the window.  The upper-left frame displays the <em>project
contents</em>, and the lower-left frame displays the <em>module
contents</em>: </p>

<table class="help summary" border="1" cellspacing="0" cellpadding="3">
  <tr style="height: 30%%">
    <td align="center" style="font-size: small">
       Project<br />Contents<hr />...</td>
    <td align="center" style="font-size: small" rowspan="2" width="70%%">
      API<br />Documentation<br />Frame<br /><br /><br />
    </td>
  </tr>
  <tr>
    <td align="center" style="font-size: small">
      Module<br />Contents<hr />&nbsp;<br />...<br />&nbsp;
    </td>
  </tr>
</table><br />

<p> The <strong>project contents frame</strong> contains a list of all packages
and modules that are defined by the project.  Clicking on an entry
will display its contents in the module contents frame.  Clicking on a
special entry, labeled "Everything," will display the contents of
the entire project. </p>

<p> The <strong>module contents frame</strong> contains a list of every
submodule, class, type, exception, function, and variable defined by a
module or package.  Clicking on an entry will display its
documentation in the API documentation frame.  Clicking on the name of
the module, at the top of the frame, will display the documentation
for the module itself. </p>

<p> The "<strong>frames</strong>" and "<strong>no frames</strong>" buttons below the top
navigation bar can be used to control whether the table of contents is
displayed or not. </p>

<h2> The Navigation Bar </h2>

<p> A navigation bar is located at the top and bottom of every page.
It indicates what type of page you are currently viewing, and allows
you to go to related pages.  The following table describes the labels
on the navigation bar.  Note that not some labels (such as
[Parent]) are not displayed on all pages. </p>

<table class="summary" border="1" cellspacing="0" cellpadding="3" width="100%%">
<tr class="summary">
  <th>Label</th>
  <th>Highlighted when...</th>
  <th>Links to...</th>
</tr>
  <tr><td valign="top"><strong>[Parent]</strong></td>
      <td valign="top"><em>(never highlighted)</em></td>
      <td valign="top"> the parent of the current package </td></tr>
  <tr><td valign="top"><strong>[Package]</strong></td>
      <td valign="top">viewing a package</td>
      <td valign="top">the package containing the current object
      </td></tr>
  <tr><td valign="top"><strong>[Module]</strong></td>
      <td valign="top">viewing a module</td>
      <td valign="top">the module containing the current object
      </td></tr> 
  <tr><td valign="top"><strong>[Class]</strong></td>
      <td valign="top">viewing a class </td>
      <td valign="top">the class containing the current object</td></tr>
  <tr><td valign="top"><strong>[Trees]</strong></td>
      <td valign="top">viewing the trees page</td>
      <td valign="top"> the trees page </td></tr>
  <tr><td valign="top"><strong>[Index]</strong></td>
      <td valign="top">viewing the index page</td>
      <td valign="top"> the index page </td></tr>
  <tr><td valign="top"><strong>[Help]</strong></td>
      <td valign="top">viewing the help page</td>
      <td valign="top"> the help page </td></tr>
</table>

<p> The "<strong>show private</strong>" and "<strong>hide private</strong>" buttons below
the top navigation bar can be used to control whether documentation
for private objects is displayed.  Private objects are usually defined
as objects whose (short) names begin with a single underscore, but do
not end with an underscore.  For example, "<code>_x</code>",
"<code>__pprint</code>", and "<code>epydoc.epytext._tokenize</code>"
are private objects; but "<code>re.sub</code>",
"<code>__init__</code>", and "<code>type_</code>" are not.  However,
if a module defines the "<code>__all__</code>" variable, then its
contents are used to decide which objects are private. </p>

<p> A timestamp below the bottom navigation bar indicates when each
page was last updated. </p>
'''
