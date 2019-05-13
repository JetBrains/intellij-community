#
# epydoc.py: epydoc LaTeX output generator
# Edward Loper
#
# Created [01/30/01 05:18 PM]
# $Id: latex.py 1621 2007-09-23 18:54:23Z edloper $
#

"""
The LaTeX output generator for epydoc.  The main interface provided by
this module is the L{LatexWriter} class.

@todo: Inheritance=listed
"""
__docformat__ = 'epytext en'

import os.path, sys, time, re, textwrap, codecs

from epydoc.apidoc import *
from epydoc.compat import *
import epydoc
from epydoc import log
from epydoc import markup
from epydoc.util import plaintext_to_latex
import epydoc.markup

class LatexWriter:
    PREAMBLE = [
        "\\documentclass{article}",
        "\\usepackage{alltt, parskip, fancyhdr, boxedminipage}",
        "\\usepackage{makeidx, multirow, longtable, tocbibind, amssymb}",
        "\\usepackage{fullpage}",
        "\\usepackage[usenames]{color}",
        # Fix the heading position -- without this, the headings generated
        # by the fancyheadings package sometimes overlap the text.
        "\\setlength{\\headheight}{16pt}",
        "\\setlength{\\headsep}{24pt}",
        "\\setlength{\\topmargin}{-\\headsep}",
        # By default, do not indent paragraphs.
        "\\setlength{\\parindent}{0ex}",
        "\\setlength{\\parskip}{2ex}",
        # Double the standard size boxedminipage outlines.
        "\\setlength{\\fboxrule}{2\\fboxrule}",
        # Create a 'base class' length named BCL for use in base trees.
        "\\newlength{\\BCL} % base class length, for base trees.",
        # Display the section & subsection names in a header.
        "\\pagestyle{fancy}",
        "\\renewcommand{\\sectionmark}[1]{\\markboth{#1}{}}",
        "\\renewcommand{\\subsectionmark}[1]{\\markright{#1}}",
        # Colorization for python source code
        "\\definecolor{py@keywordcolour}{rgb}{1,0.45882,0}",
        "\\definecolor{py@stringcolour}{rgb}{0,0.666666,0}",
        "\\definecolor{py@commentcolour}{rgb}{1,0,0}",
        "\\definecolor{py@ps1colour}{rgb}{0.60784,0,0}",
        "\\definecolor{py@ps2colour}{rgb}{0.60784,0,1}",
        "\\definecolor{py@inputcolour}{rgb}{0,0,0}",
        "\\definecolor{py@outputcolour}{rgb}{0,0,1}",
        "\\definecolor{py@exceptcolour}{rgb}{1,0,0}",
        "\\definecolor{py@defnamecolour}{rgb}{1,0.5,0.5}",
        "\\definecolor{py@builtincolour}{rgb}{0.58039,0,0.58039}",
        "\\definecolor{py@identifiercolour}{rgb}{0,0,0}",
        "\\definecolor{py@linenumcolour}{rgb}{0.4,0.4,0.4}",
        "\\definecolor{py@inputcolour}{rgb}{0,0,0}",
        "% Prompt",
        "\\newcommand{\\pysrcprompt}[1]{\\textcolor{py@ps1colour}"
            "{\\small\\textbf{#1}}}",
        "\\newcommand{\\pysrcmore}[1]{\\textcolor{py@ps2colour}"
            "{\\small\\textbf{#1}}}",
        "% Source code",
        "\\newcommand{\\pysrckeyword}[1]{\\textcolor{py@keywordcolour}"
            "{\\small\\textbf{#1}}}",
        "\\newcommand{\\pysrcbuiltin}[1]{\\textcolor{py@builtincolour}"
            "{\\small\\textbf{#1}}}",
        "\\newcommand{\\pysrcstring}[1]{\\textcolor{py@stringcolour}"
            "{\\small\\textbf{#1}}}",
        "\\newcommand{\\pysrcdefname}[1]{\\textcolor{py@defnamecolour}"
            "{\\small\\textbf{#1}}}",
        "\\newcommand{\\pysrcother}[1]{\\small\\textbf{#1}}",
        "% Comments",
        "\\newcommand{\\pysrccomment}[1]{\\textcolor{py@commentcolour}"
            "{\\small\\textbf{#1}}}",
        "% Output",
        "\\newcommand{\\pysrcoutput}[1]{\\textcolor{py@outputcolour}"
            "{\\small\\textbf{#1}}}",
        "% Exceptions",
        "\\newcommand{\\pysrcexcept}[1]{\\textcolor{py@exceptcolour}"
            "{\\small\\textbf{#1}}}",
        # Size of the function description boxes.
        "\\newlength{\\funcindent}",
        "\\newlength{\\funcwidth}",
        "\\setlength{\\funcindent}{1cm}",
        "\\setlength{\\funcwidth}{\\textwidth}",
        "\\addtolength{\\funcwidth}{-2\\funcindent}",
        # Size of the var description tables.
        "\\newlength{\\varindent}",
        "\\newlength{\\varnamewidth}",
        "\\newlength{\\vardescrwidth}",
        "\\newlength{\\varwidth}",
        "\\setlength{\\varindent}{1cm}",
        "\\setlength{\\varnamewidth}{.3\\textwidth}",
        "\\setlength{\\varwidth}{\\textwidth}",
        "\\addtolength{\\varwidth}{-4\\tabcolsep}",
        "\\addtolength{\\varwidth}{-3\\arrayrulewidth}",
        "\\addtolength{\\varwidth}{-2\\varindent}",
        "\\setlength{\\vardescrwidth}{\\varwidth}",
        "\\addtolength{\\vardescrwidth}{-\\varnamewidth}",
        # Define new environment for displaying parameter lists.
        textwrap.dedent("""\
        \\newenvironment{Ventry}[1]%
         {\\begin{list}{}{%
           \\renewcommand{\\makelabel}[1]{\\texttt{##1:}\\hfil}%
           \\settowidth{\\labelwidth}{\\texttt{#1:}}%
           \\setlength{\\leftmargin}{\\labelsep}%
           \\addtolength{\\leftmargin}{\\labelwidth}}}%
         {\\end{list}}"""),
        ]

    HRULE = '\\rule{\\textwidth}{0.5\\fboxrule}\n\n'

    SECTIONS = ['\\part{%s}', '\\chapter{%s}', '\\section{%s}',
                '\\subsection{%s}', '\\subsubsection{%s}',
                '\\textbf{%s}']

    STAR_SECTIONS = ['\\part*{%s}', '\\chapter*{%s}', '\\section*{%s}',
                     '\\subsection*{%s}', '\\subsubsection*{%s}',
                     '\\textbf{%s}']

    def __init__(self, docindex, **kwargs):
        self.docindex = docindex
        # Process keyword arguments
        self._show_private = kwargs.get('private', 0)
        self._prj_name = kwargs.get('prj_name', None) or 'API Documentation'
        self._crossref = kwargs.get('crossref', 1)
        self._index = kwargs.get('index', 1)
        self._list_classes_separately=kwargs.get('list_classes_separately',0)
        self._inheritance = kwargs.get('inheritance', 'listed')
        self._exclude = kwargs.get('exclude', 1)
        self._top_section = 2
        self._index_functions = 1
        self._hyperref = 1

        #: The Python representation of the encoding.
        #: Update L{latex_encodings} in case of mismatch between it and
        #: the C{inputenc} LaTeX package.
        self._encoding = kwargs.get('encoding', 'utf-8')

        self.valdocs = valdocs = sorted(docindex.reachable_valdocs(
            imports=False, packages=False, bases=False, submodules=False, 
            subclasses=False, private=self._show_private))
        self._num_files = self.num_files()
        # For use with select_variables():
        if self._show_private: self._public_filter = None
        else: self._public_filter = True

        self.class_list = [d for d in valdocs if isinstance(d, ClassDoc)]
        """The list of L{ClassDoc}s for the documented classes."""
        self.class_set = set(self.class_list)
        """The set of L{ClassDoc}s for the documented classes."""
        
    def write(self, directory=None):
        """
        Write the API documentation for the entire project to the
        given directory.

        @type directory: C{string}
        @param directory: The directory to which output should be
            written.  If no directory is specified, output will be
            written to the current directory.  If the directory does
            not exist, it will be created.
        @rtype: C{None}
        @raise OSError: If C{directory} cannot be created,
        @raise OSError: If any file cannot be created or written to.
        """
        # For progress reporting:
        self._files_written = 0.
        
        # Set the default values for ValueDoc formatted representations.
        orig_valdoc_defaults = (ValueDoc.SUMMARY_REPR_LINELEN,
                                ValueDoc.REPR_LINELEN,
                                ValueDoc.REPR_MAXLINES)
        ValueDoc.SUMMARY_REPR_LINELEN = 60
        ValueDoc.REPR_LINELEN = 52
        ValueDoc.REPR_MAXLINES = 5

        # Create destination directories, if necessary
        if not directory: directory = os.curdir
        self._mkdir(directory)
        self._directory = directory
        
        # Write the top-level file.
        self._write(self.write_topfile, directory, 'api.tex')

        # Write the module & class files.
        for val_doc in self.valdocs:
            if isinstance(val_doc, ModuleDoc):
                filename = '%s-module.tex' % val_doc.canonical_name
                self._write(self.write_module, directory, filename, val_doc)
            elif (isinstance(val_doc, ClassDoc) and 
                  self._list_classes_separately):
                filename = '%s-class.tex' % val_doc.canonical_name
                self._write(self.write_class, directory, filename, val_doc)

        # Restore defaults that we changed.
        (ValueDoc.SUMMARY_REPR_LINELEN, ValueDoc.REPR_LINELEN,
         ValueDoc.REPR_MAXLINES) = orig_valdoc_defaults
        
    def _write(self, write_func, directory, filename, *args):
        # Display our progress.
        self._files_written += 1
        log.progress(self._files_written/self._num_files, filename)
        
        path = os.path.join(directory, filename)
        if self._encoding == 'utf-8':
            f = codecs.open(path, 'w', 'utf-8')
            write_func(f.write, *args)
            f.close()
        else:
            result = []
            write_func(result.append, *args)
            s = u''.join(result)
            try:
                s = s.encode(self._encoding)
            except UnicodeError:
                log.error("Output could not be represented with the "
                          "given encoding (%r).  Unencodable characters "
                          "will be displayed as '?'.  It is recommended "
                          "that you use a different output encoding (utf-8, "
                          "if it's supported by latex on your system)."
                          % self._encoding)
                s = s.encode(self._encoding, 'replace')
            f = open(path, 'w')
            f.write(s)
            f.close()

    def num_files(self):
        """
        @return: The number of files that this C{LatexFormatter} will
            generate.
        @rtype: C{int}
        """
        n = 1
        for doc in self.valdocs:
            if isinstance(doc, ModuleDoc): n += 1
            if isinstance(doc, ClassDoc) and self._list_classes_separately:
                n += 1
        return n
        
    def _mkdir(self, directory):
        """
        If the given directory does not exist, then attempt to create it.
        @rtype: C{None}
        """
        if not os.path.isdir(directory):
            if os.path.exists(directory):
                raise OSError('%r is not a directory' % directory)
            os.mkdir(directory)
            
    #////////////////////////////////////////////////////////////
    #{ Main Doc File
    #////////////////////////////////////////////////////////////

    def write_topfile(self, out):
        self.write_header(out, 'Include File')
        self.write_preamble(out)
        out('\n\\begin{document}\n\n')
        self.write_start_of(out, 'Header')

        # Write the title.
        self.write_start_of(out, 'Title')
        out('\\title{%s}\n' % plaintext_to_latex(self._prj_name, 1))
        out('\\author{API Documentation}\n')
        out('\\maketitle\n')

        # Add a table of contents.
        self.write_start_of(out, 'Table of Contents')
        out('\\addtolength{\\parskip}{-2ex}\n')
        out('\\tableofcontents\n')
        out('\\addtolength{\\parskip}{2ex}\n')

        # Include documentation files.
        self.write_start_of(out, 'Includes')
        for val_doc in self.valdocs:
            if isinstance(val_doc, ModuleDoc):
                out('\\include{%s-module}\n' % val_doc.canonical_name)

        # If we're listing classes separately, put them after all the
        # modules.
        if self._list_classes_separately:
            for val_doc in self.valdocs:
                if isinstance(val_doc, ClassDoc):
                    out('\\include{%s-class}\n' % val_doc.canonical_name)

        # Add the index, if requested.
        if self._index:
            self.write_start_of(out, 'Index')
            out('\\printindex\n\n')

        # Add the footer.
        self.write_start_of(out, 'Footer')
        out('\\end{document}\n\n')

    def write_preamble(self, out):
        out('\n'.join(self.PREAMBLE))
        out('\n')
        
        # Set the encoding.
        out('\\usepackage[%s]{inputenc}\n' % self.get_latex_encoding())

        # If we're generating hyperrefs, add the appropriate packages.
        if self._hyperref:
            out('\\definecolor{UrlColor}{rgb}{0,0.08,0.45}\n')
            out('\\usepackage[dvips, pagebackref, pdftitle={%s}, '
                'pdfcreator={epydoc %s}, bookmarks=true, '
                'bookmarksopen=false, pdfpagemode=UseOutlines, '
                'colorlinks=true, linkcolor=black, anchorcolor=black, '
                'citecolor=black, filecolor=black, menucolor=black, '
                'pagecolor=black, urlcolor=UrlColor]{hyperref}\n' %
                (self._prj_name or '', epydoc.__version__))
            
        # If we're generating an index, add it to the preamble.
        if self._index:
            out("\\makeindex\n")

        # If restructuredtext was used, then we need to extend
        # the prefix to include LatexTranslator.head_prefix.
        if 'restructuredtext' in epydoc.markup.MARKUP_LANGUAGES_USED:
            from epydoc.markup import restructuredtext
            rst_head = restructuredtext.latex_head_prefix()
            rst_head = ''.join(rst_head).split('\n')
            for line in rst_head[1:]:
                m = re.match(r'\\usepackage(\[.*?\])?{(.*?)}', line)
                if m and m.group(2) in (
                    'babel', 'hyperref', 'color', 'alltt', 'parskip',
                    'fancyhdr', 'boxedminipage', 'makeidx',
                    'multirow', 'longtable', 'tocbind', 'assymb',
                    'fullpage', 'inputenc'):
                    pass
                else:
                    out(line+'\n')

        
    #////////////////////////////////////////////////////////////
    #{ Chapters
    #////////////////////////////////////////////////////////////

    def write_module(self, out, doc):
        self.write_header(out, doc)
        self.write_start_of(out, 'Module Description')

        # Add this module to the index.
        out('    ' + self.indexterm(doc, 'start'))

        # Add a section marker.
        out(self.section('%s %s' % (self.doc_kind(doc),
                                    doc.canonical_name)))

        # Label our current location.
        out('    \\label{%s}\n' % self.label(doc))

        # Add the module's description.
        if doc.descr not in (None, UNKNOWN):
            out(self.docstring_to_latex(doc.descr))

        # Add version, author, warnings, requirements, notes, etc.
        self.write_standard_fields(out, doc)

        # If it's a package, list the sub-modules.
        if doc.submodules != UNKNOWN and doc.submodules:
            self.write_module_list(out, doc)

        # Contents.
        if self._list_classes_separately:
            self.write_class_list(out, doc)
        self.write_func_list(out, 'Functions', doc, 'function')
        self.write_var_list(out, 'Variables', doc, 'other')

        # Class list.
        if not self._list_classes_separately:
            classes = doc.select_variables(imported=False, value_type='class',
                                           public=self._public_filter)
            for var_doc in classes:
                self.write_class(out, var_doc.value)

        # Mark the end of the module (for the index)
        out('    ' + self.indexterm(doc, 'end'))

    def write_class(self, out, doc):
        if self._list_classes_separately:
            self.write_header(out, doc)
        self.write_start_of(out, 'Class Description')

        # Add this class to the index.
        out('    ' + self.indexterm(doc, 'start'))

        # Add a section marker.
        if self._list_classes_separately:
            seclevel = 0
            out(self.section('%s %s' % (self.doc_kind(doc),
                                        doc.canonical_name), seclevel))
        else:
            seclevel = 1
            out(self.section('%s %s' % (self.doc_kind(doc),
                                        doc.canonical_name[-1]), seclevel))

        # Label our current location.
        out('    \\label{%s}\n' % self.label(doc))

        # Add our base list.
        if doc.bases not in (UNKNOWN, None) and len(doc.bases) > 0:
            out(self.base_tree(doc))

        # The class's known subclasses
        if doc.subclasses not in (UNKNOWN, None) and len(doc.subclasses) > 0:
            sc_items = [plaintext_to_latex('%s' % sc.canonical_name)
                        for sc in doc.subclasses]
            out(self._descrlist(sc_items, 'Known Subclasses', short=1))

        # The class's description.
        if doc.descr not in (None, UNKNOWN):
            out(self.docstring_to_latex(doc.descr))

        # Version, author, warnings, requirements, notes, etc.
        self.write_standard_fields(out, doc)

        # Contents.
        self.write_func_list(out, 'Methods', doc, 'method',
                             seclevel+1)
        self.write_var_list(out, 'Properties', doc,
                            'property', seclevel+1)
        self.write_var_list(out, 'Class Variables', doc, 
                            'classvariable', seclevel+1)
        self.write_var_list(out, 'Instance Variables', doc, 
                            'instancevariable', seclevel+1)

        # Mark the end of the class (for the index)
        out('    ' + self.indexterm(doc, 'end'))

    #////////////////////////////////////////////////////////////
    #{ Module hierarchy trees
    #////////////////////////////////////////////////////////////
    
    def write_module_tree(self, out):
        modules = [doc for doc in self.valdocs
                   if isinstance(doc, ModuleDoc)]
        if not modules: return
        
        # Write entries for all top-level modules/packages.
        out('\\begin{itemize}\n')
        out('\\setlength{\\parskip}{0ex}\n')
        for doc in modules:
            if (doc.package in (None, UNKNOWN) or
                doc.package not in self.valdocs):
                self.write_module_tree_item(out, doc)
        return s +'\\end{itemize}\n'

    def write_module_list(self, out, doc):
        if len(doc.submodules) == 0: return
        self.write_start_of(out, 'Modules')
        
        out(self.section('Modules', 1))
        out('\\begin{itemize}\n')
        out('\\setlength{\\parskip}{0ex}\n')

        for group_name in doc.group_names():
            if not doc.submodule_groups[group_name]: continue
            if group_name:
                out('  \\item \\textbf{%s}\n' % group_name)
                out('  \\begin{itemize}\n')
            for submodule in doc.submodule_groups[group_name]:
                self.write_module_tree_item(out, submodule)
            if group_name:
                out('  \end{itemize}\n')

        out('\\end{itemize}\n\n')

    def write_module_tree_item(self, out, doc, depth=0):
        """
        Helper function for L{write_module_tree} and L{write_module_list}.
        
        @rtype: C{string}
        """
        out(' '*depth + '\\item \\textbf{')
        out(plaintext_to_latex(doc.canonical_name[-1]) +'}')
        if doc.summary not in (None, UNKNOWN):
            out(': %s\n' % self.docstring_to_latex(doc.summary))
        if self._crossref:
            out('\n  \\textit{(Section \\ref{%s}' % self.label(doc))
            out(', p.~\\pageref{%s})}\n\n' % self.label(doc))
        if doc.submodules != UNKNOWN and doc.submodules:
            out(' '*depth + '  \\begin{itemize}\n')
            out(' '*depth + '\\setlength{\\parskip}{0ex}\n')
            for submodule in doc.submodules:
                self.write_module_tree_item(out, submodule, depth+4)
            out(' '*depth + '  \\end{itemize}\n')

    #////////////////////////////////////////////////////////////
    #{ Base class trees
    #////////////////////////////////////////////////////////////

    def base_tree(self, doc, width=None, linespec=None):
        if width is None:
            width = self._find_tree_width(doc)+2
            linespec = []
            s = ('&'*(width-4)+'\\multicolumn{2}{l}{\\textbf{%s}}\n' %
                   plaintext_to_latex('%s'%self._base_name(doc)))
            s += '\\end{tabular}\n\n'
            top = 1
        else:
            s = self._base_tree_line(doc, width, linespec)
            top = 0
        
        if isinstance(doc, ClassDoc):
            for i in range(len(doc.bases)-1, -1, -1):
                base = doc.bases[i]
                spec = (i > 0)
                s = self.base_tree(base, width, [spec]+linespec) + s

        if top:
            s = '\\begin{tabular}{%s}\n' % (width*'c') + s

        return s

    def _base_name(self, doc):
        if doc.canonical_name is None:
            if doc.parse_repr is not None:
                return doc.parse_repr
            else:
                return '??'
        else:
            return '%s' % doc.canonical_name

    def _find_tree_width(self, doc):
        if not isinstance(doc, ClassDoc): return 2
        width = 2
        for base in doc.bases:
            width = max(width, self._find_tree_width(base)+2)
        return width

    def _base_tree_line(self, doc, width, linespec):
        base_name = plaintext_to_latex(self._base_name(doc))
        
        # linespec is a list of booleans.
        s = '%% Line for %s, linespec=%s\n' % (base_name, linespec)

        labelwidth = width-2*len(linespec)-2

        # The base class name.
        s += ('\\multicolumn{%s}{r}{' % labelwidth)
        s += '\\settowidth{\\BCL}{%s}' % base_name
        s += '\\multirow{2}{\\BCL}{%s}}\n' % base_name

        # The vertical bars for other base classes (top half)
        for vbar in linespec:
            if vbar: s += '&&\\multicolumn{1}{|c}{}\n'
            else: s += '&&\n'

        # The horizontal line.
        s += '  \\\\\\cline{%s-%s}\n' % (labelwidth+1, labelwidth+1)

        # The vertical bar for this base class.
        s += '  ' + '&'*labelwidth
        s += '\\multicolumn{1}{c|}{}\n'

        # The vertical bars for other base classes (bottom half)
        for vbar in linespec:
            if vbar: s += '&\\multicolumn{1}{|c}{}&\n'
            else: s += '&&\n'
        s += '  \\\\\n'

        return s
        
    #////////////////////////////////////////////////////////////
    #{ Class List
    #////////////////////////////////////////////////////////////
    
    def write_class_list(self, out, doc):
        groups = [(plaintext_to_latex(group_name),
                   doc.select_variables(group=group_name, imported=False,
                                        value_type='class',
                                        public=self._public_filter))
                  for group_name in doc.group_names()]

        # Discard any empty groups; and return if they're all empty.
        groups = [(g,vars) for (g,vars) in groups if vars]
        if not groups: return

        # Write a header.
        self.write_start_of(out, 'Classes')
        out(self.section('Classes', 1))
        out('\\begin{itemize}')
        out('  \\setlength{\\parskip}{0ex}\n')

        for name, var_docs in groups:
            if name:
                out('  \\item \\textbf{%s}\n' % name)
                out('  \\begin{itemize}\n')
            # Add the lines for each class
            for var_doc in var_docs:
                self.write_class_list_line(out, var_doc)
            if name:
                out('  \\end{itemize}\n')

        out('\\end{itemize}\n')

    def write_class_list_line(self, out, var_doc):
        if var_doc.value in (None, UNKNOWN): return # shouldn't happen
        doc = var_doc.value
        out('  ' + '\\item \\textbf{')
        out(plaintext_to_latex(var_doc.name) + '}')
        if doc.summary not in (None, UNKNOWN):
            out(': %s\n' % self.docstring_to_latex(doc.summary))
        if self._crossref:
            out(('\n  \\textit{(Section \\ref{%s}' % self.label(doc)))
            out((', p.~\\pageref{%s})}\n\n' % self.label(doc)))
        
    #////////////////////////////////////////////////////////////
    #{ Function List
    #////////////////////////////////////////////////////////////
    _FUNC_GROUP_HEADER = '\n\\large{\\textbf{\\textit{%s}}}\n\n'
    
    def write_func_list(self, out, heading, doc, value_type, seclevel=1):
        # Divide all public variables of the given type into groups.
        groups = [(plaintext_to_latex(group_name),
                   doc.select_variables(group=group_name, imported=False,
                                        value_type=value_type,
                                        public=self._public_filter))
                  for group_name in doc.group_names()]

        # Discard any empty groups; and return if they're all empty.
        groups = [(g,vars) for (g,vars) in groups if vars]
        if not groups: return

        # Write a header.
        self.write_start_of(out, heading)
        out('  '+self.section(heading, seclevel))

        # Write a section for each group.
        grouped_inh_vars = {}
        for name, var_docs in groups:
            self.write_func_group(out, doc, name, var_docs, grouped_inh_vars)

        # Write a section for each inheritance pseudo-group (used if
        # inheritance=='grouped')
        if grouped_inh_vars:
            for base in doc.mro():
                if base in grouped_inh_vars:
                    hdr = ('Inherited from %s' %
                           plaintext_to_latex('%s' % base.canonical_name))
                    if self._crossref and base in self.class_set:
                        hdr += ('\\textit{(Section \\ref{%s})}' %
                                self.label(base))
                    out(self._FUNC_GROUP_HEADER % (hdr))
                    for var_doc in grouped_inh_vars[base]:
                        self.write_func_list_box(out, var_doc)

    def write_func_group(self, out, doc, name, var_docs, grouped_inh_vars):
        # Split up the var_docs list, according to the way each var
        # should be displayed:
        #   - listed_inh_vars -- for listed inherited variables.
        #   - grouped_inh_vars -- for grouped inherited variables.
        #   - normal_vars -- for all other variables.
        listed_inh_vars = {}
        normal_vars = []
        for var_doc in var_docs:
            if var_doc.container != doc:
                base = var_doc.container
                if (base not in self.class_set or
                    self._inheritance == 'listed'):
                    listed_inh_vars.setdefault(base,[]).append(var_doc)
                elif self._inheritance == 'grouped':
                    grouped_inh_vars.setdefault(base,[]).append(var_doc)
                else:
                    normal_vars.append(var_doc)
            else:
                normal_vars.append(var_doc)
            
        # Write a header for the group.
        if name:
            out(self._FUNC_GROUP_HEADER % name)
        # Write an entry for each normal var:
        for var_doc in normal_vars:
            self.write_func_list_box(out, var_doc)
        # Write a subsection for inherited vars:
        if listed_inh_vars:
            self.write_func_inheritance_list(out, doc, listed_inh_vars)

    def write_func_inheritance_list(self, out, doc, listed_inh_vars):
        for base in doc.mro():
            if base not in listed_inh_vars: continue
            #if str(base.canonical_name) == 'object': continue
            var_docs = listed_inh_vars[base]
            if self._public_filter:
                var_docs = [v for v in var_docs if v.is_public]
            if var_docs:
                hdr = ('Inherited from %s' %
                       plaintext_to_latex('%s' % base.canonical_name))
                if self._crossref and base in self.class_set:
                    hdr += ('\\textit{(Section \\ref{%s})}' %
                            self.label(base))
                out(self._FUNC_GROUP_HEADER % hdr)
                out('\\begin{quote}\n')
                out('%s\n' % ', '.join(
                    ['%s()' % plaintext_to_latex(var_doc.name)
                     for var_doc in var_docs]))
                out('\\end{quote}\n')
            
    def write_func_list_box(self, out, var_doc):
        func_doc = var_doc.value
        is_inherited = (var_doc.overrides not in (None, UNKNOWN))

        # nb: this gives the containing section, not a reference
        # directly to the function.
        if not is_inherited:
            out('    \\label{%s}\n' % self.label(func_doc))
            out('    %s\n' % self.indexterm(func_doc))

        # Start box for this function.
        out('    \\vspace{0.5ex}\n\n')
        out('\\hspace{.8\\funcindent}')
        out('\\begin{boxedminipage}{\\funcwidth}\n\n')

        # Function signature.
        out('    %s\n\n' % self.function_signature(var_doc))

        if (func_doc.docstring not in (None, UNKNOWN) and
            func_doc.docstring.strip() != ''):
            out('    \\vspace{-1.5ex}\n\n')
            out('    \\rule{\\textwidth}{0.5\\fboxrule}\n')
        
        # Description
        out("\\setlength{\\parskip}{2ex}\n")
        if func_doc.descr not in (None, UNKNOWN):
            out(self.docstring_to_latex(func_doc.descr, 4))

        # Parameters
        out("\\setlength{\\parskip}{1ex}\n")
        if func_doc.arg_descrs or func_doc.arg_types:
            # Find the longest name.
            longest = max([0]+[len(n) for n in func_doc.arg_types])
            for names, descrs in func_doc.arg_descrs:
                longest = max([longest]+[len(n) for n in names])
            # Table header.
            out(' '*6+'\\textbf{Parameters}\n')
            out('      \\vspace{-1ex}\n\n')
            out(' '*6+'\\begin{quote}\n')
            out('        \\begin{Ventry}{%s}\n\n' % (longest*'x'))
            # Add params that have @type but not @param info:
            arg_descrs = list(func_doc.arg_descrs)
            args = set()
            for arg_names, arg_descr in arg_descrs:
                args.update(arg_names)
            for arg in var_doc.value.arg_types:
                if arg not in args:
                    arg_descrs.append( ([arg],None) )
            # Display params
            for (arg_names, arg_descr) in arg_descrs:
                arg_name = plaintext_to_latex(', '.join(arg_names))
                out('%s\\item[%s]\n\n' % (' '*10, arg_name))
                if arg_descr:
                    out(self.docstring_to_latex(arg_descr, 10))
                for arg_name in arg_names:
                    arg_typ = func_doc.arg_types.get(arg_name)
                    if arg_typ is not None:
                        if len(arg_names) == 1:
                            lhs = 'type'
                        else:
                            lhs = 'type of %s' % arg_name
                        rhs = self.docstring_to_latex(arg_typ).strip()
                        out('%s{\\it (%s=%s)}\n\n' % (' '*12, lhs, rhs))
            out('        \\end{Ventry}\n\n')
            out(' '*6+'\\end{quote}\n\n')
                
        # Returns
        rdescr = func_doc.return_descr
        rtype = func_doc.return_type
        if rdescr not in (None, UNKNOWN) or rtype not in (None, UNKNOWN):
            out(' '*6+'\\textbf{Return Value}\n')
            out('    \\vspace{-1ex}\n\n')
            out(' '*6+'\\begin{quote}\n')
            if rdescr not in (None, UNKNOWN):
                out(self.docstring_to_latex(rdescr, 6))
                if rtype not in (None, UNKNOWN):
                    out(' '*6+'{\\it (type=%s)}\n\n' %
                        self.docstring_to_latex(rtype, 6).strip())
            elif rtype not in (None, UNKNOWN):
                out(self.docstring_to_latex(rtype, 6))
            out(' '*6+'\\end{quote}\n\n')

        # Raises
        if func_doc.exception_descrs not in (None, UNKNOWN, [], ()):
            out(' '*6+'\\textbf{Raises}\n')
            out('    \\vspace{-1ex}\n\n')
            out(' '*6+'\\begin{quote}\n')
            out('        \\begin{description}\n\n')
            for name, descr in func_doc.exception_descrs:
                out(' '*10+'\\item[\\texttt{%s}]\n\n' %
                    plaintext_to_latex('%s' % name))
                out(self.docstring_to_latex(descr, 10))
            out('        \\end{description}\n\n')
            out(' '*6+'\\end{quote}\n\n')

        ## Overrides
        if var_doc.overrides not in (None, UNKNOWN):
            out('      Overrides: ' +
                plaintext_to_latex('%s'%var_doc.overrides.canonical_name))
            if (func_doc.docstring in (None, UNKNOWN) and
                var_doc.overrides.value.docstring not in (None, UNKNOWN)):
                out(' \textit{(inherited documentation)}')
            out('\n\n')

        # Add version, author, warnings, requirements, notes, etc.
        self.write_standard_fields(out, func_doc)

        out('    \\end{boxedminipage}\n\n')

    def function_signature(self, var_doc):
        func_doc = var_doc.value
        func_name = var_doc.name
        
        # This should never happen, but just in case:
        if func_doc in (None, UNKNOWN):
            return ('\\raggedright \\textbf{%s}(...)' %
                    plaintext_to_latex(func_name))
            
        if func_doc.posargs == UNKNOWN:
            args = ['...']
        else:
            args = [self.func_arg(name, default) for (name, default)
                    in zip(func_doc.posargs, func_doc.posarg_defaults)]
        if func_doc.vararg:
            if func_doc.vararg == '...':
                args.append('\\textit{...}')
            else:
                args.append('*\\textit{%s}' %
                            plaintext_to_latex(func_doc.vararg))
        if func_doc.kwarg:
            args.append('**\\textit{%s}' %
                        plaintext_to_latex(func_doc.kwarg))
        return ('\\raggedright \\textbf{%s}(%s)' %
                (plaintext_to_latex(func_name), ', '.join(args)))

    def func_arg(self, name, default):
        s = '\\textit{%s}' % plaintext_to_latex(self._arg_name(name))
        if default is not None:
            s += '={\\tt %s}' % default.summary_pyval_repr().to_latex(None)
        return s
    
    def _arg_name(self, arg):
        if isinstance(arg, basestring):
            return arg
        elif len(arg) == 1:
            return '(%s,)' % self._arg_name(arg[0])
        else:
            return '(%s)' % (', '.join([self._arg_name(a) for a in arg]))

    #////////////////////////////////////////////////////////////
    #{ Variable List
    #////////////////////////////////////////////////////////////
    _VAR_GROUP_HEADER = '\\multicolumn{2}{|l|}{\\textit{%s}}\\\\\n'

    # Also used for the property list.
    def write_var_list(self, out, heading, doc, value_type, seclevel=1):
        groups = [(plaintext_to_latex(group_name),
                   doc.select_variables(group=group_name, imported=False,
                                        value_type=value_type,
                                        public=self._public_filter))
                  for group_name in doc.group_names()]

        # Discard any empty groups; and return if they're all empty.
        groups = [(g,vars) for (g,vars) in groups if vars]
        if not groups: return

        # Write a header.
        self.write_start_of(out, heading)
        out('  '+self.section(heading, seclevel))

        # [xx] without this, there's a huge gap before the table -- why??
        out('    \\vspace{-1cm}\n')
        
        out('\\hspace{\\varindent}')
        out('\\begin{longtable}')
        out('{|p{\\varnamewidth}|')
        out('p{\\vardescrwidth}|l}\n')
        out('\\cline{1-2}\n')

        # Set up the headers & footer (this makes the table span
        # multiple pages in a happy way).
        out('\\cline{1-2} ')
        out('\\centering \\textbf{Name} & ')
        out('\\centering \\textbf{Description}& \\\\\n')
        out('\\cline{1-2}\n')
        out('\\endhead')
        out('\\cline{1-2}')
        out('\\multicolumn{3}{r}{\\small\\textit{')
        out('continued on next page}}\\\\')
        out('\\endfoot')
        out('\\cline{1-2}\n')
        out('\\endlastfoot')

        # Write a section for each group.
        grouped_inh_vars = {}
        for name, var_docs in groups:
            self.write_var_group(out, doc, name, var_docs, grouped_inh_vars)

        # Write a section for each inheritance pseudo-group (used if
        # inheritance=='grouped')
        if grouped_inh_vars:
            for base in doc.mro():
                if base in grouped_inh_vars:
                    hdr = ('Inherited from %s' %
                           plaintext_to_latex('%s' % base.canonical_name))
                    if self._crossref and base in self.class_set:
                        hdr += (' \\textit{(Section \\ref{%s})}' %
                                self.label(base))
                    out(self._VAR_GROUP_HEADER % (hdr))
                    out('\\cline{1-2}\n')
                    for var_doc in grouped_inh_vars[base]:
                        if isinstance(var_doc.value3, PropertyDoc):
                            self.write_property_list_line(out, var_doc)
                        else:
                            self.write_var_list_line(out, var_doc)
    
        out('\\end{longtable}\n\n')
        
    def write_var_group(self, out, doc, name, var_docs, grouped_inh_vars):
        # Split up the var_docs list, according to the way each var
        # should be displayed:
        #   - listed_inh_vars -- for listed inherited variables.
        #   - grouped_inh_vars -- for grouped inherited variables.
        #   - normal_vars -- for all other variables.
        listed_inh_vars = {}
        normal_vars = []
        for var_doc in var_docs:
            if var_doc.container != doc:
                base = var_doc.container
                if (base not in self.class_set or
                    self._inheritance == 'listed'):
                    listed_inh_vars.setdefault(base,[]).append(var_doc)
                elif self._inheritance == 'grouped':
                    grouped_inh_vars.setdefault(base,[]).append(var_doc)
                else:
                    normal_vars.append(var_doc)
            else:
                normal_vars.append(var_doc)
            
        # Write a header for the group.
        if name:
            out(self._VAR_GROUP_HEADER % name)
            out('\\cline{1-2}\n')
        # Write an entry for each normal var:
        for var_doc in normal_vars:
            if isinstance(var_doc.value, PropertyDoc):
                self.write_property_list_line(out, var_doc)
            else:
                self.write_var_list_line(out, var_doc)
        # Write a subsection for inherited vars:
        if listed_inh_vars:
            self.write_var_inheritance_list(out, doc, listed_inh_vars)

    def write_var_inheritance_list(self, out, doc, listed_inh_vars):
        for base in doc.mro():
            if base not in listed_inh_vars: continue
            #if str(base.canonical_name) == 'object': continue
            var_docs = listed_inh_vars[base]
            if self._public_filter:
                var_docs = [v for v in var_docs if v.is_public]
            if var_docs:
                hdr = ('Inherited from %s' %
                       plaintext_to_latex('%s' % base.canonical_name))
                if self._crossref and base in self.class_set:
                    hdr += (' \\textit{(Section \\ref{%s})}' %
                            self.label(base))
                out(self._VAR_GROUP_HEADER % hdr)
                out('\\multicolumn{2}{|p{\\varwidth}|}{'
                    '\\raggedright %s}\\\\\n' %
                    ', '.join(['%s' % plaintext_to_latex(var_doc.name)
                               for var_doc in var_docs]))
                out('\\cline{1-2}\n')

        
    def write_var_list_line(self, out, var_doc):
        out('\\raggedright ')
        out(plaintext_to_latex(var_doc.name, nbsp=True, breakany=True))
        out(' & ')
        has_descr = var_doc.descr not in (None, UNKNOWN)
        has_type = var_doc.type_descr not in (None, UNKNOWN)
        has_value = var_doc.value is not UNKNOWN
        if has_type or has_value:
            out('\\raggedright ')
        if has_descr:
            out(self.docstring_to_latex(var_doc.descr, 10).strip())
            if has_type or has_value: out('\n\n')
        if has_value:
            out('\\textbf{Value:} \n{\\tt %s}' %
                var_doc.value.summary_pyval_repr().to_latex(None))
        if has_type:
            ptype = self.docstring_to_latex(var_doc.type_descr, 12).strip()
            out('%s{\\it (type=%s)}' % (' '*12, ptype))
        out('&\\\\\n')
        out('\\cline{1-2}\n')

    def write_property_list_line(self, out, var_doc):
        prop_doc = var_doc.value
        out('\\raggedright ')
        out(plaintext_to_latex(var_doc.name, nbsp=True, breakany=True))
        out(' & ')
        has_descr = prop_doc.descr not in (None, UNKNOWN)
        has_type = prop_doc.type_descr not in (None, UNKNOWN)
        if has_descr or has_type:
            out('\\raggedright ')
        if has_descr:
            out(self.docstring_to_latex(prop_doc.descr, 10).strip())
            if has_type: out('\n\n')
        if has_type:
            ptype = self.docstring_to_latex(prop_doc.type_descr, 12).strip()
            out('%s{\\it (type=%s)}' % (' '*12, ptype))
        # [xx] List the fget/fset/fdel functions?
        out('&\\\\\n')
        out('\\cline{1-2}\n')

    #////////////////////////////////////////////////////////////
    #{ Standard Fields
    #////////////////////////////////////////////////////////////

    # Copied from HTMLWriter:
    def write_standard_fields(self, out, doc):
        fields = []
        field_values = {}
        
        #if _sort_fields: fields = STANDARD_FIELD_NAMES [XX]
        
        for (field, arg, descr) in doc.metadata:
            if field not in field_values:
                fields.append(field)
            if field.takes_arg:
                subfields = field_values.setdefault(field,{})
                subfields.setdefault(arg,[]).append(descr)
            else:
                field_values.setdefault(field,[]).append(descr)

        for field in fields:
            if field.takes_arg:
                for arg, descrs in field_values[field].items():
                    self.write_standard_field(out, doc, field, descrs, arg)
                                              
            else:
                self.write_standard_field(out, doc, field, field_values[field])

    def write_standard_field(self, out, doc, field, descrs, arg=''):
        singular = field.singular
        plural = field.plural
        if arg:
            singular += ' (%s)' % arg
            plural += ' (%s)' % arg
        out(self._descrlist([self.docstring_to_latex(d) for d in descrs],
                            field.singular, field.plural, field.short))
            
    def _descrlist(self, items, singular, plural=None, short=0):
        if plural is None: plural = singular
        if len(items) == 0: return ''
        if len(items) == 1 and singular is not None:
            return '\\textbf{%s:} %s\n\n' % (singular, items[0])
        if short:
            s = '\\textbf{%s:}\n' % plural
            items = [item.strip() for item in items]
            return s + ',\n    '.join(items) + '\n\n'
        else:
            s = '\\textbf{%s:}\n' % plural
            s += '\\begin{quote}\n'
            s += '  \\begin{itemize}\n\n  \item\n'
            s += '    \\setlength{\\parskip}{0.6ex}\n'
            s += '\n\n  \item '.join(items)
            return s + '\n\n\\end{itemize}\n\n\\end{quote}\n\n'


    #////////////////////////////////////////////////////////////
    #{ Docstring -> LaTeX Conversion
    #////////////////////////////////////////////////////////////

    # We only need one linker, since we don't use context:
    class _LatexDocstringLinker(markup.DocstringLinker):
        def translate_indexterm(self, indexterm):
            indexstr = re.sub(r'["!|@]', r'"\1', indexterm.to_latex(self))
            return ('\\index{%s}\\textit{%s}' % (indexstr, indexstr))
        def translate_identifier_xref(self, identifier, label=None):
            if label is None: label = markup.plaintext_to_latex(identifier)
            return '\\texttt{%s}' % label
    _docstring_linker = _LatexDocstringLinker()
    
    def docstring_to_latex(self, docstring, indent=0, breakany=0):
        if docstring is None: return ''
        return docstring.to_latex(self._docstring_linker, indent=indent,
                                  hyperref=self._hyperref)
    
    #////////////////////////////////////////////////////////////
    #{ Helpers
    #////////////////////////////////////////////////////////////

    def write_header(self, out, where):
        out('%\n% API Documentation')
        if self._prj_name: out(' for %s' % self._prj_name)
        if isinstance(where, APIDoc):
            out('\n%% %s %s' % (self.doc_kind(where), where.canonical_name))
        else:
            out('\n%% %s' % where)
        out('\n%%\n%% Generated by epydoc %s\n' % epydoc.__version__)
        out('%% [%s]\n%%\n' % time.asctime(time.localtime(time.time())))

    def write_start_of(self, out, section_name):
        out('\n' + 75*'%' + '\n')
        out('%%' + ((71-len(section_name))/2)*' ')
        out(section_name)
        out(((72-len(section_name))/2)*' ' + '%%\n')
        out(75*'%' + '\n\n')

    def section(self, title, depth=0):
        sec = self.SECTIONS[depth+self._top_section]
        return (('%s\n\n' % sec) % plaintext_to_latex(title))                
    
    def sectionstar(self, title, depth):
        sec = self.STARSECTIONS[depth+self._top_section]
        return (('%s\n\n' % sec) % plaintext_to_latex(title))

    def doc_kind(self, doc):
        if isinstance(doc, ModuleDoc) and doc.is_package == True:
            return 'Package'
        elif (isinstance(doc, ModuleDoc) and
              doc.canonical_name[0].startswith('script')):
            return 'Script'
        elif isinstance(doc, ModuleDoc):
            return 'Module'
        elif isinstance(doc, ClassDoc):
            return 'Class'
        elif isinstance(doc, ClassMethodDoc):
            return 'Class Method'
        elif isinstance(doc, StaticMethodDoc):
            return 'Static Method'
        elif isinstance(doc, RoutineDoc):
            if isinstance(self.docindex.container(doc), ClassDoc):
                return 'Method'
            else:
                return 'Function'
        else:
            return 'Variable'

    def indexterm(self, doc, pos='only'):
        """Mark a term or section for inclusion in the index."""
        if not self._index: return ''
        if isinstance(doc, RoutineDoc) and not self._index_functions:
            return ''

        pieces = []
        while doc is not None:
            if doc.canonical_name == UNKNOWN:
                return '' # Give up.
            pieces.append('%s \\textit{(%s)}' %
                          (plaintext_to_latex('%s'%doc.canonical_name),
                           self.doc_kind(doc).lower()))
            doc = self.docindex.container(doc)
            if doc == UNKNOWN:
                return '' # Give up.

        pieces.reverse()
        if pos == 'only':
            return '\\index{%s}\n' % '!'.join(pieces)
        elif pos == 'start':
            return '\\index{%s|(}\n' % '!'.join(pieces)
        elif pos == 'end':
            return '\\index{%s|)}\n' % '!'.join(pieces)
        else:
            raise AssertionError('Bad index position %s' % pos)
        
    def label(self, doc):
        return ':'.join(doc.canonical_name)

    #: Map the Python encoding representation into mismatching LaTeX ones.
    latex_encodings = {
        'utf-8': 'utf8',
    }

    def get_latex_encoding(self):
        """
        @return: The LaTeX representation of the selected encoding.
        @rtype: C{str}
        """
        enc = self._encoding.lower()
        return self.latex_encodings.get(enc, enc)
