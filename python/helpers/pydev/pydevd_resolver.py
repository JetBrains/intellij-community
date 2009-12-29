try:
    import StringIO
except:
    import io as StringIO
import traceback

try:
    __setFalse = False
except:
    import __builtin__
    setattr(__builtin__, 'True', 1)
    setattr(__builtin__, 'False', 0)


MAX_ITEMS_TO_HANDLE = 500
TOO_LARGE_MSG = 'Too large to show contents. Max items to show: ' + str(MAX_ITEMS_TO_HANDLE)
TOO_LARGE_ATTR = 'Unable to handle:'

#=======================================================================================================================
# UnableToResolveVariableException
#=======================================================================================================================
class UnableToResolveVariableException(Exception):
    pass


#=======================================================================================================================
# InspectStub
#=======================================================================================================================
class InspectStub:
    def isbuiltin(self, _args):
        return False
    def isroutine(self, object):
        return False

try:
    import inspect
except:
    inspect = InspectStub()

try:
    import java.lang #@UnresolvedImport
except:
    pass

#types does not include a MethodWrapperType
try:
    MethodWrapperType = type([].__str__)
except:
    MethodWrapperType = None


#=======================================================================================================================
# AbstractResolver
#=======================================================================================================================
class AbstractResolver:
    '''
        This class exists only for documentation purposes to explain how to create a resolver.
        
        Some examples on how to resolve things:
        - list: getDictionary could return a dict with index->item and use the index to resolve it later
        - set: getDictionary could return a dict with id(object)->object and reiterate in that array to resolve it later
        - arbitrary instance: getDictionary could return dict with attr_name->attr and use getattr to resolve it later
    '''

    def resolve(self, var, attribute):
        '''
            In this method, we'll resolve some child item given the string representation of the item in the key
            representing the previously asked dictionary.
            
            @param var: this is the actual variable to be resolved.
            @param attribute: this is the string representation of a key previously returned in getDictionary.
        '''
        raise NotImplementedError

    def getDictionary(self, var):
        '''
            @param var: this is the variable that should have its children gotten.
            
            @return: a dictionary where each pair key, value should be shown to the user as children items
            in the variables view for the given var.
        '''
        raise NotImplementedError


#=======================================================================================================================
# DefaultResolver
#=======================================================================================================================
class DefaultResolver:
    '''
        DefaultResolver is the class that'll actually resolve how to show some variable.
    '''

    def resolve(self, var, attribute):
        return getattr(var, attribute)

    def getDictionary(self, var):
        if MethodWrapperType:
            return self._getPyDictionary(var)
        else:
            return self._getJyDictionary(var)

    def _getJyDictionary(self, obj):
        ret = {}
        found = java.util.HashMap()

        original = obj
        if hasattr(obj, '__class__') and obj.__class__ == java.lang.Class:

            #get info about superclasses
            classes = []
            classes.append(obj)
            c = obj.getSuperclass()
            while c != None:
                classes.append(c)
                c = c.getSuperclass()

            #get info about interfaces
            interfs = []
            for obj in classes:
                interfs.extend(obj.getInterfaces())
            classes.extend(interfs)

            #now is the time when we actually get info on the declared methods and fields
            for obj in classes:

                declaredMethods = obj.getDeclaredMethods()
                declaredFields = obj.getDeclaredFields()
                for i in range(len(declaredMethods)):
                    name = declaredMethods[i].getName()
                    ret[name] = declaredMethods[i].toString()
                    found.put(name, 1)

                for i in range(len(declaredFields)):
                    name = declaredFields[i].getName()
                    found.put(name, 1)
                    #if declaredFields[i].isAccessible():
                    declaredFields[i].setAccessible(True)
                    #ret[name] = declaredFields[i].get( declaredFields[i] )
                    try:
                        ret[name] = declaredFields[i].get(original)
                    except:
                        ret[name] = declaredFields[i].toString()

        #this simple dir does not always get all the info, that's why we have the part before
        #(e.g.: if we do a dir on String, some methods that are from other interfaces such as 
        #charAt don't appear)
        try:
            d = dir(original)
            for name in d:
                if found.get(name) is not 1:
                    ret[name] = getattr(original, name)
        except:
            #sometimes we're unable to do a dir
            pass

        return ret

    def _getPyDictionary(self, var):
        filterPrivate = False
        filterSpecial = True
        filterFunction = True
        filterBuiltIn = True

        names = dir(var)
        d = {}

        #Be aware that the order in which the filters are applied attempts to 
        #optimize the operation by removing as many items as possible in the 
        #first filters, leaving fewer items for later filters

        if filterBuiltIn or filterFunction:
            for n in names:
                if filterSpecial:
                    if n.startswith('__') and n.endswith('__'):
                        continue

                if filterPrivate:
                    if n.startswith('_') or n.endswith('__'):
                        continue

                try:
                    attr = getattr(var, n)

                    #filter builtins?
                    if filterBuiltIn:
                        if inspect.isbuiltin(attr):
                            continue

                    #filter functions?
                    if filterFunction:
                        if inspect.isroutine(attr) or isinstance(attr, MethodWrapperType):
                            continue
                except:
                    #if some error occurs getting it, let's put it to the user.
                    strIO = StringIO.StringIO()
                    traceback.print_exc(file=strIO)
                    attr = strIO.getvalue()

                d[ n ] = attr

        return d


#=======================================================================================================================
# DictResolver
#=======================================================================================================================
class DictResolver:

    def resolve(self, dict, key):
        if key == '__len__':
            return None

        if '(' not in key:
            #we have to treat that because the dict resolver is also used to directly resolve the global and local
            #scopes (which already have the items directly) 
            return dict[key]

        #ok, we have to iterate over the items to find the one that matches the id, because that's the only way
        #to actually find the reference from the string we have before.
        expected_id = int(key.split('(')[-1][:-1])
        for key, val in dict.items():
            if id(key) == expected_id:
                return val

        raise UnableToResolveVariableException()

    def getDictionary(self, dict):
        ret = {}

        for key, val in dict.items():
            #we need to add the id because otherwise we cannot find the real object to get its contents later on.
            key = '%s (%s)' % (key, id(key))
            ret[key] = val

        ret['__len__'] = len(dict)
        return ret



#=======================================================================================================================
# TupleResolver
#=======================================================================================================================
class TupleResolver: #to enumerate tuples and lists

    def resolve(self, var, attribute):
        '''
            @param var: that's the original attribute
            @param attribute: that's the key passed in the dict (as a string)
        '''
        if attribute == '__len__' or attribute == TOO_LARGE_ATTR:
            return None
        return var[int(attribute)]

    def getDictionary(self, var):
        #return dict( [ (i, x) for i, x in enumerate(var) ] )
        # modified 'cause jython does not have enumerate support
        l = len(var)
        d = {}
        
        if l < MAX_ITEMS_TO_HANDLE:
            format = '%0' + str(int(len(str(l)))) + 'd'
            
            
            for i, item in zip(range(l), var):
                d[ format % i ] = item
        else:
            d[TOO_LARGE_ATTR] = TOO_LARGE_MSG
        d['__len__'] = len(var)
        return d



#=======================================================================================================================
# SetResolver
#=======================================================================================================================
class SetResolver:
    '''
        Resolves a set as dict id(object)->object
    '''

    def resolve(self, var, attribute):
        if attribute == '__len__':
            return None

        attribute = int(attribute)
        for v in var:
            if id(v) == attribute:
                return v

        raise UnableToResolveVariableException('Unable to resolve %s in %s' % (attribute, var))

    def getDictionary(self, var):
        d = {}
        for item in var:
            d[ id(item) ] = item
        d['__len__'] = len(var)
        return d


#=======================================================================================================================
# InstanceResolver
#=======================================================================================================================
class InstanceResolver:

    def resolve(self, var, attribute):
        field = var.__class__.getDeclaredField(attribute)
        field.setAccessible(True)
        return field.get(var)

    def getDictionary(self, obj):
        ret = {}

        declaredFields = obj.__class__.getDeclaredFields()
        for i in range(len(declaredFields)):
            name = declaredFields[i].getName()
            try:
                declaredFields[i].setAccessible(True)
                ret[name] = declaredFields[i].get(obj)
            except:
                traceback.print_exc()

        return ret


#=======================================================================================================================
# JyArrayResolver
#=======================================================================================================================
class JyArrayResolver:
    '''
        This resolves a regular Object[] array from java
    '''

    def resolve(self, var, attribute):
        if attribute == '__len__':
            return None
        return var[int(attribute)]

    def getDictionary(self, obj):
        ret = {}

        for i in range(len(obj)):
            ret[ i ] = obj[i]

        ret['__len__'] = len(obj)
        return ret

defaultResolver = DefaultResolver()
dictResolver = DictResolver()
tupleResolver = TupleResolver()
instanceResolver = InstanceResolver()
jyArrayResolver = JyArrayResolver()
setResolver = SetResolver()
