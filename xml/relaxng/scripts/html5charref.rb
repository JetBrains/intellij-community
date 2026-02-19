#! /usr/bin/env ruby
CHAR_REFS_URL = 'https://dev.w3.org/html5/html-author/charref'
refs = `curl -L #{CHAR_REFS_URL}`

header = <<header
<!--
 * Copyright 2000-#{Time.now.year} JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<!-- It's an automatically generated code. Do not modify it. -->
<!-- Please see #{CHAR_REFS_URL} and #{$0} -->

header

body = ""
refs.scan(/<td> <code>([^<;]*);\<\/code> <td> U\+([0-9A-F]+) <td>/).sort_by { |match| [Integer("0x"+match[1]), /[[:upper:]]/.match(match[0].chr) ? 1 : 0, match[0].length, match[0]] }.each do | match |
  body = body + " <!ENTITY #{match[0]}  \"&##{Integer("0x"+match[1])};\" >\n"
end


footer = <<footer
footer

puts header + body + footer