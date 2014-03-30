import xml.etree.ElementTree as etree
def entries_to_xml(entries, dict_id, dict_name, closed):
    dictionary = etree.Element(u'Dictionary', IDName=dict_id)
    a = etree.SubElement
    a(dictionary, u'Name').text = dict_name
    a(dictionary, u'Closed').text = repr(closed).lower()
    a(dictionary, u'Action').text = u'false'
    terms = a(dictionary, u'Terms')
    for i, entry in enumerate(entries):
        term = a(terms, u'Term')
        a(term, u'Category')
        words = a(term, u'Words')
    return dictionary