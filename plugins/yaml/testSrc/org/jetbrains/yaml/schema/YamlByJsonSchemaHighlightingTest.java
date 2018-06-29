// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.yaml.YAMLLanguage;

import java.util.ArrayList;
import java.util.List;

public class YamlByJsonSchemaHighlightingTest extends JsonSchemaHighlightingTestBase {
  @Override
  public String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data";
  }

  @Override
  protected String getTestFileName() {
    return "config.yml";
  }

  @Override
  protected InspectionProfileEntry getInspectionProfile() {
    return new YamlJsonSchemaHighlightingInspection();
  }

  @Override
  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      YAMLLanguage.INSTANCE);
  }

  public void testEnum1() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"enum\": [1,2,3,\"18\"]}}}";
    doTest(schema, "prop: 1");
    doTest(schema, "prop: <warning>foo</warning>");
  }

  public void testMissingProp() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {}, \"flop\": {}}, \"required\": [\"flop\"]}";

    doTest(schema, "<warning>prop: 2</warning>");
    doTest(schema, "prop: 2\nflop: a");
    doTest(schema, "flop: a");
  }

  public void testNumberMultipleWrong() throws Exception {
    doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}",
           "prop: <warning descr=\"Schema validation: Is not multiple of 2\">3</warning>");
  }

  public void testNumberMultipleCorrect() throws Exception {
    doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}", "prop: 4");
  }

  public void testNumberMinMax() throws Exception {
    doTest("{ \"properties\": { \"prop\": {\n" +
           "  \"type\": \"number\",\n" +
           "  \"minimum\": 0,\n" +
           "  \"maximum\": 100,\n" +
           "  \"exclusiveMaximum\": true\n" +
           "}}}", "prop: 14");
  }

  @SuppressWarnings("Duplicates")
  public void testEnum() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"enum\": [1,2,3,\"18\"]}}}";
    doTest(schema, "prop: 18");
    doTest(schema, "prop: 2");
    doTest(schema, "prop: <warning descr=\"Schema validation: Value should be one of: [1, 2, 3, \\\"18\\\"]\">6</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testSimpleString() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"minLength\": 2, \"maxLength\": 3}}}";
    doTest(schema, "prop: <warning descr=\"Schema validation: String is shorter than 2\">s</warning>");
    doTest(schema, "prop: sh");
    doTest(schema, "prop: sho");
    doTest(schema, "prop: <warning descr=\"Schema validation: String is longer than 3\">shor</warning>");
  }

  public void testArray() throws Exception {
    @Language("JSON") final String schema = schema("{\n" +
                                                   "  \"type\": \"array\",\n" +
                                                   "  \"items\": {\n" +
                                                   "    \"type\": \"number\", \"minimum\": 18" +
                                                   "  }\n" +
                                                   "}");
    doTest(schema, "prop:\n - 101\n - 102");
    doTest(schema, "prop:\n - <warning descr=\"Schema validation: Less than a minimum 18.0\">16</warning>");
    doTest(schema, "prop:\n - <warning descr=\"Schema validation: Type is not allowed. Expected: number.\">test</warning>");
  }

  public void testTopLevelArray() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"type\": \"array\",\n" +
                                            "  \"items\": {\n" +
                                            "    \"type\": \"number\", \"minimum\": 18" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "- 101\n- 102");
  }

  public void testTopLevelObjectArray() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"type\": \"array\",\n" +
                                            "  \"items\": {\n" +
                                            "    \"type\": \"object\", \"properties\": {\"a\": {\"type\": \"number\"}}" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "- a: <warning descr=\"Schema validation: Type is not allowed. Expected: number.\">true</warning>");
    doTest(schema, "- a: 18");
  }

  public void testArrayTuples1() throws Exception {
    @Language("JSON") final String schema = schema("{\n" +
                                                   "  \"type\": \"array\",\n" +
                                                   "  \"items\": [{\n" +
                                                   "    \"type\": \"number\", \"minimum\": 18" +
                                                   "  }, {\"type\" : \"string\"}]\n" +
                                                   "}");
    doTest(schema, "prop:\n - 101\n - 102");

    @Language("JSON") final String schema2 = schema("{\n" +
                                                    "  \"type\": \"array\",\n" +
                                                    "  \"items\": [{\n" +
                                                    "    \"type\": \"number\", \"minimum\": 18" +
                                                    "  }, {\"type\" : \"string\"}],\n" +
                                                    "\"additionalItems\": false}");
    doTest(schema2, "prop:\n - 101\n - 102\n - <warning descr=\"Schema validation: Additional items are not allowed\">additional</warning>");
  }

  public void testArrayLength() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    doTest(schema, "prop:\n <warning descr=\"Schema validation: Array is shorter than 2\">- 1</warning>");
    doTest(schema, "prop:\n - 1\n - 2");
    doTest(schema, "prop:\n <warning descr=\"Schema validation: Array is longer than 3\">- 1\n - 2\n - 3\n - 4</warning>");
  }

  public void testArrayUnique() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    doTest(schema, "prop:\n - 1\n - 2");
    doTest(schema, "prop:\n - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n - 2\n - test\n - <warning descr=\"Schema validation: Item is not unique\">1</warning>");
  }

  public void testMetadataIsOk() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"title\" : \"Match anything\",\n" +
                                            "  \"description\" : \"This is a schema that matches anything.\",\n" +
                                            "  \"default\" : \"Default value\"\n" +
                                            "}";
    doTest(schema, "anything: 1");
  }

  public void testRequiredField() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    doTest(schema, "a: 11");
    doTest(schema, "a: 1\nb: true");
    doTest(schema, "<warning descr=\"Schema validation: Missing required property 'a'\">b: alarm</warning>");
  }

  public void testInnerRequired() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    doTest(schema, "prop:\n a: 11");
    doTest(schema, "prop:\n a: 1\n b: true");
    doTest(schema, "prop:\n <warning descr=\"Schema validation: Missing required property 'a'\">b: alarm</warning>");
  }

  public void testAdditionalPropertiesAllowed() throws Exception {
    @Language("JSON") final String schema = schema("{}");
    doTest(schema, "prop:\n q: true\n someStuff: 20");
  }

  public void testAdditionalPropertiesDisabled() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    doTest(schema, "prop:\n q: true\n<warning descr=\"Schema validation: Property 'someStuff' is not allowed\">someStuff: 20</warning>");
  }

  public void testAdditionalPropertiesSchema() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                                            "\"additionalProperties\": {\"type\": \"number\"}}";
    doTest(schema, "a: moo\nb: 5\nc: <warning descr=\"Schema validation: Type is not allowed. Expected: number.\">foo</warning>");
  }

  public void testMinMaxProperties() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"minProperties\": 2, \"maxProperties\": 3}";
    doTest(schema, "<warning descr=\"Schema validation: Number of properties is less than 2\">a: 3</warning>");
    doTest(schema, "a: 1\nb: 5");
    doTest(schema, "<warning descr=\"Schema validation: Number of properties is greater than 3\">a: 1\nb: 22\nc: 333\nd: 4444</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"number\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "prop: 5");
    doTest(schema, "prop: true");
    doTest(schema, "prop: <warning descr=\"Schema validation: Type is not allowed. Expected one of: boolean, number.\">aaa</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOfForTwoMatches() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "prop: b");
    doTest(schema, "prop: c");
    doTest(schema, "prop: <warning descr=\"Schema validation: Validates to more than one variant\">a</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOfSelectError() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\",\n" +
                   "          \"enum\": [\n" +
                   "            \"off\", \"warn\", \"error\"\n" +
                   "          ]}");
    subSchemas.add("{\"type\": \"integer\"}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "prop: off");
    doTest(schema, "prop: 12");
    doTest(schema, "prop: <warning descr=\"Schema validation: Value should be one of: [\\\"off\\\", \\\"warn\\\", \\\"error\\\"]\">wrong</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testAnyOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "prop: b");
    doTest(schema, "prop: c");
    doTest(schema, "prop: a");
    doTest(schema, "prop: <warning descr=\"Schema validation: Value should be one of: [\\\"a\\\", \\\"b\\\"]\">d</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testAllOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"integer\", \"multipleOf\": 2}");
    subSchemas.add("{\"enum\": [1,2,3]}");
    @Language("JSON") final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "prop: <warning descr=\"Schema validation: Is not multiple of 2\">1</warning>");
    doTest(schema, "prop: <warning descr=\"Schema validation: Value should be one of: [1, 2, 3]\">4</warning>");
    doTest(schema, "prop: 2");
  }

  // ----

  public void testObjectInArray() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":{}, \"innerValue\":{}" +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTest(schema, "prop:\n- innerType: aaa\n  <warning descr=\"Schema validation: Property 'alien' is not allowed\">alien: bee</warning>");
  }

  public void testObjectDeeperInArray() throws Exception {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":" + innerTypeSchema +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTest(schema,
           "prop:\n- innerType:\n   only: true\n   <warning descr=\"Schema validation: Property 'hidden' is not allowed\">hidden: false</warning>");
  }

  public void testInnerObjectPropValueInArray() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    doTest(schema, "prop:\n - 1\n - 3");
    doTest(schema, "prop:\n - <warning descr=\"Schema validation: Value should be one of: [1, 2, 3]\">out</warning>");
  }

  public void testAllOfProperties() throws Exception {
    @Language("JSON") final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                                            " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
//    doTest(schema, "first: true\nsecond: <warning descr=\"Schema validation: Value should be one of: [33, 44]\">null</warning>");
    doTest(schema, "first: true\nsecond: 44\n<warning descr=\"Schema validation: Property 'other' is not allowed\">other: 15</warning>");
    doTest(schema, "first: true\nsecond: <warning descr=\"Schema validation: Value should be one of: [33, 44]\">12</warning>");
  }

  public void testWithWaySelection() throws Exception {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    doTest(schema, "prop:\n - <warning descr=\"Schema validation: Property 'foxtrot' is not allowed\">foxtrot: 15</warning>\n   kilo: 20");
  }

  public void testPatternPropertiesHighlighting() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"patternProperties\": {\n" +
                                            "    \"^A\" : {\n" +
                                            "      \"type\": \"number\"\n" +
                                            "    },\n" +
                                            "    \"B\": {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    },\n" +
                                            "    \"C\": {\n" +
                                            "      \"enum\": [\"test\", \"em\"]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "Abezjana: 2\n" +
                   "Auto: <warning descr=\"Schema validation: Type is not allowed. Expected: number.\">no</warning>\n" +
                   "BAe: <warning descr=\"Schema validation: Type is not allowed. Expected: boolean.\">22</warning>\n" +
                   "Boloto: <warning descr=\"Schema validation: Type is not allowed. Expected: boolean.\">2</warning>\n" +
                   "Cyan: <warning descr=\"Schema validation: Value should be one of: [\\\"test\\\", \\\"em\\\"]\">me</warning>\n");
  }

  public void testPatternPropertiesFromIssue() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"type\": \"object\",\n" +
                                            "  \"additionalProperties\": false,\n" +
                                            "  \"patternProperties\": {\n" +
                                            "    \"p[0-9]\": {\n" +
                                            "      \"type\": \"string\"\n" +
                                            "    },\n" +
                                            "    \"a[0-9]\": {\n" +
                                            "      \"enum\": [\"auto!\"]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema,
                   "p1: 1\n" +
                   "p2: 3\n" +
                   "a2: auto!\n" +
                   "a1: <warning descr=\"Schema validation: Value should be one of: [\\\"auto!\\\"]\">moto!</warning>\n"
                   );
  }

  public void testPatternForPropertyValue() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withPattern\": {\n" +
                                            "      \"pattern\": \"p[0-9]\"\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    final String correctText = "withPattern: p1";
    final String wrongText = "withPattern: <warning descr=\"Schema validation: String is violating the pattern: 'p[0-9]'\">wrong</warning>";
    doTest(schema, correctText);
    doTest(schema, wrongText);
  }

  public void testPatternWithSpecialEscapedSymbols() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withPattern\": {\n" +
                                            "      \"pattern\": \"^\\\\d{4}\\\\-(0?[1-9]|1[012])\\\\-(0?[1-9]|[12][0-9]|3[01])$\"\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    @Language("yaml") final String correctText = "withPattern: 1234-11-11";
    final String wrongText = "withPattern: <warning descr=\"Schema validation: String is violating the pattern: '^\\d{4}\\-(0?[1-9]|1[012])\\-(0?[1-9]|[12][0-9]|3[01])$'\">wrong</warning>\n";
    doTest(schema, correctText);
    doTest(schema, wrongText);
  }

  // ---


  public void testRootObjectRedefinedAdditionalPropertiesForbidden() throws Exception {
    doTest(rootObjectRedefinedSchema(), "<warning descr=\"Schema validation: Property 'a' is not allowed\">a: true</warning>\n" +
                                        "r1: allowed!");
  }

  public void testNumberOfSameNamedPropertiesCorrectlyChecked() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"size\": {\n" +
                                            "      \"type\": \"object\",\n" +
                                            "      \"minProperties\": 2,\n" +
                                            "      \"maxProperties\": 3,\n" +
                                            "      \"properties\": {\n" +
                                            "        \"a\": {\n" +
                                            "          \"type\": \"boolean\"\n" +
                                            "        }\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema,
                   "size: \n" +
                   " <warning descr=\"Schema validation: Number of properties is greater than 3\">a: <warning descr=\"Schema validation: Type is not allowed. Expected: boolean.\">1</warning>\n" +
                   " b: 3\n" +
                   " c: 4\n" +
                   " a: <warning descr=\"Schema validation: Type is not allowed. Expected: boolean.\">5</warning>" +
                   "</warning>\n");
  }

  public void testManyDuplicatesInArray() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"array\":{\n" +
                                            "      \"type\": \"array\",\n" +
                                            "      \"uniqueItems\": true\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "array: \n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">2</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">2</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">2</warning>\n" +
                   " - 5\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">3</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">3</warning>\n");
  }

  // ----

  public void testPropertyValueAlsoHighlightedIfPatternIsInvalid() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withPattern\": {\n" +
                                            "      \"pattern\": \"^[]$\"\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    final String text = "withPattern:" +
                        " <warning descr=\"Schema validation: Can not check string by pattern because of error: Unclosed character class near index 3\n^[]$\n   ^\">(124)555-4216</warning>";
    doTest(schema, text);
  }

  public void testNotSchema() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\n" +
                                            "    \"not_type\": { \"not\": { \"type\": \"string\" } }\n" +
                                            "  }}";
    doTest(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">wrong</warning>");
  }

  public void testNotSchemaCombinedWithNormal() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\n" +
                                            "    \"not_type\": {\n" +
                                            "      \"pattern\": \"^[a-z]*[0-5]*$\",\n" +
                                            "      \"not\": { \"pattern\": \"^[a-z]{1}[0-5]$\" }\n" +
                                            "    }\n" +
                                            "  }}";
    doTest(schema, "not_type: va4");
    doTest(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">a4</warning>");
    doTest(schema, "not_type: <warning descr=\"Schema validation: String is violating the pattern: '^[a-z]*[0-5]*$'\">4a4</warning>");
  }

  public void testDoNotMarkOneOfThatDiffersWithFormat() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withFormat\": {\n" +
                                            "      \"type\": \"string\"," +
                                            "      \"oneOf\": [\n" +
                                            "        {\n" +
                                            "          \"format\":\"hostname\"\n" +
                                            "        },\n" +
                                            "        {\n" +
                                            "          \"format\": \"ip4\"\n" +
                                            "        }\n" +
                                            "      ]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "withFormat: localhost");
  }

  public void testAcceptSchemaWithoutType() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withFormat\": {\n" +
                                            "      \"oneOf\": [\n" +
                                            "        {\n" +
                                            "          \"format\":\"hostname\"\n" +
                                            "        },\n" +
                                            "        {\n" +
                                            "          \"format\": \"ip4\"\n" +
                                            "        }\n" +
                                            "      ]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "withFormat: localhost");
  }

  public void testArrayItemReference() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"items\": [\n" +
                                            "    {\n" +
                                            "      \"type\": \"integer\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"$ref\": \"#/items/0\"\n" +
                                            "    }\n" +
                                            "  ]\n" +
                                            "}";
    doTest(schema, "- 1\n- 2");
    doTest(schema, "- 1\n- <warning>foo</warning>");
  }

  public void testValidateAdditionalItems() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"definitions\": {\n" +
                                            "    \"options\": {\n" +
                                            "      \"type\": \"array\",\n" +
                                            "      \"items\": {\n" +
                                            "        \"type\": \"number\"\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  },\n" +
                                            "  \"items\": [\n" +
                                            "    {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"additionalItems\": {\n" +
                                            "    \"$ref\": \"#/definitions/options/items\"\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "- true\n- true");
    doTest(schema, "- true\n- true\n- 1\n- 2\n- 3");
    doTest(schema, "- true\n- true\n- 1\n- <warning>qq</warning>");
  }



  public void testExclusiveMinMaxV6() throws Exception {
    @Language("JSON") String exclusiveMinSchema = "{\"properties\": {\"prop\": {\"exclusiveMinimum\": 3}}}";
    doTest(exclusiveMinSchema, "prop: <warning>2</warning>");
    doTest(exclusiveMinSchema, "prop: <warning>3</warning>");
    doTest(exclusiveMinSchema, "prop: 4");

    @Language("JSON") String exclusiveMaxSchema = "{\"properties\": {\"prop\": {\"exclusiveMaximum\": 3}}}";
    doTest(exclusiveMaxSchema, "prop: 2");
    doTest(exclusiveMaxSchema, "prop: <warning>3</warning>");
    doTest(exclusiveMaxSchema, "prop: <warning>4</warning>");
  }

  /*todo later
  public void testPropertyNamesV6() throws Exception {
    doTest("{\"propertyNames\": {\"minLength\": 7}}", "{<warning>\"prop\"</warning>: 2}");
    doTest("{\"properties\": {\"prop\": {\"propertyNames\": {\"minLength\": 7}}}}", "{\"prop\": {<warning>\"qq\"</warning>: 7}}");
  }*/

  public void testContainsV6() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"contains\": {\"type\": \"number\"}}}}";
    doTest(schema, "prop:\n <warning>- a\n - true</warning>");
    doTest(schema, "prop:\n - a\n - true\n - 1");
  }

  public void testConstV6() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"const\": \"foo\"}}}";
    doTest(schema, "prop: <warning>a</warning>");
    doTest(schema, "prop: <warning>5</warning>");
    doTest(schema, "prop: foo");
  }

  public void testIfThenElseV7() throws Exception {
    @Language("JSON") String schema = "{\n" +
                                      "  \"if\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"a\": {\n" +
                                      "        \"type\": \"string\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"a\"]\n" +
                                      "  },\n" +
                                      "  \"then\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"b\": {\n" +
                                      "        \"type\": \"number\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"b\"]\n" +
                                      "  },\n" +
                                      "  \"else\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"c\": {\n" +
                                      "        \"type\": \"boolean\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"c\"]\n" +
                                      "  }\n" +
                                      "}";
    doTest(schema, "c: <warning>5</warning>");
    doTest(schema, "c: true");
    doTest(schema, "<warning>a: a\nc: true</warning>");
    doTest(schema, "a: a\nb: <warning>true</warning>");
    doTest(schema, "a: a\nb: 5");
  }

  public void testNestedOneOf() throws Exception {
    @Language("JSON") String schema = "{\"type\":\"object\",\n" +
                                      "  \"oneOf\": [\n" +
                                      "    {\n" +
                                      "      \"properties\": {\n" +
                                      "        \"type\": {\n" +
                                      "          \"type\": \"string\",\n" +
                                      "          \"oneOf\": [\n" +
                                      "            {\n" +
                                      "              \"pattern\": \"(good)\"\n" +
                                      "            },\n" +
                                      "            {\n" +
                                      "              \"pattern\": \"(ok)\"\n" +
                                      "            }\n" +
                                      "          ]\n" +
                                      "        }\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    {\n" +
                                      "      \"properties\": {\n" +
                                      "        \"type\": {\n" +
                                      "          \"type\": \"string\",\n" +
                                      "          \"pattern\": \"^(fine)\"\n" +
                                      "        },\n" +
                                      "        \"extra\": {\n" +
                                      "          \"type\": \"string\"\n" +
                                      "        }\n" +
                                      "      },\n" +
                                      "      \"required\": [\"type\", \"extra\"]\n" +
                                      "    }\n" +
                                      "  ]}";

    doTest(schema, "type: good");
    doTest(schema, "type: ok");
    doTest(schema, "type: <warning>doog</warning>");
    doTest(schema, "type: <warning>ko</warning>");
  }

  public void testArrayRefs() throws Exception {
    @Language("JSON") String schema = "{\n" +
                                      "  \"myDefs\": {\n" +
                                      "    \"myArray\": [\n" +
                                      "      {\n" +
                                      "        \"type\": \"number\"\n" +
                                      "      },\n" +
                                      "      {\n" +
                                      "        \"type\": \"boolean\"\n" +
                                      "      }\n" +
                                      "    ]\n" +
                                      "  },\n" +
                                      "  \"type\": \"array\",\n" +
                                      "  \"items\": [\n" +
                                      "    {\n" +
                                      "      \"$ref\": \"#/myDefs/myArray/0\"\n" +
                                      "    },\n" +
                                      "    {\n" +
                                      "      \"$ref\": \"#/myDefs/myArray/1\"\n" +
                                      "    }\n" +
                                      "  ]\n" +
                                      "}";

    doTest(schema, "- 1\n- <warning>2</warning>");
    doTest(schema, "- <warning>a</warning>\n- <warning>2</warning>");
    doTest(schema, "- <warning>a</warning>\n- true");
    doTest(schema, "- 1\n- false");
  }

  public void testWithTags() throws Exception {
    @Language("JSON") String schema = "{\"properties\": { \"platform\": { \"enum\": [\"x86\", \"x64\"] } }}";
    doTest(schema, "platform:\n  !!str x64");
    doTest(schema, "platform:\n  <warning>a x64</warning>");
  }

  static String schema(final String s) {
    return "{\"type\": \"object\", \"properties\": {\"prop\": " + s + "}}";
  }

  public static String rootObjectRedefinedSchema() {
    return "{\n" +
           "  \"$schema\": \"http://json-schema.org/draft-04/schema#\",\n" +
           "  \"type\": \"object\",\n" +
           "  \"$ref\" : \"#/definitions/root\",\n" +
           "  \"definitions\": {\n" +
           "    \"root\" : {\n" +
           "      \"type\": \"object\",\n" +
           "      \"additionalProperties\": false,\n" +
           "      \"properties\": {\n" +
           "        \"r1\": {\n" +
           "          \"type\": \"string\"\n" +
           "        },\n" +
           "        \"r2\": {\n" +
           "          \"type\": \"string\"\n" +
           "        }\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}\n";
  }
}
