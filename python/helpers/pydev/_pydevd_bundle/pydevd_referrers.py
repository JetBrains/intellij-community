from _pydevd_bundle.pydevd_constants import dict_contains
import sys
from _pydevd_bundle import pydevd_vars
from os.path import basename
import traceback
try:
    from urllib import quote, quote_plus, unquote, unquote_plus
except:
    from urllib.parse import quote, quote_plus, unquote, unquote_plus  #@Reimport @UnresolvedImport

#===================================================================================================
# print_var_node
#===================================================================================================
def print_var_node(xml_node, stream):
    name = xml_node.getAttribute('name')
    value = xml_node.getAttribute('value')
    val_type = xml_node.getAttribute('type')

    found_as = xml_node.getAttribute('found_as')
    stream.write('Name: ')
    stream.write(unquote_plus(name))
    stream.write(', Value: ')
    stream.write(unquote_plus(value))
    stream.write(', Type: ')
    stream.write(unquote_plus(val_type))
    if found_as:
        stream.write(', Found as: %s' % (unquote_plus(found_as),))
    stream.write('\n')

#===================================================================================================
# print_referrers
#===================================================================================================
def print_referrers(obj, stream=None):
    if stream is None:
        stream = sys.stdout
    result = get_referrer_info(obj)
    from xml.dom.minidom import parseString
    dom = parseString(result)

    xml = dom.getElementsByTagName('xml')[0]
    for node in xml.childNodes:
        if node.nodeType == node.TEXT_NODE:
            continue

        if node.localName == 'for':
            stream.write('Searching references for: ')
            for child in node.childNodes:
                if child.nodeType == node.TEXT_NODE:
                    continue
                print_var_node(child, stream)

        elif node.localName == 'var':
            stream.write('Referrer found: ')
            print_var_node(node, stream)

        else:
            sys.stderr.write('Unhandled node: %s\n' % (node,))

    return result


#===================================================================================================
# get_referrer_info
#===================================================================================================
def get_referrer_info(searched_obj):
    DEBUG = 0
    if DEBUG:
        sys.stderr.write('Getting referrers info.\n')
    try:
        try:
            if searched_obj is None:
                ret = ['<xml>\n']

                ret.append('<for>\n')
                ret.append(pydevd_vars.var_to_xml(
                    searched_obj,
                    'Skipping getting referrers for None',
                    additionalInXml=' id="%s"' % (id(searched_obj),)))
                ret.append('</for>\n')
                ret.append('</xml>')
                ret = ''.join(ret)
                return ret

            obj_id = id(searched_obj)

            try:
                if DEBUG:
                    sys.stderr.write('Getting referrers...\n')
                import gc
                referrers = gc.get_referrers(searched_obj)
            except:
                traceback.print_exc()
                ret = ['<xml>\n']

                ret.append('<for>\n')
                ret.append(pydevd_vars.var_to_xml(
                    searched_obj,
                    'Exception raised while trying to get_referrers.',
                    additionalInXml=' id="%s"' % (id(searched_obj),)))
                ret.append('</for>\n')
                ret.append('</xml>')
                ret = ''.join(ret)
                return ret

            if DEBUG:
                sys.stderr.write('Found %s referrers.\n' % (len(referrers),))

            curr_frame = sys._getframe()
            frame_type = type(curr_frame)

            #Ignore this frame and any caller frame of this frame

            ignore_frames = {}  #Should be a set, but it's not available on all python versions.
            while curr_frame is not None:
                if basename(curr_frame.f_code.co_filename).startswith('pydev'):
                    ignore_frames[curr_frame] = 1
                curr_frame = curr_frame.f_back


            ret = ['<xml>\n']

            ret.append('<for>\n')
            if DEBUG:
                sys.stderr.write('Searching Referrers of obj with id="%s"\n' % (obj_id,))

            ret.append(pydevd_vars.var_to_xml(
                searched_obj,
                'Referrers of obj with id="%s"' % (obj_id,)))
            ret.append('</for>\n')

            all_objects = None

            for r in referrers:
                try:
                    if dict_contains(ignore_frames, r):
                        continue  #Skip the references we may add ourselves
                except:
                    pass  #Ok: unhashable type checked...

                if r is referrers:
                    continue

                r_type = type(r)
                r_id = str(id(r))

                representation = str(r_type)

                found_as = ''
                if r_type == frame_type:
                    if DEBUG:
                        sys.stderr.write('Found frame referrer: %r\n' % (r,))
                    for key, val in r.f_locals.items():
                        if val is searched_obj:
                            found_as = key
                            break

                elif r_type == dict:
                    if DEBUG:
                        sys.stderr.write('Found dict referrer: %r\n' % (r,))

                    # Try to check if it's a value in the dict (and under which key it was found)
                    for key, val in r.items():
                        if val is searched_obj:
                            found_as = key
                            if DEBUG:
                                sys.stderr.write('    Found as %r in dict\n' % (found_as,))
                            break

                    #Ok, there's one annoying thing: many times we find it in a dict from an instance,
                    #but with this we don't directly have the class, only the dict, so, to workaround that
                    #we iterate over all reachable objects ad check if one of those has the given dict.
                    if all_objects is None:
                        all_objects = gc.get_objects()

                    for x in all_objects:
                        try:
                            if getattr(x, '__dict__', None) is r:
                                r = x
                                r_type = type(x)
                                r_id = str(id(r))
                                representation = str(r_type)
                                break
                        except:
                            pass  #Just ignore any error here (i.e.: ReferenceError, etc.)

                elif r_type in (tuple, list):
                    if DEBUG:
                        sys.stderr.write('Found tuple referrer: %r\n' % (r,))

                    #Don't use enumerate() because not all Python versions have it.
                    i = 0
                    for x in r:
                        if x is searched_obj:
                            found_as = '%s[%s]' % (r_type.__name__, i)
                            if DEBUG:
                                sys.stderr.write('    Found as %s in tuple: \n' % (found_as,))
                            break
                        i += 1

                if found_as:
                    if not isinstance(found_as, str):
                        found_as = str(found_as)
                    found_as = ' found_as="%s"' % (pydevd_vars.make_valid_xml_value(found_as),)

                ret.append(pydevd_vars.var_to_xml(
                    r,
                    representation,
                    additionalInXml=' id="%s"%s' % (r_id, found_as)))
        finally:
            if DEBUG:
                sys.stderr.write('Done searching for references.\n')

            #If we have any exceptions, don't keep dangling references from this frame to any of our objects.
            all_objects = None
            referrers = None
            searched_obj = None
            r = None
            x = None
            key = None
            val = None
            curr_frame = None
            ignore_frames = None
    except:
        traceback.print_exc()
        ret = ['<xml>\n']

        ret.append('<for>\n')
        ret.append(pydevd_vars.var_to_xml(
            searched_obj,
            'Error getting referrers for:',
            additionalInXml=' id="%s"' % (id(searched_obj),)))
        ret.append('</for>\n')
        ret.append('</xml>')
        ret = ''.join(ret)
        return ret

    ret.append('</xml>')
    ret = ''.join(ret)
    return ret

