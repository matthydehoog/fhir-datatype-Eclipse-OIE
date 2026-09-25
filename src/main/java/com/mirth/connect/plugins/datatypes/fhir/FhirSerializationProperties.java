/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.datatype.DataTypePropertyDescriptor;
import com.mirth.connect.model.datatype.PropertyEditorType;
import com.mirth.connect.model.datatype.SerializationProperties;

/**
 * Settings of the FHIR data type.
 *
 * The engine's XStream deserializer does not run the constructor, so a property added in a later
 * version is null in a saved channel. The getters fall back to the defaults, which is why the
 * properties that default to true are Booleans.
 */
public class FhirSerializationProperties extends SerializationProperties {

    /** Only R4 for now; R4B and R5 can be added later without changing saved channels. */
    public enum FhirVersion {
        R4
    }

    public enum OutputFormat {
        JSON, XML
    }

    public enum InvalidMessages {
        Reject, Accept
    }

    public enum FailOn {
        Errors, Warnings
    }

    public enum UnknownCodeSystems {
        Warning, Error, Information
    }

    private FhirVersion fhirVersion = FhirVersion.R4;
    private Boolean stripNamespaces = Boolean.TRUE;
    private OutputFormat outputFormat = OutputFormat.JSON;
    private Boolean prettyPrint = Boolean.TRUE;
    private Boolean validateInbound = Boolean.TRUE;
    private boolean validateOutbound = false;
    private InvalidMessages invalidMessages = InvalidMessages.Reject;
    private FailOn failOn = FailOn.Errors;
    private String profiles = "";
    private String requiredProfile = "";
    private UnknownCodeSystems unknownCodeSystems = UnknownCodeSystems.Warning;
    private Boolean anyExtensionsAllowed = Boolean.TRUE;
    private String terminologyServer = "";

    public FhirSerializationProperties() {}

    @Override
    public Map<String, DataTypePropertyDescriptor> getPropertyDescriptors() {
        Map<String, DataTypePropertyDescriptor> p = new LinkedHashMap<>();
        p.put("fhirVersion", new DataTypePropertyDescriptor(getFhirVersion(), "FHIR Version", "The FHIR version of the messages. Used for validation and for converting between JSON and XML.", PropertyEditorType.OPTION, FhirVersion.values()));
        p.put("stripNamespaces", new DataTypePropertyDescriptor(isStripNamespaces(), "Strip Namespaces", "If checked, the FHIR namespace (http://hl7.org/fhir) is removed from the XML the transformer works on, so you can write msg.name.family.@value instead of using namespaces. It is put back when the message leaves the transformer. The XHTML namespace of narratives (text.div) is kept.", PropertyEditorType.BOOLEAN));
        p.put("outputFormat", new DataTypePropertyDescriptor(getOutputFormat(), "Output Format", "The format of the outbound message (the encoded data): FHIR JSON or FHIR XML. Messages come in as either; the transformer always works on FHIR XML.", PropertyEditorType.OPTION, OutputFormat.values()));
        p.put("prettyPrint", new DataTypePropertyDescriptor(isPrettyPrint(), "Pretty Print", "If checked, outbound JSON and XML created by the FHIR engine are indented.", PropertyEditorType.BOOLEAN));
        p.put("validateInbound", new DataTypePropertyDescriptor(isValidateInbound(), "Validate Inbound", "If checked, every inbound message is validated against the FHIR specification and the profiles below: structure, data types, cardinality, required elements, value set bindings and invariants.", PropertyEditorType.BOOLEAN));
        p.put("validateOutbound", new DataTypePropertyDescriptor(isValidateOutbound(), "Validate Outbound", "If checked, the outbound message is validated too, after the transformer. An invalid outbound message is always rejected.", PropertyEditorType.BOOLEAN));
        p.put("invalidMessages", new DataTypePropertyDescriptor(getInvalidMessages(), "Invalid Messages", "Reject: an invalid inbound message gets status ERROR, with the validation issues as the error. Accept: it is processed normally, and the connector map gets fhirValid (true/false), fhirIssueCount and fhirIssues (the issues as text) for your filter or transformer.", PropertyEditorType.OPTION, InvalidMessages.values()));
        p.put("failOn", new DataTypePropertyDescriptor(getFailOn(), "Invalid When", "Errors: a message is invalid when validation finds errors. Warnings: warnings make it invalid too.", PropertyEditorType.OPTION, FailOn.values()));
        p.put("profiles", new DataTypePropertyDescriptor(getProfiles(), "Profile Packages", "Files or folders on the server with profiles to validate against, separated by ';'. A folder is read with its subfolders. Accepted: FHIR packages (.tgz, as downloaded from packages.fhir.org or simplifier.net, e.g. nictiz.fhir.nl.r4.zib2020 with its dependencies) and StructureDefinition, ValueSet and CodeSystem files (.json or .xml). A resource is validated against the profiles in its meta.profile.", PropertyEditorType.STRING));
        p.put("requiredProfile", new DataTypePropertyDescriptor(getRequiredProfile(), "Required Profile", "The canonical URL of a profile every message must conform to, whatever its meta.profile says, for example http://nictiz.nl/fhir/StructureDefinition/nl-core-Patient. Leave empty to only use meta.profile.", PropertyEditorType.STRING));
        p.put("unknownCodeSystems", new DataTypePropertyDescriptor(getUnknownCodeSystems(), "Unknown Code Systems", "How a code from a code system the validator does not know is reported (for example SNOMED CT or LOINC, which are not part of the specification). Warning is usual; use Error to require every code to be checked, together with a terminology server.", PropertyEditorType.OPTION, UnknownCodeSystems.values()));
        p.put("anyExtensionsAllowed", new DataTypePropertyDescriptor(isAnyExtensionsAllowed(), "Allow Unknown Extensions", "If checked, extensions whose definition the validator does not know are accepted. If not checked, they are errors.", PropertyEditorType.BOOLEAN));
        p.put("terminologyServer", new DataTypePropertyDescriptor(getTerminologyServer(), "Terminology Server", "Optional base URL of a FHIR terminology server (for example the Nationale Terminologieserver) to check codes the validator cannot check itself. Every such code is a call to the server. Leave empty to validate offline.", PropertyEditorType.STRING));
        return p;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        if (properties == null) {
            return;
        }
        if (properties.get("fhirVersion") != null) {
            fhirVersion = (FhirVersion) properties.get("fhirVersion");
        }
        if (properties.get("stripNamespaces") != null) {
            stripNamespaces = (Boolean) properties.get("stripNamespaces");
        }
        if (properties.get("outputFormat") != null) {
            outputFormat = (OutputFormat) properties.get("outputFormat");
        }
        if (properties.get("prettyPrint") != null) {
            prettyPrint = (Boolean) properties.get("prettyPrint");
        }
        if (properties.get("validateInbound") != null) {
            validateInbound = (Boolean) properties.get("validateInbound");
        }
        if (properties.get("validateOutbound") != null) {
            validateOutbound = (Boolean) properties.get("validateOutbound");
        }
        if (properties.get("invalidMessages") != null) {
            invalidMessages = (InvalidMessages) properties.get("invalidMessages");
        }
        if (properties.get("failOn") != null) {
            failOn = (FailOn) properties.get("failOn");
        }
        if (properties.get("profiles") != null) {
            profiles = (String) properties.get("profiles");
        }
        if (properties.get("requiredProfile") != null) {
            requiredProfile = (String) properties.get("requiredProfile");
        }
        if (properties.get("unknownCodeSystems") != null) {
            unknownCodeSystems = (UnknownCodeSystems) properties.get("unknownCodeSystems");
        }
        if (properties.get("anyExtensionsAllowed") != null) {
            anyExtensionsAllowed = (Boolean) properties.get("anyExtensionsAllowed");
        }
        if (properties.get("terminologyServer") != null) {
            terminologyServer = (String) properties.get("terminologyServer");
        }
    }

    /** The options for {@link FhirEngine#validate}. */
    public Map<String, String> engineOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(FhirEngine.FHIR_VERSION, getFhirVersion().name());
        options.put(FhirEngine.PROFILES, getProfiles());
        options.put(FhirEngine.REQUIRED_PROFILE, getRequiredProfile());
        options.put(FhirEngine.UNKNOWN_CODE_SYSTEMS, getUnknownCodeSystems().name().toLowerCase());
        options.put(FhirEngine.ANY_EXTENSIONS_ALLOWED, String.valueOf(isAnyExtensionsAllowed()));
        options.put(FhirEngine.TERMINOLOGY_SERVER, getTerminologyServer());
        return options;
    }

    public FhirVersion getFhirVersion() {
        return fhirVersion == null ? FhirVersion.R4 : fhirVersion;
    }

    public void setFhirVersion(FhirVersion fhirVersion) {
        this.fhirVersion = fhirVersion;
    }

    public boolean isStripNamespaces() {
        return stripNamespaces == null || stripNamespaces;
    }

    public void setStripNamespaces(boolean stripNamespaces) {
        this.stripNamespaces = stripNamespaces;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat == null ? OutputFormat.JSON : outputFormat;
    }

    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    public boolean isPrettyPrint() {
        return prettyPrint == null || prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isValidateInbound() {
        return validateInbound == null || validateInbound;
    }

    public void setValidateInbound(boolean validateInbound) {
        this.validateInbound = validateInbound;
    }

    public boolean isValidateOutbound() {
        return validateOutbound;
    }

    public void setValidateOutbound(boolean validateOutbound) {
        this.validateOutbound = validateOutbound;
    }

    public InvalidMessages getInvalidMessages() {
        return invalidMessages == null ? InvalidMessages.Reject : invalidMessages;
    }

    public void setInvalidMessages(InvalidMessages invalidMessages) {
        this.invalidMessages = invalidMessages;
    }

    public FailOn getFailOn() {
        return failOn == null ? FailOn.Errors : failOn;
    }

    public void setFailOn(FailOn failOn) {
        this.failOn = failOn;
    }

    public String getProfiles() {
        return profiles == null ? "" : profiles;
    }

    public void setProfiles(String profiles) {
        this.profiles = profiles;
    }

    public String getRequiredProfile() {
        return requiredProfile == null ? "" : requiredProfile;
    }

    public void setRequiredProfile(String requiredProfile) {
        this.requiredProfile = requiredProfile;
    }

    public UnknownCodeSystems getUnknownCodeSystems() {
        return unknownCodeSystems == null ? UnknownCodeSystems.Warning : unknownCodeSystems;
    }

    public void setUnknownCodeSystems(UnknownCodeSystems unknownCodeSystems) {
        this.unknownCodeSystems = unknownCodeSystems;
    }

    public boolean isAnyExtensionsAllowed() {
        return anyExtensionsAllowed == null || anyExtensionsAllowed;
    }

    public void setAnyExtensionsAllowed(boolean anyExtensionsAllowed) {
        this.anyExtensionsAllowed = anyExtensionsAllowed;
    }

    public String getTerminologyServer() {
        return terminologyServer == null ? "" : terminologyServer;
    }

    public void setTerminologyServer(String terminologyServer) {
        this.terminologyServer = terminologyServer;
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {}
    @Override public void migrate3_2_0(DonkeyElement element) {}
    @Override public void migrate3_3_0(DonkeyElement element) {}
    @Override public void migrate3_4_0(DonkeyElement element) {}
    @Override public void migrate3_5_0(DonkeyElement element) {}
    @Override public void migrate3_6_0(DonkeyElement element) {}
    @Override public void migrate3_7_0(DonkeyElement element) {}
    @Override public void migrate3_9_0(DonkeyElement element) {}
    @Override public void migrate3_11_0(DonkeyElement element) {}
    @Override public void migrate3_11_1(DonkeyElement element) {}
    @Override public void migrate3_12_0(DonkeyElement element) {}
    // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purged = new HashMap<>();
        purged.put("fhirVersion", getFhirVersion());
        purged.put("stripNamespaces", isStripNamespaces());
        purged.put("outputFormat", getOutputFormat());
        purged.put("prettyPrint", isPrettyPrint());
        purged.put("validateInbound", isValidateInbound());
        purged.put("validateOutbound", isValidateOutbound());
        purged.put("invalidMessages", getInvalidMessages());
        purged.put("failOn", getFailOn());
        purged.put("profilesSet", !getProfiles().isEmpty());
        purged.put("requiredProfileSet", !getRequiredProfile().isEmpty());
        purged.put("unknownCodeSystems", getUnknownCodeSystems());
        purged.put("anyExtensionsAllowed", isAnyExtensionsAllowed());
        purged.put("terminologyServerSet", !getTerminologyServer().isEmpty());
        return purged;
    }
}
