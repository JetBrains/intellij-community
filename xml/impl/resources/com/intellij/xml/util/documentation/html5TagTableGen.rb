require 'rexml/document'

# read html4 tags and attributes to be able to skip them
file = File.new("htmltable.xml")
doc = REXML::Document.new file
known4Tags = Set.new
known4Attributes = Set.new
doc.elements.each("html-property-table/tag") { |e| known4Tags << e.attributes["name"] }
doc.elements.each("html-property-table/attribute") { |e| known4Attributes << e.attributes["name"] }
file.close

# read html5 tags and attributes for verifying generated data
file = File.new("html5table.xml")
doc = REXML::Document.new file
known5Tags = Set.new
known5Attributes = Set.new
doc.elements.each("html-property-table/tag") { |e| known5Tags << e.attributes["name"] }
doc.elements.each("html-property-table/attribute") { |e| known5Attributes << e.attributes["name"] }
file.close

# read html5 spec
generatedTags = Set.new
result = "<html-property-table baseHelpRef=\"http://www.w3.org/html/wg/drafts/html/master/\">\n"
file = File.new("html5.html")
content = file.read
offset = 0
# parse tags
content.scan(/<tr><th><code(?:[^>]*)><a href="([^"]+)">([^<]+)(?:(?!<tr>).)*<\/th>\s*<td>(?:<a href="(?:[^"]+)"[^>]*>)?([^<]+)(?:(?!<tr>).)*(<\/td>)?/) do |match|
  next if known4Tags.include?($2)
  startTag = true
  endTag = true
  nextTag = content.index("<tr>", ($~.offset(0)[1]))
  empty = content[$~.offset(0)[0]..nextTag].include?("empty")
  dtd = ""
  result +=
       "<tag name        = \"#{$2}\"\n" +
       "     helpref     = \"#{$1}\"\n" +
       "     description = \"#{$3}\"\n" +
       "     startTag    = \"#{startTag}\"\n" +
       "     endTag      = \"#{endTag}\"\n" +
       "     empty       = \"#{empty}\"\n" +
       "     dtd         = \"#{dtd}\"\n" +
       "/>\n"
  generatedTags << $2
  offset = $~.offset(0)[1]
end

generatedAttributes = Set.new
content[offset..-1].scan(/<tr><th(?:[^>]*)>\s?<code(?:[^>]*)>([^<]+)\s*<\/code>(?:\s*<\/td>)?\s*<\/th>\s*<td>([^;\n]*(?:;\s*[^;\n]*)*)(?:\s*<\/td>)\s*<td>\s*(.*)(?:\s*<\/td>)\s*<td>(.*)/) do
  next if known4Attributes.include?($1)
  name = $1
  field_and_link = $2
  description = $3
  type = $4
  type = type.gsub(/<[^>]*>/, "").gsub(/\s+/, " ").gsub(/"/, "").gsub(/^\s+/, "").gsub(/;$/, "")
  description = description.gsub(/<[^>]*>/, "").gsub(/\s+/, " ").gsub(/"/, "").gsub(/^\s+/, "").gsub(/;$/, "")
  helpref_match = field_and_link.match(/<a href="([^"]*)"/)
  helpref = helpref_match ? helpref_match[1] : ""
  relatedTags = field_and_link.gsub(/<[^>]*>/, "").gsub(/\s+/, " ").gsub(/"/, "").gsub(/^\s+/, "").gsub(/;$/, "")
  dtd = ""
  default = true
  result +=
       "<attribute name        = \"#{name}\"\n" +
       "           helpref     = \"#{helpref}\"\n" +
       "           description = \"#{description}\"\n" +
       "           relatedTags = \"#{relatedTags}\"\n" +
       "           dtd         = \"#{dtd}\"\n" +
       "           type        = \"#{type}\"\n" +
       "           default     = \"#{default}\"\n" +
       "/>\n"
  generatedAttributes << name
end
result += '</html-property-table>'
puts result



# verify that we haven't missed tags or attributes
if (!(generatedTags + known4Tags).superset?(known5Tags))
  printf $stderr, "warning! missing tags: #{(known5Tags - known4Tags - generatedTags).to_a.sort}\n"
end

if !(generatedAttributes + known4Attributes).superset?(known5Attributes)
  printf $stderr, "warning! missing attributes: #{(known5Attributes - known4Attributes - generatedAttributes).to_a.sort}\n"
end