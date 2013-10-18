# epydoc -- Plaintext output generation
#
# Copyright (C) 2005 Edward Loper
# Author: Edward Loper <edloper@loper.org>
# URL: <http://epydoc.sf.net>
#
# $Id: plaintext.py 1473 2007-02-13 19:46:05Z edloper $

"""
Plaintext output generation.
"""
__docformat__ = 'epytext en'

from epydoc.apidoc import *
import re

class PlaintextWriter:
    def write(self, api_doc, **options):
        result = []
        out = result.append

        self._cols = options.get('cols', 75)

        try:
            if isinstance(api_doc, ModuleDoc):
                self.write_module(out, api_doc)
            elif isinstance(api_doc, ClassDoc):
                self.write_class(out, api_doc)
            elif isinstance(api_doc, RoutineDoc):
                self.write_function(out, api_doc)
            else:
                assert 0, ('%s not handled yet' % api_doc.__class__)
        except Exception, e:
            print '\n\n'
            print ''.join(result)
            raise

        return ''.join(result)

    def write_module(self, out, mod_doc):
        #for n,v in mod_doc.variables.items():
        #    print n, `v.value`, `v.value.value`
        
        # The cannonical name of the module.
        out(self.section('Module Name'))
        out('    %s\n\n' % mod_doc.canonical_name)

        # The module's description.
        if mod_doc.descr not in (None, '', UNKNOWN):
            out(self.section('Description'))
            out(mod_doc.descr.to_plaintext(None, indent=4))

        #out('metadata: %s\n\n' % mod_doc.metadata) # [xx] testing

        self.write_list(out, 'Classes', mod_doc, value_type='class')
        self.write_list(out, 'Functions', mod_doc, value_type='function')
        self.write_list(out, 'Variables', mod_doc, value_type='other')
        # hmm.. do this as just a flat list??
        #self.write_list(out, 'Imports', mod_doc, imported=True, verbose=False)

    def baselist(self, class_doc):
        if class_doc.bases is UNKNOWN:
            return '(unknown bases)'
        if len(class_doc.bases) == 0: return ''
        s = '('
        class_parent = class_doc.canonical_name.container()
        for i, base in enumerate(class_doc.bases):
            if base.canonical_name is None:
                if base.parse_repr is not UNKNOWN:
                    s += base.parse_repr
                else:
                    s += '??'
            elif base.canonical_name.container() == class_parent:
                s += str(base.canonical_name[-1])
            else:
                s += str(base.canonical_name)
            if i < len(class_doc.bases)-1: out(', ')
        return s+')'

    def write_class(self, out, class_doc, name=None, prefix='', verbose=True):
        baselist = self.baselist(class_doc)
        
        # If we're at the top level, then list the cannonical name of
        # the class; otherwise, our parent will have already printed
        # the name of the variable containing the class.
        if prefix == '':
            out(self.section('Class Name'))
            out('    %s%s\n\n' % (class_doc.canonical_name, baselist))
        else:
            out(prefix + 'class %s' % self.bold(str(name)) + baselist+'\n')

        if not verbose: return

        # Indent the body
        if prefix != '':
            prefix += ' |  '

        # The class's description.
        if class_doc.descr not in (None, '', UNKNOWN):
            if prefix == '':
                out(self.section('Description', prefix))
                out(self._descr(class_doc.descr, '    '))
            else:
                out(self._descr(class_doc.descr, prefix))

        # List of nested classes in this class.
        self.write_list(out, 'Methods', class_doc,
                        value_type='instancemethod', prefix=prefix,
                        noindent=len(prefix)>4)
        self.write_list(out, 'Class Methods', class_doc,
                        value_type='classmethod', prefix=prefix)
        self.write_list(out, 'Static Methods', class_doc,
                        value_type='staticmethod', prefix=prefix)
        self.write_list(out, 'Nested Classes', class_doc,
                        value_type='class', prefix=prefix)
        self.write_list(out, 'Instance Variables', class_doc,
                        value_type='instancevariable', prefix=prefix)
        self.write_list(out, 'Class Variables', class_doc,
                        value_type='classvariable', prefix=prefix)
        
        self.write_list(out, 'Inherited Methods', class_doc,
                        value_type='method', prefix=prefix,
                        inherited=True, verbose=False)
        self.write_list(out, 'Inherited Instance Variables', class_doc,
                        value_type='instancevariable', prefix=prefix,
                        inherited=True, verbose=False)
        self.write_list(out, 'Inherited Class Variables', class_doc,
                        value_type='classvariable', prefix=prefix,
                        inherited=True, verbose=False)
        self.write_list(out, 'Inherited Nested Classes', class_doc,
                        value_type='class', prefix=prefix,
                        inherited=True, verbose=False)

    def write_variable(self, out, var_doc, name=None, prefix='', verbose=True):
        if name is None: name = var_doc.name
        out(prefix+self.bold(str(name)))
        if (var_doc.value not in (UNKNOWN, None) and
            var_doc.is_alias is True and
            var_doc.value.canonical_name not in (None, UNKNOWN)):
            out(' = %s' % var_doc.value.canonical_name)
        elif var_doc.value not in (UNKNOWN, None):
            val_repr = var_doc.value.summary_pyval_repr(
                max_len=self._cols-len(name)-len(prefix)-3)
            out(' = %s' % val_repr.to_plaintext(None))
        out('\n')
        if not verbose: return
        prefix += '    ' # indent the body.
        if var_doc.descr not in (None, '', UNKNOWN):
            out(self._descr(var_doc.descr, prefix))

    def write_property(self, out, prop_doc, name=None, prefix='',
                       verbose=True):
        if name is None: name = prop_doc.canonical_name
        out(prefix+self.bold(str(name)))
        if not verbose: return
        prefix += '    ' # indent the body.
            
        if prop_doc.descr not in (None, '', UNKNOWN):
            out(self._descr(prop_doc.descr, prefix))


    def write_function(self, out, func_doc, name=None, prefix='',
                       verbose=True):
        if name is None: name = func_doc.canonical_name
        self.write_signature(out, func_doc, name, prefix)
        if not verbose: return
        
        prefix += '    ' # indent the body.
            
        if func_doc.descr not in (None, '', UNKNOWN):
            out(self._descr(func_doc.descr, prefix))

        if func_doc.return_descr not in (None, '', UNKNOWN):
            out(self.section('Returns:', prefix))
            out(self._descr(func_doc.return_descr, prefix+'    '))

        if func_doc.return_type not in (None, '', UNKNOWN):
            out(self.section('Return Type:', prefix))
            out(self._descr(func_doc.return_type, prefix+'    '))

    def write_signature(self, out, func_doc, name, prefix):
        args = [self.fmt_arg(argname, default) for (argname, default) 
                in zip(func_doc.posargs, func_doc.posarg_defaults)]
        if func_doc.vararg: args.append('*'+func_doc.vararg)
        if func_doc.kwarg: args.append('**'+func_doc.kwarg)

        out(prefix+self.bold(str(name))+'(')
        x = left = len(prefix) + len(name) + 1
        for i, arg in enumerate(args):
            if x > left and x+len(arg) > 75:
                out('\n'+prefix + ' '*len(name) + ' ')
                x = left
            out(arg)
            x += len(arg)
            if i < len(args)-1:
                out(', ')
                x += 2
        out(')\n')

    # [xx] tuple args!
    def fmt_arg(self, name, default):
        if default is None:
            return '%s' % name
        else:
            default_repr = default.summary_pyval_repr()
            return '%s=%s' % (name, default_repr.to_plaintext(None))

    def write_list(self, out, heading, doc, value_type=None, imported=False,
                   inherited=False, prefix='', noindent=False,
                   verbose=True):
        # Get a list of the VarDocs we should describe.
        if isinstance(doc, ClassDoc):
            var_docs = doc.select_variables(value_type=value_type,
                                            imported=imported,
                                            inherited=inherited)
        else:
            var_docs = doc.select_variables(value_type=value_type,
                                            imported=imported)
        if not var_docs: return

        out(prefix+'\n')
        if not noindent:
            out(self.section(heading, prefix))
            prefix += '    '

        for i, var_doc in enumerate(var_docs):
            val_doc, name = var_doc.value, var_doc.name

            if verbose:
                out(prefix+'\n')

            # hmm:
            if not verbose:
                if isinstance(doc, ClassDoc):
                    name = var_doc.canonical_name
                elif val_doc not in (None, UNKNOWN):
                    name = val_doc.canonical_name
                    
            if isinstance(val_doc, RoutineDoc):
                self.write_function(out, val_doc, name, prefix, verbose)
            elif isinstance(val_doc, PropertyDoc):
                self.write_property(out, val_doc, name, prefix, verbose)
            elif isinstance(val_doc, ClassDoc):
                self.write_class(out, val_doc, name, prefix, verbose)
            else:
                self.write_variable(out, var_doc, name, prefix, verbose)

    def _descr(self, descr, prefix):
        s = descr.to_plaintext(None, indent=len(prefix)).rstrip()
        s = '\n'.join([(prefix+l[len(prefix):]) for l in s.split('\n')])
        return s+'\n'#+prefix+'\n'
                               

#    def drawline(self, s, x):
#        s = re.sub(r'(?m)^(.{%s}) ' % x, r'\1|', s)
#        return re.sub(r'(?m)^( {,%s})$(?=\n)' % x, x*' '+'|', s)

        
    #////////////////////////////////////////////////////////////
    # Helpers
    #////////////////////////////////////////////////////////////
    
    def bold(self, text):
        """Write a string in bold by overstriking."""
        return ''.join([ch+'\b'+ch for ch in text])

    def title(self, text, indent):
        return ' '*indent + self.bold(text.capitalize()) + '\n\n'

    def section(self, text, indent=''):
        if indent == '':
            return indent + self.bold(text.upper()) + '\n'
        else:
            return indent + self.bold(text.capitalize()) + '\n'


