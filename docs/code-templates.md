# Code templates

Since 1.2.0 the FHIR Data Type adds a **FHIR Functions** category to the reference list of the script editors. It holds 16 ready-made snippets for working with FHIR in a channel: validating and converting with `FhirTools`, reading the result of the inbound validation, and E4X patterns for FHIR XML. They come with the plugin; there is nothing to import in the Swing Administrator.

## Using them

1. Open a script: a filter rule or transformer step of type *JavaScript*, a response transformer, or a deploy, undeploy, preprocessor or postprocessor script.
2. On the right of the editor, open the **Reference** tab and choose the category **FHIR Functions**.
3. Hover over a template to read what it does.
4. Drag the template into the script. Its code is inserted where you drop it; the description is not.
5. Adapt the inserted code where needed (see the table below), save and deploy the channel.

The list only shows the templates that fit the script you are editing:

| Templates | Shown in |
| --- | --- |
| `FhirTools` (validate, convert) | every script |
| Validation variables | connector scripts: source and destination filters and transformers, response transformers |
| E4X on FHIR XML | filters and transformers (source, destination and response), where `msg` is the message |

These are *drag-and-drop* templates: they insert code once and are never added to your channel's scripts the way *function* templates from a code template library are. The category *Conversion Functions* also has the standard *Convert FHIR to XML* and *Convert XML to FHIR* entries that every data type gets.

## The templates

### FhirTools

`FhirTools` validates and converts in any script. The templates start with `var FhirTools = Packages.com.mirth.connect.plugins.datatypes.fhir.FhirTools;`; declare it once per script.

| Template | Code | Adapt |
| --- | --- | --- |
| Validate FHIR resource | `FhirTools.validate(msg.toString())`, then `result.isValid()` and `result.summary()` | Validate something else than `msg`, for example a response: `FhirTools.validate(response.getMessage())`. |
| Validate FHIR resource against a profile | `FhirTools.validate(resource, profileUrl, packages)` | The canonical URL of the profile, and the folders or files with your profiles and NPM packages separated by `;`. Use `''` for profiles of the FHIR specification itself, such as `http://hl7.org/fhir/StructureDefinition/bodyweight`. |
| Read FHIR validation issues | Loops over `result.getProblems()` | Each issue is a map with `severity`, `location`, `line`, `column`, `message` and `messageId`. `getProblems()` leaves out information messages, `getIssues()` returns all. |
| Convert FHIR XML to JSON | `FhirTools.toJson(msg.toString())` | Nothing. XML with or without the FHIR namespace is accepted. |
| Convert FHIR JSON to XML | `FhirTools.toXml(json)` | Provide `json`. The result has the FHIR namespace; mind that if you parse it with `new XML(...)`. |

`validate` and `toJson` accept FHIR JSON and FHIR XML with or without the namespace, so `msg.toString()` works in a transformer with *Strip Namespaces* on. Positions in the issues refer to the text you validated: for `msg.toString()` that is the transformer XML, not the message as it came in.

### Validation variables

The data type validates every inbound message (*Validate Inbound*, on by default) and puts the result in the connector map, also when *Invalid Messages* is on *Accept*. Read it with `$()` in the same connector.

| Template | Code | Value |
| --- | --- | --- |
| Is the FHIR message valid | `$('fhirValid')` | `true` or `false` |
| Get FHIR issue count | `$('fhirIssueCount')` | number of errors and warnings |
| Get FHIR issues | `$('fhirIssues')` | one line per error and warning, with the position in the message as received |
| Get FHIR OperationOutcome | `$('fhirOperationOutcome')` | the issues as a FHIR `OperationOutcome` (JSON) |
| Filter out invalid FHIR messages | a filter rule on `$('fhirValid')` | logs the issues and filters the message |

The filter template only makes sense with *Invalid Messages* on *Accept*: with *Reject*, an invalid message ends as *Error* before the filter runs. With *Accept* and the rule, it ends as *Filtered*.

### E4X on FHIR XML

The transformer works on FHIR XML. These templates assume **Strip Namespaces** is on (the default); otherwise E4X needs the FHIR namespace in every path.

| Template | Code | Adapt |
| --- | --- | --- |
| Read a FHIR value | `msg.name[0].family.@value.toString()` | The path. FHIR XML keeps values in the `value` attribute, so a path ends in `.@value`. |
| Set a FHIR value | `msg.gender.@value = 'female';` | The path and the value. |
| Add a FHIR element | `msg.appendChild(<birthDate value="1980-01-01"/>);` | The order does not matter: the outbound message, JSON or XML, is written in the element order of the specification. |
| Iterate FHIR Bundle entries | `for each (var entry in msg.entry)` with `entry.resource.children()[0]` | What to do per resource; `localName()` gives its type. |
| Find a FHIR coding by system | `msg.code.coding.(system.@value == 'http://loinc.org').code.@value` | The path to the CodeableConcept and the system. |
| Find a FHIR extension by URL | `msg.extension.(@url == '...')` | The extension URL; read the value with e.g. `.valueString.@value`. |

To keep a number from a script in the channel map, store it as a string, for example `channelMap.put('count', String(issues.size()))`; a JavaScript number shows as `3.0` otherwise.

## Web administrator

The web administrator's reference list has no way yet for plugins to add a category ([gibson9583/oie-web-client#81](https://github.com/gibson9583/oie-web-client/issues/81)). It does show code template libraries, so the same templates are available as a library:

1. Open **Code Templates** and click **Import Libraries**.
2. Select [`examples/fhir-functions-code-templates.xml`](../examples/fhir-functions-code-templates.xml).
3. In a script editor's **Reference** panel, choose the category **FHIR Functions**.

The library is set to include all channels, because the web administrator only lists libraries that apply to the channel; its drag-and-drop templates still add nothing to the scripts. You don't need it in the Swing Administrator, where it would show the templates a second time under *User Defined Code*.

## For developers

- The templates are defined once, in `FhirDataTypeCodeTemplatePlugin.fhirTemplates()` (client jar). OIE loads the class through `<templateClassName>` in `plugin.xml` and calls `getReferenceItems()`, which adds the *FHIR Functions* category to the standard conversion entries.
- `fhirLibrary()` wraps the same list in a code template library with fixed IDs and dates. `examples/fhir-functions-code-templates.xml` is generated from it with the engine's `ObjectXMLSerializer`; a test fails when the file is out of date. Regenerate it with `mvn test -DupdateExamples=true`.
- The tests compile every template in Rhino 1.7.13 with E4X, and run the E4X templates against namespace-stripped FHIR XML.
