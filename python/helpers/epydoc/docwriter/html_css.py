#
# epydoc.css: default epydoc CSS stylesheets
# Edward Loper
#
# Created [01/30/01 05:18 PM]
# $Id: html_css.py 1634 2007-09-24 15:58:38Z dvarrazzo $
#

"""
Predefined CSS stylesheets for the HTML outputter (L{epydoc.docwriter.html}).

@type STYLESHEETS: C{dictionary} from C{string} to C{(string, string)}
@var STYLESHEETS: A dictionary mapping from stylesheet names to CSS
    stylesheets and descriptions.  A single stylesheet may have
    multiple names.  Currently, the following stylesheets are defined:
      - C{default}: The default stylesheet (synonym for C{white}).
      - C{white}: Black on white, with blue highlights (similar to
        javadoc).
      - C{blue}: Black on steel blue.
      - C{green}: Black on green.
      - C{black}: White on black, with blue highlights
      - C{grayscale}: Grayscale black on white.
      - C{none}: An empty stylesheet.
"""
__docformat__ = 'epytext en'

import re

############################################################
## Basic stylesheets
############################################################

# [xx] Should I do something like:
#
#    @import url(html4css1.css);
#
# But then where do I get that css file from?  Hm.
# Also, in principle I'm mangling classes, but it looks like I'm
# failing.
#

# Black on white, with blue highlights.  This is similar to how
# javadoc looks.
TEMPLATE = """

/* Epydoc CSS Stylesheet
 *
 * This stylesheet can be used to customize the appearance of epydoc's
 * HTML output.
 *
 */

/* Default Colors & Styles
 *   - Set the default foreground & background color with 'body'; and 
 *     link colors with 'a:link' and 'a:visited'.
 *   - Use bold for decision list terms.
 *   - The heading styles defined here are used for headings *within*
 *     docstring descriptions.  All headings used by epydoc itself use
 *     either class='epydoc' or class='toc' (CSS styles for both
 *     defined below).
 */
body                        { background: $body_bg; color: $body_fg; }
p                           { margin-top: 0.5em; margin-bottom: 0.5em; }
a:link                      { color: $body_link; }
a:visited                   { color: $body_visited_link; }
dt                          { font-weight: bold; }
h1                          { font-size: +140%; font-style: italic;
                              font-weight: bold; }
h2                          { font-size: +125%; font-style: italic;
                              font-weight: bold; }
h3                          { font-size: +110%; font-style: italic;
                              font-weight: normal; }
code                        { font-size: 100%; }
/* N.B.: class, not pseudoclass */
a.link                      { font-family: monospace; }
 
/* Page Header & Footer
 *   - The standard page header consists of a navigation bar (with
 *     pointers to standard pages such as 'home' and 'trees'); a
 *     breadcrumbs list, which can be used to navigate to containing
 *     classes or modules; options links, to show/hide private
 *     variables and to show/hide frames; and a page title (using
 *     <h1>).  The page title may be followed by a link to the
 *     corresponding source code (using 'span.codelink').
 *   - The footer consists of a navigation bar, a timestamp, and a
 *     pointer to epydoc's homepage.
 */ 
h1.epydoc                   { margin: 0; font-size: +140%; font-weight: bold; }
h2.epydoc                   { font-size: +130%; font-weight: bold; }
h3.epydoc                   { font-size: +115%; font-weight: bold;
                              margin-top: 0.2em; }
td h3.epydoc                { font-size: +115%; font-weight: bold;
                              margin-bottom: 0; }
table.navbar                { background: $navbar_bg; color: $navbar_fg;
                              border: $navbar_border; }
table.navbar table          { color: $navbar_fg; }
th.navbar-select            { background: $navbar_select_bg;
                              color: $navbar_select_fg; } 
table.navbar a              { text-decoration: none; }  
table.navbar a:link         { color: $navbar_link; }
table.navbar a:visited      { color: $navbar_visited_link; }
span.breadcrumbs            { font-size: 85%; font-weight: bold; }
span.options                { font-size: 70%; }
span.codelink               { font-size: 85%; }
td.footer                   { font-size: 85%; }

/* Table Headers
 *   - Each summary table and details section begins with a 'header'
 *     row.  This row contains a section title (marked by
 *     'span.table-header') as well as a show/hide private link
 *     (marked by 'span.options', defined above).
 *   - Summary tables that contain user-defined groups mark those
 *     groups using 'group header' rows.
 */
td.table-header             { background: $table_hdr_bg; color: $table_hdr_fg;
                              border: $table_border; }
td.table-header table       { color: $table_hdr_fg; }
td.table-header table a:link      { color: $table_hdr_link; }
td.table-header table a:visited   { color: $table_hdr_visited_link; }
span.table-header           { font-size: 120%; font-weight: bold; }
th.group-header             { background: $group_hdr_bg; color: $group_hdr_fg;
                              text-align: left; font-style: italic; 
                              font-size: 115%; 
                              border: $table_border; }

/* Summary Tables (functions, variables, etc)
 *   - Each object is described by a single row of the table with
 *     two cells.  The left cell gives the object's type, and is
 *     marked with 'code.summary-type'.  The right cell gives the
 *     object's name and a summary description.
 *   - CSS styles for the table's header and group headers are
 *     defined above, under 'Table Headers'
 */
table.summary               { border-collapse: collapse;
                              background: $table_bg; color: $table_fg;
                              border: $table_border;
                              margin-bottom: 0.5em; }
td.summary                  { border: $table_border; }
code.summary-type           { font-size: 85%; }
table.summary a:link        { color: $table_link; }
table.summary a:visited     { color: $table_visited_link; }


/* Details Tables (functions, variables, etc)
 *   - Each object is described in its own div.
 *   - A single-row summary table w/ table-header is used as
 *     a header for each details section (CSS style for table-header
 *     is defined above, under 'Table Headers').
 */
table.details               { border-collapse: collapse;
                              background: $table_bg; color: $table_fg;
                              border: $table_border;
                              margin: .2em 0 0 0; }
table.details table         { color: $table_fg; }
table.details a:link        { color: $table_link; }
table.details a:visited     { color: $table_visited_link; }

/* Fields */
dl.fields                   { margin-left: 2em; margin-top: 1em;
                              margin-bottom: 1em; }
dl.fields dd ul             { margin-left: 0em; padding-left: 0em; }
dl.fields dd ul li ul       { margin-left: 2em; padding-left: 0em; }
div.fields                  { margin-left: 2em; }
div.fields p                { margin-bottom: 0.5em; }

/* Index tables (identifier index, term index, etc)
 *   - link-index is used for indices containing lists of links
 *     (namely, the identifier index & term index).
 *   - index-where is used in link indices for the text indicating
 *     the container/source for each link.
 *   - metadata-index is used for indices containing metadata
 *     extracted from fields (namely, the bug index & todo index).
 */
table.link-index            { border-collapse: collapse;
                              background: $table_bg; color: $table_fg;
                              border: $table_border; }
td.link-index               { border-width: 0px; }
table.link-index a:link     { color: $table_link; }
table.link-index a:visited  { color: $table_visited_link; }
span.index-where            { font-size: 70%; }
table.metadata-index        { border-collapse: collapse;
                              background: $table_bg; color: $table_fg;
                              border: $table_border; 
                              margin: .2em 0 0 0; }
td.metadata-index           { border-width: 1px; border-style: solid; }
table.metadata-index a:link { color: $table_link; }
table.metadata-index a:visited  { color: $table_visited_link; }

/* Function signatures
 *   - sig* is used for the signature in the details section.
 *   - .summary-sig* is used for the signature in the summary 
 *     table, and when listing property accessor functions.
 * */
.sig-name                   { color: $sig_name; }
.sig-arg                    { color: $sig_arg; }
.sig-default                { color: $sig_default; }
.summary-sig                { font-family: monospace; }
.summary-sig-name           { color: $summary_sig_name; font-weight: bold; }
table.summary a.summary-sig-name:link
                            { color: $summary_sig_name; font-weight: bold; }
table.summary a.summary-sig-name:visited
                            { color: $summary_sig_name; font-weight: bold; }
.summary-sig-arg            { color: $summary_sig_arg; }
.summary-sig-default        { color: $summary_sig_default; }

/* Subclass list
 */
ul.subclass-list { display: inline; }
ul.subclass-list li { display: inline; }

/* To render variables, classes etc. like functions */
table.summary .summary-name { color: $summary_sig_name; font-weight: bold;
                              font-family: monospace; }
table.summary
     a.summary-name:link    { color: $summary_sig_name; font-weight: bold;
                              font-family: monospace; }
table.summary
    a.summary-name:visited  { color: $summary_sig_name; font-weight: bold;
                              font-family: monospace; }

/* Variable values
 *   - In the 'variable details' sections, each varaible's value is
 *     listed in a 'pre.variable' box.  The width of this box is
 *     restricted to 80 chars; if the value's repr is longer than
 *     this it will be wrapped, using a backslash marked with
 *     class 'variable-linewrap'.  If the value's repr is longer
 *     than 3 lines, the rest will be ellided; and an ellipsis
 *     marker ('...' marked with 'variable-ellipsis') will be used.
 *   - If the value is a string, its quote marks will be marked
 *     with 'variable-quote'.
 *   - If the variable is a regexp, it is syntax-highlighted using
 *     the re* CSS classes.
 */
pre.variable                { padding: .5em; margin: 0;
                              background: $variable_bg; color: $variable_fg;
                              border: $variable_border; }
.variable-linewrap          { color: $variable_linewrap; font-weight: bold; }
.variable-ellipsis          { color: $variable_ellipsis; font-weight: bold; }
.variable-quote             { color: $variable_quote; font-weight: bold; }
.variable-group             { color: $variable_group; font-weight: bold; }
.variable-op                { color: $variable_op; font-weight: bold; }
.variable-string            { color: $variable_string; }
.variable-unknown           { color: $variable_unknown; font-weight: bold; }
.re                         { color: $re; }
.re-char                    { color: $re_char; }
.re-op                      { color: $re_op; }
.re-group                   { color: $re_group; }
.re-ref                     { color: $re_ref; }

/* Base tree
 *   - Used by class pages to display the base class hierarchy.
 */
pre.base-tree               { font-size: 80%; margin: 0; }

/* Frames-based table of contents headers
 *   - Consists of two frames: one for selecting modules; and
 *     the other listing the contents of the selected module.
 *   - h1.toc is used for each frame's heading
 *   - h2.toc is used for subheadings within each frame.
 */
h1.toc                      { text-align: center; font-size: 105%;
                              margin: 0; font-weight: bold;
                              padding: 0; }
h2.toc                      { font-size: 100%; font-weight: bold; 
                              margin: 0.5em 0 0 -0.3em; }

/* Syntax Highlighting for Source Code
 *   - doctest examples are displayed in a 'pre.py-doctest' block.
 *     If the example is in a details table entry, then it will use
 *     the colors specified by the 'table pre.py-doctest' line.
 *   - Source code listings are displayed in a 'pre.py-src' block.
 *     Each line is marked with 'span.py-line' (used to draw a line
 *     down the left margin, separating the code from the line
 *     numbers).  Line numbers are displayed with 'span.py-lineno'.
 *     The expand/collapse block toggle button is displayed with
 *     'a.py-toggle' (Note: the CSS style for 'a.py-toggle' should not
 *     modify the font size of the text.)
 *   - If a source code page is opened with an anchor, then the
 *     corresponding code block will be highlighted.  The code
 *     block's header is highlighted with 'py-highlight-hdr'; and
 *     the code block's body is highlighted with 'py-highlight'.
 *   - The remaining py-* classes are used to perform syntax
 *     highlighting (py-string for string literals, py-name for names,
 *     etc.)
 */
pre.py-doctest              { padding: .5em; margin: 1em;
                              background: $doctest_bg; color: $doctest_fg;
                              border: $doctest_border; }
table pre.py-doctest        { background: $doctest_in_table_bg;
                              color: $doctest_in_table_fg; }
pre.py-src                  { border: $pysrc_border; 
                              background: $pysrc_bg; color: $pysrc_fg; }
.py-line                    { border-left: $pysrc_sep_border; 
                              margin-left: .2em; padding-left: .4em; }
.py-lineno                  { font-style: italic; font-size: 90%;
                              padding-left: .5em; }
a.py-toggle                 { text-decoration: none; }
div.py-highlight-hdr        { border-top: $pysrc_border;
                              border-bottom: $pysrc_border;
                              background: $pysrc_highlight_hdr_bg; }
div.py-highlight            { border-bottom: $pysrc_border;
                              background: $pysrc_highlight_bg; }
.py-prompt                  { color: $py_prompt; font-weight: bold;}
.py-more                    { color: $py_more; font-weight: bold;}
.py-string                  { color: $py_string; }
.py-comment                 { color: $py_comment; }
.py-keyword                 { color: $py_keyword; }
.py-output                  { color: $py_output; }
.py-name                    { color: $py_name; }
.py-name:link               { color: $py_name !important; }
.py-name:visited            { color: $py_name !important; }
.py-number                  { color: $py_number; }
.py-defname                 { color: $py_def_name; font-weight: bold; }
.py-def-name                { color: $py_def_name; font-weight: bold; }
.py-base-class              { color: $py_base_class; }
.py-param                   { color: $py_param; }
.py-docstring               { color: $py_docstring; }
.py-decorator               { color: $py_decorator; }
/* Use this if you don't want links to names underlined: */
/*a.py-name                   { text-decoration: none; }*/

/* Graphs & Diagrams
 *   - These CSS styles are used for graphs & diagrams generated using
 *     Graphviz dot.  'img.graph-without-title' is used for bare
 *     diagrams (to remove the border created by making the image
 *     clickable).
 */
img.graph-without-title     { border: none; }
img.graph-with-title        { border: $graph_border; }
span.graph-title            { font-weight: bold; }
span.graph-caption          { }

/* General-purpose classes
 *   - 'p.indent-wrapped-lines' defines a paragraph whose first line
 *     is not indented, but whose subsequent lines are.
 *   - The 'nomargin-top' class is used to remove the top margin (e.g.
 *     from lists).  The 'nomargin' class is used to remove both the
 *     top and bottom margin (but not the left or right margin --
 *     for lists, that would cause the bullets to disappear.)
 */
p.indent-wrapped-lines      { padding: 0 0 0 7em; text-indent: -7em; 
                              margin: 0; }
.nomargin-top               { margin-top: 0; }
.nomargin                   { margin-top: 0; margin-bottom: 0; }

/* HTML Log */
div.log-block               { padding: 0; margin: .5em 0 .5em 0;
                              background: $log_bg; color: $log_fg;
                              border: $log_border; }
div.log-error               { padding: .1em .3em .1em .3em; margin: 4px;
                              background: $log_error_bg; color: $log_error_fg;
                              border: $log_error_border; }
div.log-warning             { padding: .1em .3em .1em .3em; margin: 4px;
                              background: $log_warn_bg; color: $log_warn_fg;
                              border: $log_warn_border; }
div.log-info               { padding: .1em .3em .1em .3em; margin: 4px;
                              background: $log_info_bg; color: $log_info_fg;
                              border: $log_info_border; }
h2.log-hdr                  { background: $log_hdr_bg; color: $log_hdr_fg;
                              margin: 0; padding: 0em 0.5em 0em 0.5em;
                              border-bottom: $log_border; font-size: 110%; }
p.log                       { font-weight: bold; margin: .5em 0 .5em 0; }
tr.opt-changed              { color: $opt_changed_fg; font-weight: bold; }
tr.opt-default              { color: $opt_default_fg; }
pre.log                     { margin: 0; padding: 0; padding-left: 1em; }
""" 

############################################################
## Derived stylesheets
############################################################
# Use some simple manipulations to produce a wide variety of color
# schemes.  In particular, use th _COLOR_RE regular expression to
# search for colors, and to transform them in various ways.

_COLOR_RE = re.compile(r'#(..)(..)(..)')

def _set_colors(template, *dicts):
    colors = dicts[0].copy()
    for d in dicts[1:]: colors.update(d)
    return re.sub(r'\$(\w+)', lambda m:colors[m.group(1)], template)
    
def _rv(match):
    """
    Given a regexp match for a color, return the reverse-video version
    of that color.

    @param match: A regular expression match.
    @type match: C{Match}
    @return: The reverse-video color.
    @rtype: C{string}
    """
    rgb = [int(grp, 16) for grp in match.groups()]
    return '#' + ''.join(['%02x' % (255-c) for c in rgb])

def _darken_darks(match):
    rgb = [int(grp, 16) for grp in match.groups()]
    return '#' + ''.join(['%02x' % (((c/255.)**2) * 255) for c in rgb])

_WHITE_COLORS = dict(
    # Defaults:
    body_bg                 =  '#ffffff',
    body_fg                 =  '#000000',
    body_link               =  '#0000ff',
    body_visited_link       =  '#204080',
    # Navigation bar:
    navbar_bg               =  '#a0c0ff',
    navbar_fg               =  '#000000',
    navbar_border           =  '2px groove #c0d0d0',
    navbar_select_bg        =  '#70b0ff',
    navbar_select_fg        =  '#000000',
    navbar_link             =  '#0000ff',
    navbar_visited_link     =  '#204080',
    # Tables (summary tables, details tables, indices):
    table_bg                =  '#e8f0f8',
    table_fg                =  '#000000',
    table_link              =  '#0000ff',
    table_visited_link      =  '#204080',
    table_border            =  '1px solid #608090',
    table_hdr_bg            =  '#70b0ff',
    table_hdr_fg            =  '#000000', 
    table_hdr_link          =  '#0000ff',
    table_hdr_visited_link  =  '#204080',
    group_hdr_bg            =  '#c0e0f8',
    group_hdr_fg            =  '#000000',
    # Function signatures:
    sig_name                =  '#006080',
    sig_arg                 =  '#008060',
    sig_default             =  '#602000',
    summary_sig_name        =  '#006080',
    summary_sig_arg         =  '#006040',
    summary_sig_default     =  '#501800',
    # Variable values:
    variable_bg             =  '#dce4ec',
    variable_fg             =  '#000000',
    variable_border         =  '1px solid #708890',
    variable_linewrap       =  '#604000',
    variable_ellipsis       =  '#604000',
    variable_quote          =  '#604000',
    variable_group          =  '#008000',
    variable_string         =  '#006030',
    variable_op             =  '#604000',
    variable_unknown        =  '#a00000',
    re                      =  '#000000',
    re_char                 =  '#006030',
    re_op                   =  '#600000',
    re_group                =  '#003060',
    re_ref                  =  '#404040',
    # Python source code:
    doctest_bg              =  '#e8f0f8',
    doctest_fg              =  '#000000',
    doctest_border          =  '1px solid #708890',
    doctest_in_table_bg     =  '#dce4ec',
    doctest_in_table_fg     =  '#000000',
    pysrc_border            =  '2px solid #000000',
    pysrc_sep_border        =  '2px solid #000000',
    pysrc_bg                =  '#f0f0f0',
    pysrc_fg                =  '#000000',
    pysrc_highlight_hdr_bg  =  '#d8e8e8',
    pysrc_highlight_bg      =  '#d0e0e0',
    py_prompt               =  '#005050',
    py_more                 =  '#005050',
    py_string               =  '#006030',
    py_comment              =  '#003060',
    py_keyword              =  '#600000',
    py_output               =  '#404040',
    py_name                 =  '#000050',
    py_number               =  '#005000',
    py_def_name             =  '#000060',
    py_base_class           =  '#000060',
    py_param                =  '#000060',
    py_docstring            =  '#006030',
    py_decorator            =  '#804020',
    # Graphs
    graph_border            =  '1px solid #000000',
    # Log block
    log_bg                  =  '#e8f0f8',
    log_fg                  =  '#000000',
    log_border              =  '1px solid #000000',
    log_hdr_bg              =  '#70b0ff',
    log_hdr_fg              =  '#000000',
    log_error_bg            =  '#ffb0b0',
    log_error_fg            =  '#000000',
    log_error_border        =  '1px solid #000000',
    log_warn_bg             =  '#ffffb0',
    log_warn_fg             =  '#000000',
    log_warn_border         =  '1px solid #000000',
    log_info_bg             =  '#b0ffb0',
    log_info_fg             =  '#000000',
    log_info_border         =  '1px solid #000000',
    opt_changed_fg          =  '#000000',
    opt_default_fg          =  '#606060',
    )

_BLUE_COLORS = _WHITE_COLORS.copy()
_BLUE_COLORS.update(dict(
    # Body: white text on a dark blue background
    body_bg                 =  '#000070',
    body_fg                 =  '#ffffff',
    body_link               =  '#ffffff',
    body_visited_link       =  '#d0d0ff',
    # Tables: cyan headers, black on white bodies
    table_bg                =   '#ffffff',
    table_fg                =  '#000000',
    table_hdr_bg            =  '#70b0ff',
    table_hdr_fg            =  '#000000',
    table_hdr_link          =  '#000000',
    table_hdr_visited_link  =  '#000000',
    table_border            =  '1px solid #000000',
    # Navigation bar: blue w/ cyan selection
    navbar_bg               =  '#0000ff',
    navbar_fg               =  '#ffffff',
    navbar_link             =  '#ffffff',
    navbar_visited_link     =  '#ffffff',
    navbar_select_bg        =   '#70b0ff',
    navbar_select_fg        =   '#000000',
    navbar_border           =  '1px solid #70b0ff',
    # Variable values & doctest blocks: cyan
    variable_bg             =   '#c0e0f8',
    variable_fg             =  '#000000',
    doctest_bg              =   '#c0e0f8',
    doctest_fg              =  '#000000',
    doctest_in_table_bg     =   '#c0e0f8',
    doctest_in_table_fg     =  '#000000',
    ))

_WHITE = _set_colors(TEMPLATE, _WHITE_COLORS)
_BLUE = _set_colors(TEMPLATE, _BLUE_COLORS)

    # Black-on-green
_GREEN = _COLOR_RE.sub(_darken_darks, _COLOR_RE.sub(r'#\1\3\2', _BLUE))

# White-on-black, with blue highlights.
_BLACK = _COLOR_RE.sub(r'#\3\2\1', _COLOR_RE.sub(_rv, _WHITE))

# Grayscale
_GRAYSCALE = _COLOR_RE.sub(r'#\2\2\2', _WHITE)

############################################################
## Stylesheet table
############################################################

STYLESHEETS = {
    'white': (_WHITE, "Black on white, with blue highlights"),
    'blue': (_BLUE, "Black on steel blue"),
    'green': (_GREEN, "Black on green"),
    'black': (_BLACK, "White on black, with blue highlights"),
    'grayscale': (_GRAYSCALE, "Grayscale black on white"),
    'default': (_WHITE, "Default stylesheet (=white)"),
#    'none': (_LAYOUT, "A base stylesheet (no color modifications)"),
    }
