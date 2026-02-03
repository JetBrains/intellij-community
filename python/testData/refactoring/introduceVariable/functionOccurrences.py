import xml.etree.ElementTree as etree
def entries_to_xml(entries, dict_id, dict_name, closed):
    dictionary = etree.Element(u'Dictionary', IDName=dict_id)
    <selection>etree.SubElement</selection>(dictionary, u'Name').text = dict_name
    etree.SubElement(dictionary, u'Closed').text = repr(closed).lower()
    etree.SubElement(dictionary, u'Action').text = u'false'
    terms = etree.SubElement(dictionary, u'Terms')
    for i, entry in enumerate(entries):
        term = etree.SubElement(terms, u'Term')
        etree.SubElement(term, u'Category')
        words = etree.SubElement(term, u'Words')
    return dictionary