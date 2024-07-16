#  Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import threading

from _pydevd_bundle.pydevd_constants import RETURN_VALUES_DICT, dict_iter_items, \
    GET_FRAME_NORMAL_GROUP, GET_FRAME_SPECIAL_GROUP

HIDDEN_TYPES = ('function', 'type', 'classobj', 'module', 'typing')
DOUBLE_UNDERSCORE = '__'
DOUBLE_EX = '__exception__'
DUMMY_RET_VAL = '_dummy_ret_val'
DUMMY_IPYTHON_HIDDEN = '_dummy_ipython_val'
DUMMY_SPECIAL_VAR = '_dummy_special_var'
DO_NOT_PROCESS_VARS = (DUMMY_SPECIAL_VAR, DUMMY_RET_VAL, DUMMY_IPYTHON_HIDDEN)


class Handler(object):
    def __init__(self, fun):
        self.lst = []
        self.fun = fun

    _next_handler = None

    def set_next(self, handler):
        self._next_handler = handler
        return handler

    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        self.give_to_next(key, value, hidden_ns, evaluate_full_value, user_type_renderers)

    def regular_handle(self, key, value, hidden_ns, evaluate_full_value, is_dict_iter_items=False, user_type_renderers=None):
        if self.is_belong_to_group(key, value, hidden_ns):
            if is_dict_iter_items:
                for name, val in dict_iter_items(value):
                    self.lst.append(self.fun(name, val, hidden_ns, evaluate_full_value, user_type_renderers))
            else:
                self.lst.append(self.fun(key, value, hidden_ns, evaluate_full_value, user_type_renderers))
        else:
            self.give_to_next(key, value, hidden_ns, evaluate_full_value, user_type_renderers)

    def give_to_next(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers):
        if self._next_handler:
            self._next_handler.handle(key, value, hidden_ns, evaluate_full_value, user_type_renderers)

    @staticmethod
    def is_belong_to_group(key, value, hidden_ns):
        return False

    def get_xml(self):
        xml = ''
        handler = self
        while handler is not None:
            for value in handler.lst:
                xml += value
            handler = handler._next_handler
        return xml

    def get_list(self):
        result = []
        handler = self
        while handler is not None:
            result += handler.lst
            handler = handler._next_handler
        return result

    def update_handlers(self):
        handler = self
        while handler is not None:
            handler.lst = []
            if hasattr(handler, 'added_var'):
                handler.added_var = False

            handler = handler._next_handler


class DunderVarsHandler(Handler):
    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        self.regular_handle(key, value, hidden_ns, evaluate_full_value, user_type_renderers)

    @staticmethod
    def is_belong_to_group(key, value, hidden_ns):
        return is_dunder_var(str(key))


class SpecialVarsHandler(Handler):
    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        self.regular_handle(key, value, hidden_ns, evaluate_full_value, user_type_renderers)

    @staticmethod
    def is_belong_to_group(key, value, hidden_ns):
        return is_hidden_var(value)


class IpythonVarsHandler(Handler):
    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        self.regular_handle(key, value, hidden_ns, evaluate_full_value, user_type_renderers)

    @staticmethod
    def is_belong_to_group(key, value, hidden_ns):
        return hidden_ns is not None and key in hidden_ns


class PytestVarsHandler(Handler):
    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        self.regular_handle(key, value, hidden_ns, evaluate_full_value, user_type_renderers)

    @staticmethod
    def is_belong_to_group(key, value, hidden_ns):
        return str(key).startswith('@')


class ReturnVarsHandler(Handler):
    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        self.regular_handle(key, value, hidden_ns, evaluate_full_value, True, user_type_renderers)

    @staticmethod
    def is_belong_to_group(key, value, hidden_ns):
        return key == RETURN_VALUES_DICT


class AnotherVarsHandler(Handler):
    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        if is_special_var(key, value):
            self.lst.append(self.fun(key, value, hidden_ns, evaluate_full_value, user_type_renderers))


class DefaultVarHandler(Handler):
    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        self.lst.append(self.fun(key, value, evaluate_full_value, user_type_renderers))


class DummyVarHandler(Handler):
    def __init__(self, fun, cls):
        super(DummyVarHandler, self).__init__(fun)
        self.cls = cls
        self.added_var = False

    def handle(self, key, value, hidden_ns, evaluate_full_value, user_type_renderers=None):
        if self.cls.is_belong_to_group(key, value, hidden_ns):
            if self.added_var:
                return
            self.lst.append(self.fun())
            self.added_var = True
        else:
            super(DummyVarHandler, self).handle(key, value, hidden_ns, evaluate_full_value, user_type_renderers)


def is_special_var(key, value):
    return is_hidden_var(value) or is_dunder_var(str(key))


def is_dunder_var(key_str):
    return key_str.startswith(DOUBLE_UNDERSCORE) and \
           key_str.endswith(DOUBLE_UNDERSCORE) and \
           key_str != DOUBLE_EX and \
           key_str != '__' and \
           key_str != '___'


def is_hidden_var(value):
    return get_type(value) in HIDDEN_TYPES


def get_type(o):
    try:
        type_object = o.__class__
    except:
        type_object = type(o)

    return type_object.__name__


XML_COMMUNICATION_VARS_HANDLER = 0
THRIFT_COMMUNICATION_VARS_HANDLER = 1


class VarsHandlerContainer:
    instance_xml_handler = None
    instance_thrift_handler = None


_vars_handler_initialization_lock = threading.RLock()


def _init_xml_communication_vars_handler(func, handler_type):
    with _vars_handler_initialization_lock:
        if VarsHandlerContainer.instance_xml_handler is None:
            VarsHandlerContainer.instance_xml_handler = \
                VarsHandler(func, handler_type)


def _init_thrift_communication_var_handler(func, handler_type):
    with _vars_handler_initialization_lock:
        if VarsHandlerContainer.instance_thrift_handler is None:
            VarsHandlerContainer.instance_thrift_handler = \
                VarsHandler(func, handler_type)


def get_vars_handler(func, handler_type, group_type):
    if handler_type == XML_COMMUNICATION_VARS_HANDLER:
        if VarsHandlerContainer.instance_xml_handler is None:
            _init_xml_communication_vars_handler(func, handler_type)
        return VarsHandlerContainer.instance_xml_handler.get_instance(group_type)
    elif handler_type == THRIFT_COMMUNICATION_VARS_HANDLER:
        if VarsHandlerContainer.instance_thrift_handler is None:
            _init_thrift_communication_var_handler(func, handler_type)
        return VarsHandlerContainer.instance_thrift_handler.get_instance(group_type)


class VarsHandler:
    def __init__(self, func, handler_type):
        self.func = func
        self._instance_normal = None
        self._instance_special = None
        self._instance_return = None
        self.handler_type = handler_type
        self._lock = threading.RLock()

    def get_instance(self, group_type):
        with self._lock:
            if group_type == GET_FRAME_NORMAL_GROUP:
                if self._instance_normal is None:
                    self._init_normal()
                instance = self._instance_normal
            elif group_type == GET_FRAME_SPECIAL_GROUP:
                if self._instance_special is None:
                    self._init_special()
                instance = self._instance_special
            else:
                if self._instance_return is None:
                    self._init_return()
                instance = self._instance_return

            instance.update_handlers()

            return instance

    def _init_normal(self):
        self._instance_normal = DummyVarHandler(
            lambda: self.func(DUMMY_RET_VAL, DUMMY_RET_VAL), ReturnVarsHandler
        )

        self._instance_normal.set_next(
            DummyVarHandler(
                lambda: self.func(DUMMY_IPYTHON_HIDDEN, DUMMY_IPYTHON_HIDDEN),
                IpythonVarsHandler
            )
        ).set_next(
            DummyVarHandler(
                lambda: self.func(DUMMY_SPECIAL_VAR, DUMMY_SPECIAL_VAR),
                SpecialVarsHandler
            )
        ).set_next(
            DummyVarHandler(
                lambda: self.func(DUMMY_SPECIAL_VAR, DUMMY_SPECIAL_VAR),
                PytestVarsHandler
            )
        ).set_next(
            DummyVarHandler(
                lambda: self.func(DUMMY_SPECIAL_VAR, DUMMY_SPECIAL_VAR),
                DunderVarsHandler
            )
        ).set_next(
            DefaultVarHandler(
                lambda key, var, eval, type_renderers: self.func(var,
                                                                 str(key),
                                                                 evaluate_full_value=eval,
                                                                 user_type_renderers=type_renderers)
            )
        )

    def _get_lambda_for_special(self, is_special_lambda=False):
        if self.handler_type == XML_COMMUNICATION_VARS_HANDLER:
            if is_special_lambda:
                additional_in_xml = ' isSpecialVal="True"'
            else:
                additional_in_xml = ' isIPythonHidden="True"'

            return lambda key, var, hidden_ns, eval_full, type_renderers: self.func(var,
                                                                                    str(key),
                                                                                    additional_in_xml=additional_in_xml,
                                                                                    evaluate_full_value=eval_full,
                                                                                    user_type_renderers=type_renderers)
        elif self.handler_type == THRIFT_COMMUNICATION_VARS_HANDLER:
            return lambda key, var, hidden_ns, eval_full, type_renderers: self.func(var,
                                                                                    str(key),
                                                                                    evaluate_full_value=eval_full,
                                                                                    user_type_renderers=type_renderers)
        else:
            raise ValueError('Handler type is incorrect')

    def _init_special(self):
        initial_lambda = self._get_lambda_for_special(is_special_lambda=False)
        special_lambda = self._get_lambda_for_special(is_special_lambda=True)

        self._instance_special = DunderVarsHandler(initial_lambda)
        self._instance_special.set_next(
            SpecialVarsHandler(special_lambda)
        ).set_next(
            IpythonVarsHandler(special_lambda)
        ).set_next(
            PytestVarsHandler(special_lambda)
        ).set_next(
            AnotherVarsHandler(special_lambda)
        )

    def _get_lambda_for_return(self):
        if self.handler_type == XML_COMMUNICATION_VARS_HANDLER:
            return lambda name, val, hidden_ns, eval_full, type_renderers: self.func(val,
                                                                                     name,
                                                                                     additional_in_xml=' isRetVal="True"',
                                                                                     user_type_renderers=type_renderers)
        elif self.handler_type == THRIFT_COMMUNICATION_VARS_HANDLER:
            return lambda name, val, hidden_ns, eval_full, type_renderers: self.func(val, name)
        else:
            return None

    def _init_return(self):
        self._instance_return = ReturnVarsHandler(self._get_lambda_for_return())
