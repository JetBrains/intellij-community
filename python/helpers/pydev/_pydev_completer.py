import pydevconsole

try:
    import __builtin__
except ImportError:
    import builtins as __builtin__

try:
    False
    True
except NameError: # version < 2.3 -- didn't have the True/False builtins
    setattr(__builtin__, 'True', 1)
    setattr(__builtin__, 'False', 0)
    
try:
    import java.lang #@UnusedImport
    import _pydev_jy_imports_tipper #as _pydev_imports_tipper #changed to be backward compatible with 1.5
    _pydev_imports_tipper = _pydev_jy_imports_tipper
except ImportError:
    IS_JYTHON = False
    import _pydev_imports_tipper

import pydevd_vars
dir2 = _pydev_imports_tipper.GenerateImportsTipForModule


#=======================================================================================================================
# _StartsWithFilter
#=======================================================================================================================
class _StartsWithFilter:
    '''
        Used because we can't create a lambda that'll use an outer scope in jython 2.1 
    '''
    
    
    def __init__(self, start_with):
        self.start_with = start_with.lower()
        
    def __call__(self, name):
        return name.lower().startswith(self.start_with)

#=======================================================================================================================
# Completer
#
# This class was gotten from IPython.completer (dir2 was replaced with the completer already in pydev) 
#=======================================================================================================================
class Completer:
    
    def __init__(self, namespace=None, global_namespace=None):
        """Create a new completer for the command line.

        Completer([namespace,global_namespace]) -> completer instance.

        If unspecified, the default namespace where completions are performed
        is __main__ (technically, __main__.__dict__). Namespaces should be
        given as dictionaries.

        An optional second namespace can be given.  This allows the completer
        to handle cases where both the local and global scopes need to be
        distinguished.

        Completer instances should be used as the completion mechanism of
        readline via the set_completer() call:

        readline.set_completer(Completer(my_namespace).complete)
        """

        # Don't bind to namespace quite yet, but flag whether the user wants a
        # specific namespace or to use __main__.__dict__. This will allow us
        # to bind to __main__.__dict__ at completion time, not now.
        if namespace is None:
            self.use_main_ns = 1
        else:
            self.use_main_ns = 0
            self.namespace = namespace

        # The global namespace, if given, can be bound directly
        if global_namespace is None:
            self.global_namespace = {}
        else:
            self.global_namespace = global_namespace

    def complete(self, text):
        """Return the next possible completion for 'text'.

        This is called successively with state == 0, 1, 2, ... until it
        returns None.  The completion should begin with 'text'.

        """
        if self.use_main_ns:
            #In pydev this option should never be used
            raise RuntimeError('Namespace must be provided!')
            self.namespace = __main__.__dict__ #@UndefinedVariable
            
        if "." in text:
            return self.attr_matches(text)
        else:
            return self.global_matches(text)

    def global_matches(self, text):
        """Compute matches when text is a simple name.

        Return a list of all keywords, built-in functions and names currently
        defined in self.namespace or self.global_namespace that match.

        """
        
        
        def get_item(obj, attr):
            return obj[attr]
        
        a = {}
        
        for dict_with_comps in [__builtin__.__dict__, self.namespace, self.global_namespace]: #@UndefinedVariable
            a.update(dict_with_comps)
            
        filter = _StartsWithFilter(text)
            
        return dir2(a, a.keys(), get_item, filter)

    def attr_matches(self, text):
        """Compute matches when text contains a dot.

        Assuming the text is of the form NAME.NAME....[NAME], and is
        evaluatable in self.namespace or self.global_namespace, it will be
        evaluated and its attributes (as revealed by dir()) are used as
        possible completions.  (For class instances, class members are are
        also considered.)

        WARNING: this can still invoke arbitrary C code, if an object
        with a __getattr__ hook is evaluated.

        """
        import re

        # Another option, seems to work great. Catches things like ''.<tab>
        m = re.match(r"(\S+(\.\w+)*)\.(\w*)$", text) #@UndefinedVariable

        if not m:
            return []
        
        expr, attr = m.group(1, 3)
        try:
            obj = eval(expr, self.namespace)
        except:
            try:
                obj = eval(expr, self.global_namespace)
            except:
                return []

        filter = _StartsWithFilter(attr)

        words = dir2(obj, filter=filter)

        return words
        
    
#=======================================================================================================================
# GenerateCompletionsAsXML
#=======================================================================================================================
def GenerateCompletionsAsXML(frame, act_tok):
    if frame is None:
        return '<xml></xml>'

    #Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
    #(Names not resolved in generator expression in method)
    #See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
    updated_globals = {}
    updated_globals.update(frame.f_globals)
    updated_globals.update(frame.f_locals) #locals later because it has precedence over the actual globals

    if pydevconsole.IPYTHON:
        completions = pydevconsole.get_completions(act_tok, act_tok, updated_globals, frame.f_locals)
    else:
        completer = Completer(updated_globals, None)
        #list(tuple(name, descr, parameters, type))
        completions = completer.complete(act_tok)

    valid_xml = pydevd_vars.makeValidXmlValue
    quote = pydevd_vars.quote

    msg = ["<xml>"]

    for comp in completions:
        msg.append('<comp p0="')
        msg.append(valid_xml(quote(comp[0], '/>_= \t')))
        msg.append('" p1="')
        msg.append(valid_xml(quote(comp[1], '/>_= \t')))
        msg.append('" p2="')
        msg.append(valid_xml(quote(comp[2], '/>_= \t')))
        msg.append('" p3="')
        msg.append(valid_xml(quote(comp[3], '/>_= \t')))
        msg.append('"/>')
    msg.append("</xml>")

    return ''.join(msg)

