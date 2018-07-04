import keyword
import os

from pycharm_generator_utils.util_methods import *

is_pregenerated = os.getenv("IS_PREGENERATED_SKELETONS", None)


class emptylistdict(dict):
    """defaultdict not available before 2.5; simplest reimplementation using [] as default"""

    def __getitem__(self, item):
        if item in self:
            return dict.__getitem__(self, item)
        else:
            it = []
            self.__setitem__(item, it)
            return it


class Buf(object):
    """Buffers data in a list, can write to a file. Indentation is provided externally."""

    def __init__(self, indenter):
        self.data = []
        self.indenter = indenter

    def put(self, data):
        if data:
            self.data.append(ensureUnicode(data))

    def out(self, indent, *what):
        """Output the arguments, indenting as needed, and adding an eol"""
        self.put(self.indenter.indent(indent))
        for item in what:
            self.put(item)
        self.put("\n")

    def flush_bytes(self, outfile):
        for data in self.data:
            outfile.write(data.encode(OUT_ENCODING, "replace"))

    def flush_str(self, outfile):
        for data in self.data:
            outfile.write(data)

    if version[0] < 3:
        flush = flush_bytes
    else:
        flush = flush_str

    def isEmpty(self):
        return len(self.data) == 0


class ClassBuf(Buf):
    def __init__(self, name, indenter):
        super(ClassBuf, self).__init__(indenter)
        self.name = name


#noinspection PyUnresolvedReferences,PyBroadException
class ModuleRedeclarator(object):
    def __init__(self, module, outfile, mod_filename, indent_size=4, doing_builtins=False):
        """
        Create new instance.
        @param module module to restore.
        @param outfile output file, must be open and writable.
        @param mod_filename filename of binary module (the .dll or .so)
        @param indent_size amount of space characters per indent
        """
        self.module = module
        self.outfile = outfile # where we finally write
        self.mod_filename = mod_filename
        # we write things into buffers out-of-order
        self.header_buf = Buf(self)
        self.imports_buf = Buf(self)
        self.functions_buf = Buf(self)
        self.classes_buf = Buf(self)
        self.classes_buffs = list()
        self.footer_buf = Buf(self)
        self.indent_size = indent_size
        self._indent_step = " " * self.indent_size
        self.split_modules = False
        #
        self.imported_modules = {"": the_builtins} # explicit module imports: {"name": module}
        self.hidden_imports = {} # {'real_mod_name': 'alias'}; we alias names with "__" since we don't want them exported
        # ^ used for things that we don't re-export but need to import, e.g. certain base classes in gnome.
        self._defined = {} # stores True for every name defined so far, to break circular refs in values
        self.doing_builtins = doing_builtins
        self.ret_type_cache = {}
        self.used_imports = emptylistdict() # qual_mod_name -> [imported_names,..]: actually used imported names

    def _initializeQApp4(self):
        try:  # QtGui should be imported _before_ QtCore package.
            # This is done for the QWidget references from QtCore (such as QSignalMapper). Known bug in PyQt 4.7+
            # Causes "TypeError: C++ type 'QWidget*' is not supported as a native Qt signal type"
            import PyQt4.QtGui
        except ImportError:
            pass

        # manually instantiate and keep reference to singleton QCoreApplication (we don't want it to be deleted during the introspection)
        # use QCoreApplication instead of QApplication to avoid blinking app in Dock on Mac OS
        try:
            from PyQt4.QtCore import QCoreApplication
            self.app = QCoreApplication([])
            return
        except ImportError:
            pass

    def _initializeQApp5(self):
        try:
            from PyQt5.QtCore import QCoreApplication
            self.app = QCoreApplication([])
            return
        except ImportError:
            pass

    def indent(self, level):
        """Return indentation whitespace for given level."""
        return self._indent_step * level

    def flush(self):
        init = None
        try:
            if self.split_modules:
                mod_path = module_to_package_name(self.outfile)

                fname = build_output_name(mod_path, "__init__")
                init = fopen(fname, "w")
                for buf in (self.header_buf, self.imports_buf, self.functions_buf, self.classes_buf):
                    buf.flush(init)

                data = ""
                for buf in self.classes_buffs:
                    fname = build_output_name(mod_path, buf.name)
                    dummy = fopen(fname, "w")
                    self.header_buf.flush(dummy)
                    self.imports_buf.flush(dummy)
                    buf.flush(dummy)
                    data += self.create_local_import(buf.name)
                    dummy.close()

                init.write(data)
                self.footer_buf.flush(init)
            else:
                init = fopen(self.outfile, "w")
                for buf in (self.header_buf, self.imports_buf, self.functions_buf, self.classes_buf):
                    buf.flush(init)

                for buf in self.classes_buffs:
                    buf.flush(init)

                self.footer_buf.flush(init)

        finally:
            if init is not None and not init.closed:
                init.close()

    # Some builtin classes effectively change __init__ signature without overriding it.
    # This callable serves as a placeholder to be replaced via REDEFINED_BUILTIN_SIGS
    def fake_builtin_init(self):
        pass # just a callable, sig doesn't matter

    fake_builtin_init.__doc__ = object.__init__.__doc__ # this forces class's doc to be used instead

    def create_local_import(self, name):
        if len(name.split(".")) > 1: return ""
        data = "from "
        if version[0] >= 3:
            data += "."
        data += name + " import " + name + "\n"
        return data

    def find_imported_name(self, item):
        """
        Finds out how the item is represented in imported modules.
        @param item what to check
        @return qualified name (like "sys.stdin") or None
        """
        # TODO: return a pair, not a glued string
        if not isinstance(item, SIMPLEST_TYPES):
            for mname in self.imported_modules:
                m = self.imported_modules[mname]
                for inner_name in m.__dict__:
                    suspect = getattr(m, inner_name)
                    if suspect is item:
                        if mname:
                            mname += "."
                        elif self.module is the_builtins: # don't short-circuit builtins
                            return None
                        return mname + inner_name
        return None

    _initializers = (
        (dict, "{}"),
        (tuple, "()"),
        (list, "[]"),
    )

    def invent_initializer(self, a_type):
        """
        Returns an innocuous initializer expression for a_type, or "None"
        """
        for initializer_type, r in self._initializers:
            if initializer_type == a_type:
                return r
                # NOTE: here we could handle things like defaultdict, sets, etc if we wanted
        return "None"

    def fmt_value(self, out, p_value, indent, prefix="", postfix="", as_name=None, seen_values=None):
        """
        Formats and outputs value (it occupies an entire line or several lines).
        @param out function that does output (a Buf.out)
        @param p_value the value.
        @param indent indent level.
        @param prefix text to print before the value
        @param postfix text to print after the value
        @param as_name hints which name are we trying to print; helps with circular refs.
        @param seen_values a list of keys we've seen if we're processing a dict
        """
        SELF_VALUE = "<value is a self-reference, replaced by this string>"
        ERR_VALUE = "<failed to retrieve the value>"
        if isinstance(p_value, SIMPLEST_TYPES):
            out(indent, prefix, reliable_repr(p_value), postfix)
        else:
            if sys.platform == "cli":
                imported_name = None
            else:
                imported_name = self.find_imported_name(p_value)
            if imported_name:
                out(indent, prefix, imported_name, postfix)
                # TODO: kind of self.used_imports[imported_name].append(p_value) but split imported_name
                # else we could potentially return smth we did not otherwise import. but not likely.
            else:
                if isinstance(p_value, (list, tuple)):
                    if not seen_values:
                        seen_values = [p_value]
                    if len(p_value) == 0:
                        out(indent, prefix, repr(p_value), postfix)
                    else:
                        if isinstance(p_value, list):
                            lpar, rpar = "[", "]"
                        else:
                            lpar, rpar = "(", ")"
                        out(indent, prefix, lpar)
                        for value in p_value:
                            if value in seen_values:
                                value = SELF_VALUE
                            elif not isinstance(value, SIMPLEST_TYPES):
                                seen_values.append(value)
                            self.fmt_value(out, value, indent + 1, postfix=",", seen_values=seen_values)
                        out(indent, rpar, postfix)
                elif isinstance(p_value, dict):
                    if len(p_value) == 0:
                        out(indent, prefix, repr(p_value), postfix)
                    else:
                        if not seen_values:
                            seen_values = [p_value]
                        out(indent, prefix, "{")
                        keys = list(p_value.keys())
                        try:
                            keys.sort()
                        except TypeError:
                            pass # unsortable keys happen, e,g, in py3k _ctypes
                        for k in keys:
                            value = p_value[k]

                            try:
                                is_seen = value in seen_values
                            except:
                                is_seen = False
                                value = ERR_VALUE

                            if is_seen:
                                value = SELF_VALUE
                            elif not isinstance(value, SIMPLEST_TYPES):
                                seen_values.append(value)
                            if isinstance(k, SIMPLEST_TYPES):
                                self.fmt_value(out, value, indent + 1, prefix=repr(k) + ": ", postfix=",",
                                               seen_values=seen_values)
                            else:
                                # both key and value need fancy formatting
                                self.fmt_value(out, k, indent + 1, postfix=": ", seen_values=seen_values)
                                self.fmt_value(out, value, indent + 2, seen_values=seen_values)
                                out(indent + 1, ",")
                        out(indent, "}", postfix)
                else: # something else, maybe representable
                    # look up this value in the module.
                    if sys.platform == "cli":
                        out(indent, prefix, "None", postfix)
                        return
                    found_name = ""
                    for inner_name in self.module.__dict__:
                        if self.module.__dict__[inner_name] is p_value:
                            found_name = inner_name
                            break
                    if self._defined.get(found_name, False):
                        out(indent, prefix, found_name, postfix)
                    elif hasattr(self, "app"):
                        return
                    else:
                        # a forward / circular declaration happens
                        notice = ""
                        try:
                            representation = repr(p_value)
                        except Exception:
                            import traceback
                            traceback.print_exc(file=sys.stderr)
                            return
                        real_value = cleanup(representation)
                        if found_name:
                            if found_name == as_name:
                                notice = " # (!) real value is %r" % real_value
                                real_value = "None"
                            else:
                                notice = " # (!) forward: %s, real value is %r" % (found_name, real_value)
                        if SANE_REPR_RE.match(real_value):
                            out(indent, prefix, real_value, postfix, notice)
                        else:
                            if not found_name:
                                notice = " # (!) real value is %r" % real_value
                            out(indent, prefix, "None", postfix, notice)

    def get_ret_type(self, attr):
        """
        Returns a return type string as given by T_RETURN in tokens, or None
        """
        if attr:
            ret_type = RET_TYPE.get(attr, None)
            if ret_type:
                return ret_type
            thing = getattr(self.module, attr, None)
            if thing:
                if not isinstance(thing, type) and is_callable(thing): # a function
                    return None # TODO: maybe divinate a return type; see pygame.mixer.Channel
                return attr
                # adds no noticeable slowdown, I did measure. dch.
            for im_name, im_module in self.imported_modules.items():
                cache_key = (im_name, attr)
                cached = self.ret_type_cache.get(cache_key, None)
                if cached:
                    return cached
                ret_type = getattr(im_module, attr, None)
                if ret_type:
                    if isinstance(ret_type, type):
                        # detect a constructor
                        constr_args = detect_constructor(ret_type)
                        if constr_args is None:
                            constr_args = "*(), **{}" # a silly catch-all constructor
                        reference = "%s(%s)" % (attr, constr_args)
                    elif is_callable(ret_type): # a function, classes are ruled out above
                        return None
                    else:
                        reference = attr
                    if im_name:
                        result = "%s.%s" % (im_name, reference)
                    else: # built-in
                        result = reference
                    self.ret_type_cache[cache_key] = result
                    return result
                    # TODO: handle things like "[a, b,..] and (foo,..)"
        return None


    SIG_DOC_NOTE = "restored from __doc__"
    SIG_DOC_UNRELIABLY = "NOTE: unreliably restored from __doc__ "

    def restore_by_docstring(self, signature_string, class_name, deco=None, ret_hint=None):
        """
        @param signature_string: parameter list extracted from the doc string.
        @param class_name: name of the containing class, or None
        @param deco: decorator to use
        @param ret_hint: return type hint, if available
        @return (reconstructed_spec, return_type, note) or (None, _, _) if failed.
        """
        action("restoring func %r of class %r", signature_string, class_name)
        # parse
        parsing_failed = False
        ret_type = None
        try:
            # strict parsing
            tokens = paramSeqAndRest.parseString(signature_string, True)
            ret_name = None
            if tokens:
                ret_t = tokens[-1]
                if ret_t[0] is T_RETURN:
                    ret_name = ret_t[1]
            ret_type = self.get_ret_type(ret_name) or self.get_ret_type(ret_hint)
        except ParseException:
            # it did not parse completely; scavenge what we can
            parsing_failed = True
            tokens = []
            try:
                # most unrestrictive parsing
                tokens = paramSeq.parseString(signature_string, False)
            except ParseException:
                pass
                #
        seq = transform_seq(tokens)

        # add safe defaults for unparsed
        if parsing_failed:
            doc_node = self.SIG_DOC_UNRELIABLY
            starred = None
            double_starred = None
            for one in seq:
                if type(one) is str:
                    if one.startswith("**"):
                        double_starred = one
                    elif one.startswith("*"):
                        starred = one
            if not starred:
                seq.append("*args")
            if not double_starred:
                seq.append("**kwargs")
        else:
            doc_node = self.SIG_DOC_NOTE

        # add 'self' if needed YYY
        if class_name and (not seq or seq[0] != 'self'):
            first_param = propose_first_param(deco)
            if first_param:
                seq.insert(0, first_param)
        seq = make_names_unique(seq)
        return (seq, ret_type, doc_node)

    def parse_func_doc(self, func_doc, func_id, func_name, class_name, deco=None, sip_generated=False):
        """
        @param func_doc: __doc__ of the function.
        @param func_id: name to look for as identifier of the function in docstring
        @param func_name: name of the function.
        @param class_name: name of the containing class, or None
        @param deco: decorator to use
        @return (reconstructed_spec, return_literal, note) or (None, _, _) if failed.
        """
        if sip_generated:
            overloads = []
            for part in func_doc.split('\n'):
                signature = func_id + '('
                i = part.find(signature)
                if i >= 0:
                    overloads.append(part[i + len(signature):])
            if len(overloads) > 1:
                docstring_results = [self.restore_by_docstring(overload, class_name, deco) for overload in overloads]
                ret_types = []
                for result in docstring_results:
                    rt = result[1]
                    if rt and rt not in ret_types:
                        ret_types.append(rt)
                if ret_types:
                    ret_literal = " or ".join(ret_types)
                else:
                    ret_literal = None
                param_lists = [result[0] for result in docstring_results]
                spec = build_signature(func_name, restore_parameters_for_overloads(param_lists))
                return (spec, ret_literal, "restored from __doc__ with multiple overloads")

        # find the first thing to look like a definition
        prefix_re = re.compile("\s*(?:(\w+)[ \\t]+)?" + func_id + "\s*\(") # "foo(..." or "int foo(..."
        match = prefix_re.search(func_doc) # Note: this and previous line may consume up to 35% of time
        # parse the part that looks right
        if match:
            ret_hint = match.group(1)
            params, ret_literal, doc_note = self.restore_by_docstring(func_doc[match.end():], class_name, deco, ret_hint)
            spec = func_name + flatten(params)
            return (spec, ret_literal, doc_note)
        else:
            return (None, None, None)


    def is_predefined_builtin(self, module_name, class_name, func_name):
        return self.doing_builtins and module_name == BUILTIN_MOD_NAME and (
            class_name, func_name) in PREDEFINED_BUILTIN_SIGS

    def redo_function(self, out, p_func, p_name, indent, p_class=None, p_modname=None, classname=None, seen=None):
        """
        Restore function argument list as best we can.
        @param out output function of a Buf
        @param p_func function or method object
        @param p_name function name as known to owner
        @param indent indentation level
        @param p_class the class that contains this function as a method
        @param p_modname module name
        @param seen {id(func): name} map of functions already seen in the same namespace;
               id() because *some* functions are unhashable (eg _elementtree.Comment in py2.7)
        """
        action("redoing func %r of class %r", p_name, p_class)
        if seen is not None:
            other_func = seen.get(id(p_func), None)
            if other_func and getattr(other_func, "__doc__", None) is getattr(p_func, "__doc__", None):
                # _bisect.bisect == _bisect.bisect_right in py31, but docs differ
                out(indent, p_name, " = ", seen[id(p_func)])
                out(indent, "")
                return
            else:
                seen[id(p_func)] = p_name
                # real work
        if classname is None:
            classname = p_class and p_class.__name__ or None
        if p_class and hasattr(p_class, '__mro__'):
            sip_generated = [base_t for base_t in p_class.__mro__ if 'sip.simplewrapper' in str(base_t)]
        else:
            sip_generated = False
        deco = None
        deco_comment = ""
        mod_class_method_tuple = (p_modname, classname, p_name)
        ret_literal = None
        is_init = False
        # any decorators?
        action("redoing decos of func %r of class %r", p_name, p_class)
        if self.doing_builtins and p_modname == BUILTIN_MOD_NAME:
            deco = KNOWN_DECORATORS.get((classname, p_name), None)
            if deco:
                deco_comment = " # known case"
        elif p_class and p_name in p_class.__dict__:
            # detect native methods declared with METH_CLASS flag
            descriptor = p_class.__dict__[p_name]
            if p_name != "__new__" and type(descriptor).__name__.startswith('classmethod'):
                # 'classmethod_descriptor' in Python 2.x and 3.x, 'classmethod' in Jython
                deco = "classmethod"
            elif type(p_func).__name__.startswith('staticmethod'):
                deco = "staticmethod"
        if p_name == "__new__":
            deco = "staticmethod"
            deco_comment = " # known case of __new__"

        action("redoing innards of func %r of class %r", p_name, p_class)
        if deco and HAS_DECORATORS:
            out(indent, "@", deco, deco_comment)
        if inspect and inspect.isfunction(p_func):
            out(indent, "def ", p_name, restore_by_inspect(p_func), ": # reliably restored by inspect", )
            out_doc_attr(out, p_func, indent + 1, p_class)
        elif self.is_predefined_builtin(*mod_class_method_tuple):
            spec, sig_note = restore_predefined_builtin(classname, p_name)
            out(indent, "def ", spec, ": # ", sig_note)
            out_doc_attr(out, p_func, indent + 1, p_class)
        elif sys.platform == 'cli' and is_clr_type(p_class):
            is_static, spec, sig_note = restore_clr(p_name, p_class)
            if is_static:
                out(indent, "@staticmethod")
            if not spec: return
            if sig_note:
                out(indent, "def ", spec, ": #", sig_note)
            else:
                out(indent, "def ", spec, ":")
            if not p_name in ['__gt__', '__ge__', '__lt__', '__le__', '__ne__', '__reduce_ex__', '__str__']:
                out_doc_attr(out, p_func, indent + 1, p_class)
        elif mod_class_method_tuple in PREDEFINED_MOD_CLASS_SIGS:
            sig, ret_literal = PREDEFINED_MOD_CLASS_SIGS[mod_class_method_tuple]
            if classname:
                ofwhat = "%s.%s.%s" % mod_class_method_tuple
            else:
                ofwhat = "%s.%s" % (p_modname, p_name)
            out(indent, "def ", p_name, sig, ": # known case of ", ofwhat)
            out_doc_attr(out, p_func, indent + 1, p_class)
        else:
            # __doc__ is our best source of arglist
            sig_note = "real signature unknown"
            spec = ""
            is_init = (p_name == "__init__" and p_class is not None)
            funcdoc = None
            if is_init and hasattr(p_class, "__doc__"):
                if hasattr(p_func, "__doc__"):
                    funcdoc = p_func.__doc__
                if funcdoc == object.__init__.__doc__:
                    funcdoc = p_class.__doc__
            elif hasattr(p_func, "__doc__"):
                funcdoc = p_func.__doc__
            sig_restored = False
            action("parsing doc of func %r of class %r", p_name, p_class)
            if isinstance(funcdoc, STR_TYPES):
                (spec, ret_literal, more_notes) = self.parse_func_doc(funcdoc, p_name, p_name, classname, deco,
                                                                      sip_generated)
                if spec is None and p_name == '__init__' and classname:
                    (spec, ret_literal, more_notes) = self.parse_func_doc(funcdoc, classname, p_name, classname, deco,
                                                                          sip_generated)
                sig_restored = spec is not None
                if more_notes:
                    if sig_note:
                        sig_note += "; "
                    sig_note += more_notes
            if not sig_restored:
                # use an allow-all declaration
                decl = []
                if p_class:
                    first_param = propose_first_param(deco)
                    if first_param:
                        decl.append(first_param)
                decl.append("*args")
                decl.append("**kwargs")
                spec = p_name + "(" + ", ".join(decl) + ")"
            out(indent, "def ", spec, ": # ", sig_note)
            # to reduce size of stubs, don't output same docstring twice for class and its __init__ method
            if not is_init or funcdoc != p_class.__doc__:
                out_docstring(out, funcdoc, indent + 1)
                # body
        if ret_literal and not is_init:
            out(indent + 1, "return ", ret_literal)
        else:
            out(indent + 1, "pass")
        if deco and not HAS_DECORATORS:
            out(indent, p_name, " = ", deco, "(", p_name, ")", deco_comment)
        out(0, "") # empty line after each item

    def redo_class(self, out, p_class, p_name, indent, p_modname=None, seen=None, inspect_dir=False):
        """
        Restores a class definition.
        @param out output function of a relevant buf
        @param p_class the class object
        @param p_name class name as known to owner
        @param indent indentation level
        @param p_modname name of module
        @param seen {class: name} map of classes already seen in the same namespace
        """
        action("redoing class %r of module %r", p_name, p_modname)
        if seen is not None:
            if p_class in seen:
                out(indent, p_name, " = ", seen[p_class])
                out(indent, "")
                return
            else:
                seen[p_class] = p_name
        bases = get_bases(p_class)
        base_def = ""
        skipped_bases = []
        if bases:
            skip_qualifiers = [p_modname, BUILTIN_MOD_NAME, 'exceptions']
            skip_qualifiers.extend(KNOWN_FAKE_REEXPORTERS.get(p_modname, ()))
            bases_list = [] # what we'll render in the class decl
            for base in bases:
                if [1 for (cls, mdl) in KNOWN_FAKE_BASES if cls == base and mdl != self.module]:
                    # our base is a wrapper and our module is not its defining module
                    skipped_bases.append(str(base))
                    continue
                    # somehow import every base class
                base_name = base.__name__
                qual_module_name = qualifier_of(base, skip_qualifiers)
                got_existing_import = False
                if qual_module_name:
                    if qual_module_name in self.used_imports:
                        import_list = self.used_imports[qual_module_name]
                        if base in import_list:
                            bases_list.append(base_name) # unqualified: already set to import
                            got_existing_import = True
                    if not got_existing_import:
                        mangled_qualifier = "__" + qual_module_name.replace('.', '_') # foo.bar -> __foo_bar
                        bases_list.append(mangled_qualifier + "." + base_name)
                        self.hidden_imports[qual_module_name] = mangled_qualifier
                else:
                    bases_list.append(base_name)
            base_def = "(" + ", ".join(bases_list) + ")"

            if self.split_modules:
                for base in bases_list:
                    local_import = self.create_local_import(base)
                    if local_import:
                        out(indent, local_import)
        out(indent, "class ", p_name, base_def, ":",
            skipped_bases and " # skipped bases: " + ", ".join(skipped_bases) or "")
        out_doc_attr(out, p_class, indent + 1)
        # inner parts
        methods = {}
        properties = {}
        others = {}
        we_are_the_base_class = p_modname == BUILTIN_MOD_NAME and p_name == "object"
        field_source = {}
        try:
            if hasattr(p_class, "__dict__") and not inspect_dir:
                field_source = p_class.__dict__
                field_keys = field_source.keys() # Jython 2.5.1 _codecs fail here
            else:
                field_keys = dir(p_class) # this includes unwanted inherited methods, but no dict + inheritance is rare
        except:
            field_keys = ()
        for item_name in field_keys:
            if item_name in ("__doc__", "__module__"):
                if we_are_the_base_class:
                    item = "" # must be declared in base types
                else:
                    continue # in all other cases must be skipped
            elif keyword.iskeyword(item_name):  # for example, PyQt4 contains definitions of methods named 'exec'
                continue
            else:
                try:
                    item = getattr(p_class, item_name) # let getters do the magic
                except AttributeError:
                    item = field_source.get(item_name) # have it raw
                    if item is None:
                        continue
                except Exception:
                    continue
            if is_callable(item) and not isinstance(item, type):
                methods[item_name] = item
            elif is_property(item):
                properties[item_name] = item
            else:
                others[item_name] = item
                #
        if we_are_the_base_class:
            others["__dict__"] = {} # force-feed it, for __dict__ does not contain a reference to itself :)
            # add fake __init__s to have the right sig
        if p_class in FAKE_BUILTIN_INITS:
            methods["__init__"] = self.fake_builtin_init
            note("Faking init of %s", p_name)
        elif '__init__' not in methods:
            init_method = getattr(p_class, '__init__', None)
            if init_method:
                methods['__init__'] = init_method

        #
        seen_funcs = {}
        for item_name in sorted_no_case(methods.keys()):
            item = methods[item_name]
            try:
                self.redo_function(out, item, item_name, indent + 1, p_class, p_modname, classname=p_name, seen=seen_funcs)
            except:
                handle_error_func(item_name, out)
                #
        known_props = KNOWN_PROPS.get(p_modname, {})
        a_setter = "lambda self, v: None"
        a_deleter = "lambda self: None"
        for item_name in sorted_no_case(properties.keys()):
            item = properties[item_name]
            prop_docstring = getattr(item, '__doc__', None)
            prop_key = (p_name, item_name)
            if prop_key in known_props:
                prop_descr = known_props.get(prop_key, None)
                if prop_descr is None:
                    continue # explicitly omitted
                acc_line, getter_and_type = prop_descr
                if getter_and_type:
                    getter, prop_type = getter_and_type
                else:
                    getter, prop_type = None, None
                out(indent + 1, item_name,
                    " = property(", format_accessors(acc_line, getter, a_setter, a_deleter), ")"
                )
                if prop_type:
                    if prop_docstring:
                        out(indent + 1, '"""', prop_docstring)
                        out(0, "")
                        out(indent + 1, ':type: ', prop_type)
                        out(indent + 1, '"""')
                    else:
                        out(indent + 1, '""":type: ', prop_type, '"""')
                    out(0, "")
            else:
                out(indent + 1, item_name, " = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default")
                if prop_docstring:
                    out(indent + 1, '"""', prop_docstring, '"""')
                out(0, "")
        if properties:
            out(0, "") # empty line after the block
            #
        for item_name in sorted_no_case(others.keys()):
            item = others[item_name]
            self.fmt_value(out, item, indent + 1, prefix=item_name + " = ")
        if p_name == "object":
            out(indent + 1, "__module__ = ''")
        if others:
            out(0, "") # empty line after the block
            #
        if not methods and not properties and not others:
            out(indent + 1, "pass")

    def redo_simple_header(self, p_name):
        """Puts boilerplate code on the top"""
        out = self.header_buf.out # 1st class methods rule :)
        out(0, "# encoding: %s" % OUT_ENCODING) # line 1
        # NOTE: maybe encoding should be selectable
        if hasattr(self.module, "__name__"):
            self_name = self.module.__name__
            if self_name != p_name:
                mod_name = " calls itself " + self_name
            else:
                mod_name = ""
        else:
            mod_name = " does not know its name"
        out(0, "# module ", p_name, mod_name) # line 2

        BUILT_IN_HEADER = "(built-in)"
        if is_pregenerated is not None:
            filename = '(pre-generated)'
        elif self.mod_filename:
            filename = self.mod_filename
        elif p_name in sys.builtin_module_names:
            filename = BUILT_IN_HEADER
        else:
            filename = getattr(self.module, "__file__", BUILT_IN_HEADER)

        out(0, "# from %s" % filename)  # line 3
        out(0, "# by generator %s" % VERSION) # line 4
        if p_name == BUILTIN_MOD_NAME and version[0] == 2 and version[1] >= 6:
            out(0, "from __future__ import print_function")
        out_doc_attr(out, self.module, 0)

    def redo_imports(self):
        module_type = type(sys)
        for item_name in self.module.__dict__.keys():
            try:
                item = self.module.__dict__[item_name]
            except:
                continue
            if type(item) is module_type: # not isinstance, py2.7 + PyQt4.QtCore on windows have a bug here
                self.imported_modules[item_name] = item
                self.add_import_header_if_needed()
                ref_notice = getattr(item, "__file__", str(item))
                if hasattr(item, "__name__"):
                    self.imports_buf.out(0, "import ", item.__name__, " as ", item_name, " # ", ref_notice)
                else:
                    self.imports_buf.out(0, item_name, " = None # ??? name unknown; ", ref_notice)

    def add_import_header_if_needed(self):
        if self.imports_buf.isEmpty():
            self.imports_buf.out(0, "")
            self.imports_buf.out(0, "# imports")

    def redo(self, p_name, inspect_dir):
        """
        Restores module declarations.
        Intended for built-in modules and thus does not handle import statements.
        @param p_name name of module
        """
        action("redoing header of module %r %r", p_name, str(self.module))

        if "pyqt4" in p_name.lower():   # qt4 specific patch
            self._initializeQApp4()
        elif "pyqt5" in p_name.lower():   # qt5 specific patch
            self._initializeQApp5()

        self.redo_simple_header(p_name)

        # find whatever other self.imported_modules the module knows; effectively these are imports
        action("redoing imports of module %r %r", p_name, str(self.module))
        try:
            self.redo_imports()
        except:
            pass

        action("redoing innards of module %r %r", p_name, str(self.module))

        module_type = type(sys)
        # group what we have into buckets
        vars_simple = {}
        vars_complex = {}
        funcs = {}
        classes = {}
        module_dict = self.module.__dict__
        if inspect_dir:
            module_dict = dir(self.module)
        for item_name in module_dict:
            note("looking at %s", item_name)
            if item_name in (
                "__dict__", "__doc__", "__module__", "__file__", "__name__", "__builtins__", "__package__"):
                continue # handled otherwise
            try:
                item = getattr(self.module, item_name) # let getters do the magic
            except AttributeError:
                if not item_name in self.module.__dict__: continue
                item = self.module.__dict__[item_name] # have it raw
                # check if it has percolated from an imported module
            except NotImplementedError:
                if not item_name in self.module.__dict__: continue
                item = self.module.__dict__[item_name] # have it raw

            # unless we're adamantly positive that the name was imported, we assume it is defined here
            mod_name = None # module from which p_name might have been imported
            # IronPython has non-trivial reexports in System module, but not in others:
            skip_modname = sys.platform == "cli" and p_name != "System"
            surely_not_imported_mods = KNOWN_FAKE_REEXPORTERS.get(p_name, ())
            ## can't figure weirdness in some modules, assume no reexports:
            #skip_modname =  skip_modname or p_name in self.KNOWN_FAKE_REEXPORTERS
            if not skip_modname:
                try:
                    mod_name = getattr(item, '__module__', None)
                except:
                    pass
                    # we assume that module foo.bar never imports foo; foo may import foo.bar. (see pygame and pygame.rect)
            maybe_import_mod_name = mod_name or ""
            import_is_from_top = len(p_name) > len(maybe_import_mod_name) and p_name.startswith(maybe_import_mod_name)
            note("mod_name = %s, prospective = %s,  from top = %s", mod_name, maybe_import_mod_name, import_is_from_top)
            want_to_import = False
            if (mod_name
                and mod_name != BUILTIN_MOD_NAME
                and mod_name != p_name
                and mod_name not in surely_not_imported_mods
                and not import_is_from_top
            ):
                # import looks valid, but maybe it's a .py file? we're certain not to import from .py
                # e.g. this rules out _collections import collections and builtins import site.
                try:
                    imported = __import__(mod_name) # ok to repeat, Python caches for us
                    if imported:
                        qualifiers = mod_name.split(".")[1:]
                        for qual in qualifiers:
                            imported = getattr(imported, qual, None)
                            if not imported:
                                break
                        imported_path = (getattr(imported, '__file__', False) or "").lower()
                        want_to_import = not (imported_path.endswith('.py') or imported_path.endswith('.pyc'))
                        imported_name = getattr(imported, "__name__", None)
                        if imported_name == p_name:
                            want_to_import = False
                        note("path of %r is %r, want? %s", mod_name, imported_path, want_to_import)
                except ImportError:
                    want_to_import = False
                    # NOTE: if we fail to import, we define 'imported' names here lest we lose them at all
                if want_to_import:
                    import_list = self.used_imports[mod_name]
                    if item_name not in import_list:
                        import_list.append(item_name)
            if not want_to_import:
                if isinstance(item, type) or type(item).__name__ == 'classobj':
                    classes[item_name] = item
                elif is_callable(item): # some classes are callable, check them before functions
                    funcs[item_name] = item
                elif isinstance(item, module_type):
                    continue # self.imported_modules handled above already
                else:
                    if isinstance(item, SIMPLEST_TYPES):
                        vars_simple[item_name] = item
                    else:
                        vars_complex[item_name] = item

        # sort and output every bucket
        action("outputting innards of module %r %r", p_name, str(self.module))
        #
        omitted_names = OMIT_NAME_IN_MODULE.get(p_name, [])
        if vars_simple:
            out = self.functions_buf.out
            prefix = "" # try to group variables by common prefix
            PREFIX_LEN = 2 # default prefix length if we can't guess better
            out(0, "# Variables with simple values")
            for item_name in sorted_no_case(vars_simple.keys()):
                if item_name in omitted_names:
                    out(0, "# definition of " + item_name + " omitted")
                    continue
                item = vars_simple[item_name]
                # track the prefix
                if len(item_name) >= PREFIX_LEN:
                    prefix_pos = string.rfind(item_name, "_") # most prefixes end in an underscore
                    if prefix_pos < 1:
                        prefix_pos = PREFIX_LEN
                    beg = item_name[0:prefix_pos]
                    if prefix != beg:
                        out(0, "") # space out from other prefix
                        prefix = beg
                else:
                    prefix = ""
                    # output
                replacement = REPLACE_MODULE_VALUES.get((p_name, item_name), None)
                if replacement is not None:
                    out(0, item_name, " = ", replacement, " # real value of type ", str(type(item)), " replaced")
                elif is_skipped_in_module(p_name, item_name):
                    t_item = type(item)
                    out(0, item_name, " = ", self.invent_initializer(t_item), " # real value of type ", str(t_item),
                        " skipped")
                else:
                    self.fmt_value(out, item, 0, prefix=item_name + " = ")
                self._defined[item_name] = True
            out(0, "") # empty line after vars
            #
        if funcs:
            out = self.functions_buf.out
            out(0, "# functions")
            out(0, "")
            seen_funcs = {}
            for item_name in sorted_no_case(funcs.keys()):
                if item_name in omitted_names:
                    out(0, "# definition of ", item_name, " omitted")
                    continue
                item = funcs[item_name]
                try:
                    self.redo_function(out, item, item_name, 0, p_modname=p_name, seen=seen_funcs)
                except:
                    handle_error_func(item_name, out)
        else:
            self.functions_buf.out(0, "# no functions")
            #
        if classes:
            self.classes_buf.out(0, "# classes")
            self.classes_buf.out(0, "")
            seen_classes = {}
            # sort classes so that inheritance order is preserved
            cls_list = [] # items are (class_name, mro_tuple)
            for cls_name in sorted_no_case(classes.keys()):
                cls = classes[cls_name]
                ins_index = len(cls_list)
                for i in range(ins_index):
                    maybe_child_bases = cls_list[i][1]
                    if cls in maybe_child_bases:
                        ins_index = i # we could not go farther than current ins_index
                        break         # ...and need not go fartehr than first known child
                cls_list.insert(ins_index, (cls_name, get_mro(cls)))
            self.split_modules = self.mod_filename and len(cls_list) >= 30
            for item_name in [cls_item[0] for cls_item in cls_list]:
                buf = ClassBuf(item_name, self)
                self.classes_buffs.append(buf)
                out = buf.out
                if item_name in omitted_names:
                    out(0, "# definition of ", item_name, " omitted")
                    continue
                item = classes[item_name]
                self.redo_class(out, item, item_name, 0, p_modname=p_name, seen=seen_classes, inspect_dir=inspect_dir)
                self._defined[item_name] = True
                out(0, "") # empty line after each item

            if self.doing_builtins and p_name == BUILTIN_MOD_NAME and version[0] < 3:
                # classobj still supported
                txt = classobj_txt
                self.classes_buf.out(0, txt)

            if self.doing_builtins and p_name == BUILTIN_MOD_NAME:
                txt = create_generator()
                self.classes_buf.out(0, txt)
                txt = create_async_generator()
                self.classes_buf.out(0, txt)
                txt = create_function()
                self.classes_buf.out(0, txt)
                txt = create_method()
                self.classes_buf.out(0, txt)
                txt = create_coroutine()
                self.classes_buf.out(0, txt)

                # Fake <type 'namedtuple'>
                if version[0] >= 3 or (version[0] == 2 and version[1] >= 6):
                    namedtuple_text = create_named_tuple()
                    self.classes_buf.out(0, namedtuple_text)

        else:
            self.classes_buf.out(0, "# no classes")
            #
        if vars_complex:
            out = self.footer_buf.out
            out(0, "# variables with complex values")
            out(0, "")
            for item_name in sorted_no_case(vars_complex.keys()):
                if item_name in omitted_names:
                    out(0, "# definition of " + item_name + " omitted")
                    continue
                item = vars_complex[item_name]
                if str(type(item)) == "<type 'namespace#'>":
                    continue            # this is an IronPython submodule, we mustn't generate a reference for it in the base module
                replacement = REPLACE_MODULE_VALUES.get((p_name, item_name), None)
                if replacement is not None:
                    out(0, item_name + " = " + replacement + " # real value of type " + str(type(item)) + " replaced")
                elif is_skipped_in_module(p_name, item_name):
                    t_item = type(item)
                    out(0, item_name + " = " + self.invent_initializer(t_item) + " # real value of type " + str(
                        t_item) + " skipped")
                else:
                    self.fmt_value(out, item, 0, prefix=item_name + " = ", as_name=item_name)
                self._defined[item_name] = True
                out(0, "") # empty line after each item
        values_to_add = ADD_VALUE_IN_MODULE.get(p_name, None)
        if values_to_add:
            self.footer_buf.out(0, "# intermittent names")
            for value in values_to_add:
                self.footer_buf.out(0, value)
                # imports: last, because previous parts could alter used_imports or hidden_imports
        self.output_import_froms()
        if self.imports_buf.isEmpty():
            self.imports_buf.out(0, "# no imports")
        self.imports_buf.out(0, "") # empty line after imports

    def output_import_froms(self):
        """Mention all imported names known within the module, wrapping as per PEP."""
        out = self.imports_buf.out
        if self.used_imports:
            self.add_import_header_if_needed()
            for mod_name in sorted_no_case(self.used_imports.keys()):
                import_names = self.used_imports[mod_name]
                if import_names:
                    self._defined[mod_name] = True
                    right_pos = 0 # tracks width of list to fold it at right margin
                    import_heading = "from % s import (" % mod_name
                    right_pos += len(import_heading)
                    names_pack = [import_heading]
                    indent_level = 0
                    import_names = list(import_names)
                    import_names.sort()
                    for n in import_names:
                        self._defined[n] = True
                        len_n = len(n)
                        if right_pos + len_n >= 78:
                            out(indent_level, *names_pack)
                            names_pack = [n, ", "]
                            if indent_level == 0:
                                indent_level = 1 # all but first line is indented
                            right_pos = self.indent_size + len_n + 2
                        else:
                            names_pack.append(n)
                            names_pack.append(", ")
                            right_pos += (len_n + 2)
                            # last line is...
                    if indent_level == 0: # one line
                        names_pack[0] = names_pack[0][:-1] # cut off lpar
                        names_pack[-1] = "" # cut last comma
                    else: # last line of multiline
                        names_pack[-1] = ")" # last comma -> rpar
                    out(indent_level, *names_pack)

                    out(0, "") # empty line after group

        if self.hidden_imports:
            self.add_import_header_if_needed()
            for mod_name in sorted_no_case(self.hidden_imports.keys()):
                out(0, 'import ', mod_name, ' as ', self.hidden_imports[mod_name])
            out(0, "") # empty line after group


def module_to_package_name(module_name):
    return re.sub(r"(.*)\.py$", r"\1", module_name)
