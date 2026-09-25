# FHIR Data Type for Open Integration Engine

An HL7 FHIR data type for [Eclipse Open Integration Engine](https://github.com/OpenIntegrationEngine) (OIE, a fork of Mirth Connect):

- **FHIR JSON and FHIR XML in**: the format is detected per message.
- **FHIR XML in the transformer** (E4X), optionally without the FHIR namespace: `msg.name.family.@value`.
- **FHIR JSON or FHIR XML out**, chosen per connector.
- **Validation** with [HAPI FHIR](https://hapifhir.io) against the FHIR R4 specification and your own profiles and packages (for example Nictiz zib2020 / nl-core). It checks structure, data types, cardinality, required elements, value set bindings and invariants.
- **Invalid messages** are rejected (status ERROR, with the issues as the error) or accepted with the issues in the connector map; you choose per connector.

FHIR R4 (4.0.1) is supported. The design allows R4B and R5 to be added later without changing saved channels.

## Installation

1. Download `datatype-fhir-<version>.zip` from the [releases](https://github.com/matthydehoog/fhir-datatype-Eclipse-OIE/releases).
2. In the Administrator: *Settings > Extensions > Install Extension*, or unzip it into `<OIE_HOME>/extensions/`.
3. Restart the OIE service.

**Memory.** The validator loads the whole FHIR specification: about 300 MB of heap, more with large profile packages. Give the server at least 1 GB (`-Xmx1g` or more in `oieserver.vmoptions` / `oieservice.vmoptions`). The first validation after a deploy takes a few seconds; the plugin starts loading when the channel is deployed, and after that a message takes tens of milliseconds.

**Size.** HAPI FHIR and its libraries make the zip about 90 MB. They sit in `extensions/datatype-fhir/lib/` and are loaded in the plugin's own class loader, so they never clash with the libraries OIE ships.

## Properties

| Property | Default | What it does |
| --- | --- | --- |
| FHIR Version | R4 | The FHIR version of the messages. |
| Strip Namespaces | on | Removes the FHIR namespace from the transformer XML (the XHTML namespace of `text.div` stays) and puts it back afterwards. |
| Output Format | JSON | FHIR JSON or FHIR XML for the outbound message. |
| Pretty Print | on | Indents JSON and XML created by the FHIR engine. |
| Validate Inbound | on | Validates every inbound message. |
| Validate Outbound | off | Validates the outbound message after the transformer; an invalid one is always rejected. |
| Invalid Messages | Reject | *Reject*: status ERROR with the issues. *Accept*: processed normally, issues in the connector map. |
| Invalid When | Errors | *Errors*, or *Warnings* to treat warnings as invalid too. |
| Profile Packages | empty | Files or folders on the server with profiles, separated by `;`. |
| Required Profile | empty | Canonical URL every message must conform to, in addition to its `meta.profile`. |
| Unknown Code Systems | Warning | How codes from code systems the validator does not know (SNOMED CT, LOINC, ...) are reported. |
| Allow Unknown Extensions | on | Accept extensions whose definition is unknown. |
| Terminology Server | empty | Optional FHIR terminology server for codes the validator cannot check offline. |

### Connector map

Inbound validation sets these variables, also when invalid messages are accepted:

| Variable | Value |
| --- | --- |
| `fhirValid` | `true` or `false` |
| `fhirIssueCount` | number of errors and warnings |
| `fhirIssues` | one line per issue: `error Patient.gender (line 1, column 39): ...` |
| `mirth_type` | resource type, e.g. `Patient` or `Bundle` |
| `mirth_version` | `R4` |

For example, a source filter that only lets valid messages through, with *Invalid Messages* on *Accept*:

```javascript
if ($('fhirValid') == false) {
    logger.warn('Invalid FHIR message:\n' + $('fhirIssues'));
    return false;
}
return true;
```

## Profiles and packages

Put the packages or files on the server and enter the folder in *Profile Packages*, for example `C:\fhir\profiles` or `/opt/oie/fhir/profiles`. A folder is read with its subfolders. Accepted:

- **FHIR packages** (`.tgz`) as downloaded from [packages.fhir.org](https://packages.fhir.org) or [simplifier.net](https://simplifier.net). Include every package a package depends on (`hl7.fhir.r4.core` is built in). For the Dutch profiles: `nictiz.fhir.nl.r4.nl-core` and `nictiz.fhir.nl.r4.zib2020`.
- **Conformance resources**: StructureDefinition, ValueSet, CodeSystem, ConceptMap and NamingSystem files in JSON or XML.

A resource is validated against the profiles in its `meta.profile`. Set *Required Profile* to validate every message against one profile, for example `http://nictiz.nl/fhir/StructureDefinition/nl-core-Patient`. An unknown profile is an error.

Profiles are loaded once per combination of settings and kept in memory; restart the channel after changing the files.

## The transformer

With *Strip Namespaces* on, the transformer works on plain FHIR XML:

```javascript
var family = msg.name[0].family.@value.toString();
msg.gender.@value = 'female';

// A new element: FHIR XML keeps values in the value attribute.
msg.appendChild(<birthDate value="1980-01-01"/>);
```

Mind the FHIR element order when you add elements and choose XML output: FHIR XML requires the order of the specification. JSON output is ordered by the FHIR engine.

### In scripts

`FhirTools` validates and converts in any script, for example an HTTP response:

```javascript
var FhirTools = Packages.com.mirth.connect.plugins.datatypes.fhir.FhirTools;

var result = FhirTools.validate(response.getMessage());
if (!result.isValid()) {
    logger.warn(result.summary());
}

var json = FhirTools.toJson(msg.toString()); // transformer XML to JSON
```

`FhirTools.validate(resource, requiredProfile, profilePackages)` validates against your own profiles.

## Behaviour to know

- **No transformer**: without filter or transformer steps a message passes through unchanged, unless the output format differs from the input (JSON in, XML out or the other way round); then it is converted.
- **Unknown elements**: converting XML to JSON (and JSON to XML output) refuses elements that are not in the specification instead of dropping them silently. Inbound validation reports them first.
- **Bundles** are one message. There is no batch splitting.
- **Accept on outbound** does not exist: an invalid outbound message is always rejected, because there is no later step to handle it.

## Building

JDK 17 and Maven. The engine jars (`com.mirth.connect:*:4.6.0`) must be in the local Maven repository.

```bash
mvn package
```

The result is `target/datatype-fhir-<version>.zip`. The plugin jars target Java 11; the FHIR engine needs Java 17 (HAPI FHIR 8), which OIE ships with.

## License

[Mozilla Public License 2.0](LICENSE). HAPI FHIR is licensed under the Apache License 2.0.
