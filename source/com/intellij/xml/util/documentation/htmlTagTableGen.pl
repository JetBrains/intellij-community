
open(I,"elements.html");

$tagname = "";
$helpref = "";
$description = "";
$startTag="";

$endTag="";
$empty ="";
$dtd="";

open(O,">htmltable.xml");
$basehref="http://www.w3.org/TR/HTML4/";
print O  "<html-property-table version=\"4_01\" generator-version=\"1\" baseHelpRef=\"$basehref\">\n";
$expectedName = 0;

while(<I>) {
  if ($_ =~ /<td title="Name">(.*)<\/td>/i ||
    ( $expectedName == 1 &&
      $_ =~ /(.*)<\/a><\/td>/i  #accept-charset</a></td>
    )
   ) {
    $value = $1;

    if (length ($tagname) > 0) {
      printTagInfo($tagname, $helpref, $description, $startTag, $endTag, $empty,
        $dtd
      );
    }

    $tagname = $value;
    $tagname =~ s/<a href="([^"]*)">(.*?)<\/a>/$2/i;
    if ($expectedName == 0) {
      $helpref = substr($1,3,length($1)-3);
    }

    $tagname = lc($tagname);
    $expectedName = 0;
  } else {
    if ($_ =~ /<td title="Name">(.*)>/i) { #<td title="Name"><a href="../interact/forms.html#adef-accept-charset">
        if (length ($tagname) > 0) {
          printTagInfo($tagname, $helpref, $description, $startTag, $endTag, $empty,
            $dtd
          );
        }

        $tagname="";
        $expectedName=1;
        $helpref = $1;

        $helpref =~ s/<a href="([^"]*)"/$1/i; #<a href="../interact/forms.html#adef-accept-charset"
        $helpref = substr($helpref,3,length($helpref)-3);
      }
      elsif ($_ =~ /<td align="center" title="Description">(.*)<\/td>/i) {
      $description = $1;
    } elsif ($_ =~ /<td align="center" title="Start Tag">(.*)<\/td>/i) {
      $startTag = $1;
      if ($startTag eq "O") { $startTag = "false"; }
    } elsif ($_ =~ /<td align="center" title="End Tag">(.*)<\/td>/i) {
      $endTag = $1;
      if ($endTag eq "O") { $endTag = "false"; }
      elsif ($endTag eq "F") { $endTag = "true"; }
    } elsif ($_ =~ /<td align="center" title="Empty">(.*)<\/td>/i) {
      $empty = $1;
      if ($empty eq "E") { $empty = "true"; }
    } elsif ($_ =~ /<td align="center" title="DTD">(.*)<\/td>/i) {
      $dtd = $1;
    }
  }
}

if (length ($tagname) > 0) {
  printTagInfo($tagname, $helpref, $description, $startTag, $endTag, $empty,
    $dtd
  );
}

close(I);
open(I,"attributes.html");

$attributeName = "";
$processingRelated = 0;
$expectedName = 0;

while(<I>) {
  if (!$processingRelated) {
    if ($_ =~ /<td title="Name">(.*)<\/td>/i || #<td title="Name"><a href="../struct/tables.html#adef-abbr">abbr</a></td>
        ( $expectedName == 1 &&
          $_ =~ /(.*)<\/a><\/td>/i  #accept-charset</a></td>
        )
       ) {
      $value = $1;

      if (length ($attributeName) > 0) {
        printAttributeInfo($attributeName, $helpref, $description, $relatedTags, $dtd, $type, $default);
      }

      $attributeName = $value;
      $attributeName =~ s/<a href="([^"]*)">(.*)<\/a>/$2/i;
      if ($expectedName == 0) {
        $helpref = substr($1,3,length($1)-3);
      }

      $attributeName = lc($attributeName);
      $expectedName = 0;
    } else {
      if ($_ =~ /<td title="Name">(.*)>/i) { #<td title="Name"><a href="../interact/forms.html#adef-accept-charset">
        if (length ($attributeName) > 0) {
          printAttributeInfo($attributeName, $helpref, $description, $relatedTags, $dtd, $type, $default);
        }

        $attributeName="";
        $expectedName=1;
        $helpref = $1;
        
        $helpref =~ s/<a href="([^"]*)"/$1/i; #<a href="../interact/forms.html#adef-accept-charset"
        $helpref = substr($helpref,3,length($helpref)-3);
      } 
      elsif ($_ =~ /<td align="center" title="Comment">(.*)<\/td>/i) {
        $description = $1;
        $description =~ s/"/&quot;/g;
      } elsif ($_ =~ /<td align="center" title="Related Elements">/i) {	
        $relatedTags = "";
        $processingRelated=1;
      } elsif ($_ =~ /<td align="center" title="DTD">(.*)<\/td>/i) {
        $dtd = $1;
      } elsif ($_ =~ /<td align="center" title="Type">(.*)<\/td>/i) {
        $type = $1;
        #<a href="../sgml/dtd.html#URI">%URI;</a> -> %URI;
        $type =~ s/<a href="([^"]*)">(.*)<\/a>/$2/i;
        $type =~ s/%(.*);/$1/i;
      } elsif ($_ =~ /<td align="center" title="Default">(.*)<\/td>/i) {
        $default = $1;
        if ($default eq "#REQUIRED") { $default="false"; }
        else { $default="true"; }
      } elsif ($_ =~ /<td align="center" title="Type"><a href="[^"]*">/i) {
        $expectedType = 1;
      } elsif ( $expectedType == 1 &&
        $_ =~ /(.*)<\/a><\/td>/i
      ) {
        $expectedType = 0;
        $type = $1;
        $type =~ s/%(.*);/$1/i;
      }
    }
  } else {
    $newRelTags = extractRelatedTag($_);
    if(defined($newRelTags)) {
      if(length($relatedTags) != 0) {
        $relatedTags = "$relatedTags,"; 
      }
      $relatedTags = "$relatedTags$newRelTags";
    }
 
    if(/.*<\/td>$/i) {
      $processingRelated=0;
    }
  }
}

if (length ($attributeName) > 0) {
  printAttributeInfo($attributeName, $helpref, $description, $relatedTags, $dtd, $type, $default);
}

sub extractRelatedTag {
  my $a = $_[0];
  my $result;
  my $other;

  if($a =~ /^.*?([ \w]+)<\/a>(.*)/i) {
    $result = lc($1);
    $result = "!" if $result eq "all elements";

    $other = extractRelatedTag($2);

    return $result if (!defined($other));
    return "$result,$other";
  }

  return undef;
}
sub printTagInfo {
  my ($name,$helpref,$description,$startTag,$endTag,$empty,$dtd) = @_;

  $description = &fixNbspEqualToDefault($description,"");
  $startTag = &fixNbspEqualToDefault($startTag,"true");
  $endTag = &fixNbspEqualToDefault($endTag,"true");
  $empty = &fixNbspEqualToDefault($empty,"false");
  $dtd = &fixNbspEqualToDefault($dtd,"");

  print O <<END
<tag name        = "$name"
     helpref     = "$helpref"
     description = "$description"
     startTag    = "$startTag"
     endTag      = "$endTag"
     empty       = "$empty"
     dtd         = "$dtd"
/>
END
}

sub printAttributeInfo {
  my ($name,$helpref,$description,$relatedTags,$dtd,$type, $default) = @_;

  $description = &fixNbspEqualToDefault($description,"");
  $dtd = &fixNbspEqualToDefault($dtd,"");

  print O <<END2
<attribute name        = "$name"
           helpref     = "$helpref"
           description = "$description"
           relatedTags = "$relatedTags"
           dtd         = "$dtd"
           type        = "$type"
           default     = "$default"
/>
END2
}

sub fixNbspEqualToDefault {
  if ( $_[0] =~ /&nbsp;/ ) {
    return $_[1];
  } else {
    return $_[0];
  }
}

print O  "</html-property-table>";

close(I);
close(O);