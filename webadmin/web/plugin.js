// FHIR data type - properties panel for the web administrator (equivalent of the Swing
// DataTypeClientPlugin properties). Same shape as the built-in data type plugins.
const PKG = "com.mirth.connect.plugins.datatypes.fhir";

const bool = (key, label, def, hint) => ({ key, label, type: "checkbox", default: def, hint });
const text = (key, label, def, hint) => ({ key, label, type: "text", default: def, hint });
const opt = (key, label, values, def, hint) => ({ key, label, type: "select", options: values.map((value) => ({ value, label: value })), default: def, hint });

const DEF = {
  name: "FHIR",
  label: "FHIR",
  order: 73,
  propertiesClass: `${PKG}.FhirDataTypeProperties`,
  groups: [
    {
      key: "serializationProperties",
      label: "Serialization",
      class: `${PKG}.FhirSerializationProperties`,
      fields: [
        opt("fhirVersion", "FHIR Version", ["R4"], "R4", "The FHIR version of the messages. Used for validation and for converting between JSON and XML."),
        bool("stripNamespaces", "Strip Namespaces", true, "If checked, the FHIR namespace (http://hl7.org/fhir) is removed from the XML the transformer works on, so you can write msg.name.family.@value. It is put back when the message leaves the transformer. The XHTML namespace of narratives (text.div) is kept."),
        opt("outputFormat", "Output Format", ["JSON", "XML"], "JSON", "The format of the outbound message (the encoded data): FHIR JSON or FHIR XML. Messages come in as either; the transformer always works on FHIR XML."),
        bool("prettyPrint", "Pretty Print", true, "If checked, outbound JSON and XML created by the FHIR engine are indented."),
        bool("validateInbound", "Validate Inbound", true, "If checked, every inbound message is validated against the FHIR specification and the profiles below: structure, data types, cardinality, required elements, value set bindings and invariants."),
        bool("validateOutbound", "Validate Outbound", false, "If checked, the outbound message is validated too, after the transformer. An invalid outbound message is always rejected."),
        opt("invalidMessages", "Invalid Messages", ["Reject", "Accept"], "Reject", "Reject: an invalid inbound message gets status ERROR, with the validation issues as the error. Accept: it is processed normally, and the connector map gets fhirValid (true/false), fhirIssueCount and fhirIssues (the issues as text)."),
        opt("failOn", "Invalid When", ["Errors", "Warnings"], "Errors", "Errors: a message is invalid when validation finds errors. Warnings: warnings make it invalid too."),
        text("profiles", "Profile Packages", "", "Files or folders on the server with profiles, separated by ';'. FHIR packages (.tgz, e.g. nictiz.fhir.nl.r4.zib2020 with its dependencies) and StructureDefinition, ValueSet and CodeSystem files (.json or .xml). A resource is validated against the profiles in its meta.profile."),
        text("requiredProfile", "Required Profile", "", "Canonical URL of a profile every message must conform to, whatever its meta.profile says. Leave empty to only use meta.profile."),
        opt("unknownCodeSystems", "Unknown Code Systems", ["Warning", "Error", "Information"], "Warning", "How a code from a code system the validator does not know is reported (for example SNOMED CT or LOINC)."),
        bool("anyExtensionsAllowed", "Allow Unknown Extensions", true, "If checked, extensions whose definition the validator does not know are accepted. If not checked, they are errors."),
        text("terminologyServer", "Terminology Server", "", "Optional base URL of a FHIR terminology server to check codes the validator cannot check itself. Leave empty to validate offline.")
      ]
    }
  ]
};

DEF.defaults = (version) => {
  const props = { "@class": DEF.propertiesClass, "@version": version };
  for (const group of DEF.groups) {
    const obj = { "@class": group.class, "@version": version };
    for (const f of group.fields) obj[f.key] = f.default ?? null;
    props[group.key] = obj;
  }
  return props;
};

export function register(platform) {
  platform.registerDataType(DEF.name, DEF);
}
