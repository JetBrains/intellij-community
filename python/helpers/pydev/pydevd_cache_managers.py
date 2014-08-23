__author__ = 'novokreshchenov.konstantin'

import pydevd_vars
import sys


def get_signature_info(signature):
    return signature.file, signature.name, ' '.join([arg[1]for arg in signature.args])

def get_frame_info(frame):
    co = frame.f_code
    return co.co_name, frame.f_lineno, co.co_filename


class CacheManager(object):
    def __init__(self, log=None):
        self.cache = {}
        self.log = log

    def get_cache_size(self):
        return sys.getsizeof(self.cache)

    def write_cache_size(self):
        cache_size = self.get_cache_size()
        if self.log:
            open(self.log, 'w+').write(str(cache_size))

    def write_cache(self):
        if self.log is not None:
            open(self.log, "w+").write(self.print_cache())


class CallSignatureCacheManager(CacheManager):
    def __init__(self, log=None):
        CacheManager.__init__(self, log)

    def add(self, signature):
        filename, name, args_type = get_signature_info(signature)

        if not filename in self.cache:
            self.cache[filename] = {}

        calls_from_file = self.cache[filename]

        if not name in calls_from_file:
            calls_from_file[name] = {}

        name_calls = calls_from_file[name]
        name_calls[args_type] = None

        self.write_cache()

    def is_repetition(self, signature):
        filename, name, args_type = get_signature_info(signature)
        if filename in self.cache and name in self.cache[filename] and args_type in self.cache[filename][name]:
            return True
        return False

    def is_first_call(self, signature):
        filename, name = get_signature_info(signature)[:-1]
        if filename in self.cache and name in self.cache[filename]:
            return False
        return True

    def print_cache(self):
        output = ""
        for filename, calls_from_file in self.cache.items():
            for name, args_type in calls_from_file.items():
                output += "filename=%s, name=%s, args_type=%s \n" % (filename, name, args_type)

        return output


class ReturnSignatureCacheManager(CacheManager):
    def __init__(self, log=None):
        CacheManager.__init__(self, log)

    def add(self, signature, return_info):
        filename, name = get_signature_info(signature)[:-1]

        if not filename in self.cache:
            self.cache[filename] = {}

        calls_from_file = self.cache[filename]

        if not name in calls_from_file:
            calls_from_file[name] = {}

        returns = calls_from_file[name]
        returns[return_info] = None

    def is_repetition(self, signature, return_info):
        filename, name = get_signature_info(signature)[:-1]
        if filename in self.cache and name in self.cache[filename] and return_info in self.cache[filename][name]:
            return True
        return False

    def print_cache(self):
        output = ""
        for filename, calls_from_file in self.cache.items():
            for name, returns in calls_from_file.items():
                output += 'filename=%s, name=%s, returns=%s \n' % (filename, name, returns)


class HierarchyCallData(object):
    def __init__(self,
                 caller_file, caller_name, caller_def_lineno,
                 callee_file, callee_name, callee_def_lineno, callee_call_lineno):
        self.caller_file, self.caller_name, self.caller_def_lineno = caller_file, caller_name, caller_def_lineno
        self.callee_file, self.callee_name, self.callee_def_lineno = callee_file, callee_name, callee_def_lineno
        self.callee_call_lineno = callee_call_lineno


class CallHierarchyCacheManager(CacheManager): #stores for every function in file its own callers
    def __init__(self, log=None):
        CacheManager.__init__(self, log)

    def print_cache(self):
        output = ""
        for filename, cache_for_file in self.cache.items():
            for callee_name, cache_for_callee in cache_for_file.items():
                output += 'file="%s", func="%s":\n' % (filename, callee_name)
                for caller_filename, cache_for_caller_file in cache_for_callee.items():
                    for caller_name, lines_in_caller in cache_for_caller_file.items():
                        output += '    file="%s", caller="%s", lines="%s"\n' % (caller_filename, caller_name, lines_in_caller)
                output += '\n'
        return output

    def add(self, callee_frame):
        result = False

        caller_frame = callee_frame.f_back
        if (caller_frame is None):
            return None

        callee_code = callee_frame.f_code
        caller_code = caller_frame.f_code

        callee_filename, callee_name, callee_def_lineno = callee_code.co_filename, callee_code.co_name, callee_code.co_firstlineno - 1
        caller_filename, caller_name, caller_def_lineno = caller_code.co_filename, caller_code.co_name, caller_code.co_firstlineno - 1
        callee_call_lineno = caller_frame.f_lineno - 1

        callee_fullname = callee_name + " " + str(callee_def_lineno)
        caller_fullname = caller_name + " " + str(caller_def_lineno)

        if not callee_filename in self.cache:
            self.cache[callee_filename] = {}

        cache_for_file = self.cache[callee_filename]

        if not callee_fullname in cache_for_file:
            cache_for_file[callee_fullname] = {}

        cache_for_callee = cache_for_file[callee_fullname]

        if not caller_filename in cache_for_callee:
            cache_for_callee[caller_filename] = {}

        cache_for_caller_file = cache_for_callee[caller_filename]

        if not caller_fullname in cache_for_caller_file:
            cache_for_caller_file[caller_fullname] = {}

        cache_for_caller = cache_for_caller_file[caller_fullname]

        if not callee_call_lineno in cache_for_caller:
            cache_for_caller[callee_call_lineno] = None
            result = True

        if result:
            return HierarchyCallData(caller_filename, caller_name, str(caller_def_lineno), callee_filename, callee_name,  str(callee_def_lineno), str(callee_call_lineno))
        else:
            return None

    def is_repetition(self, frame):
        caller_frame = frame.f_back

        if caller_frame is None:
            return True

        func_name, func_def_line_no, func_filename = get_frame_info(frame)
        caller_func_name, func_call_line_no, caller_filename = get_frame_info(caller_frame)

        if func_filename in self.cache:
            cache_for_file = self.cache[func_filename]
            if func_name in cache_for_file:
                cache_for_func = cache_for_file[func_name]
                if caller_filename in cache_for_func:
                    cache_for_caller_file = cache_for_func[caller_filename]
                    if caller_func_name in cache_for_caller_file:
                        cache_for_func_caller = cache_for_caller_file[caller_func_name]
                        if func_call_line_no in cache_for_func_caller:
                            return True

        return False