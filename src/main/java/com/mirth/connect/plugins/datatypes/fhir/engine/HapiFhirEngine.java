/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.UnknownCodeSystemWarningValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.mirth.connect.plugins.datatypes.fhir.FhirEngine;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;

/**
 * HAPI FHIR behind {@link FhirEngine}. A validator is expensive to build (it loads the whole
 * specification and the profiles), so one is kept per set of options; FhirValidator is thread safe.
 */
public final class HapiFhirEngine implements FhirEngine {

    /** Conformance resources taken from profile files and packages; examples and the like are skipped. */
    private static final Set<String> CONFORMANCE_TYPES = Set.of("StructureDefinition", "ValueSet", "CodeSystem", "ConceptMap", "NamingSystem");

    private final Map<String, FhirValidator> validators = new ConcurrentHashMap<>();

    @Override
    public List<Map<String, String>> validate(String resource, Map<String, String> options) throws Exception {
        return withEngineClassLoader(() -> {
            FhirValidator validator = validator(options);
            ValidationOptions validationOptions = new ValidationOptions();
            String required = options.getOrDefault(REQUIRED_PROFILE, "").trim();
            if (!required.isEmpty()) {
                validationOptions.addProfile(required);
            }
            ValidationResult result = validator.validateWithResult(resource, validationOptions);
            List<Map<String, String>> issues = new ArrayList<>();
            for (SingleValidationMessage m : result.getMessages()) {
                Map<String, String> issue = new LinkedHashMap<>();
                issue.put(SEVERITY, m.getSeverity() == null ? "error" : m.getSeverity().getCode());
                issue.put(LOCATION, m.getLocationString() == null ? "" : m.getLocationString());
                issue.put(LINE, m.getLocationLine() == null ? "" : String.valueOf(m.getLocationLine()));
                issue.put(COLUMN, m.getLocationCol() == null ? "" : String.valueOf(m.getLocationCol()));
                issue.put(MESSAGE, m.getMessage() == null ? "" : m.getMessage());
                issue.put(MESSAGE_ID, m.getMessageId() == null ? "" : m.getMessageId());
                issues.add(issue);
            }
            return issues;
        });
    }

    @Override
    public String toJson(String xml, String fhirVersion, boolean pretty) throws Exception {
        return withEngineClassLoader(() -> {
            FhirContext ctx = context(fhirVersion);
            IBaseResource resource = strict(ctx.newXmlParser()).parseResource(xml);
            return ctx.newJsonParser().setPrettyPrint(pretty).encodeResourceToString(resource);
        });
    }

    @Override
    public String toXml(String json, String fhirVersion, boolean pretty) throws Exception {
        return withEngineClassLoader(() -> {
            FhirContext ctx = context(fhirVersion);
            IBaseResource resource = strict(ctx.newJsonParser()).parseResource(json);
            return ctx.newXmlParser().setPrettyPrint(pretty).encodeResourceToString(resource);
        });
    }

    @Override
    public void warmUp(Map<String, String> options) {
        try {
            withEngineClassLoader(() -> validator(options));
        } catch (Exception e) {
            // Reported again, with the details, when the first message is validated.
        }
    }

    // ------------------------------------------------------------------ validator

    private FhirValidator validator(Map<String, String> options) {
        Map<String, String> key = new TreeMap<>(options);
        key.remove(REQUIRED_PROFILE); // passed per call, not part of the validator
        return validators.computeIfAbsent(key.toString(), k -> build(options));
    }

    private FhirValidator build(Map<String, String> options) {
        FhirContext ctx = context(options.get(FHIR_VERSION));

        List<IValidationSupport> supports = new ArrayList<>();
        PrePopulatedValidationSupport profiles = loadProfiles(ctx, options.getOrDefault(PROFILES, ""));
        if (profiles != null) {
            supports.add(profiles);
        }
        supports.add(new DefaultProfileValidationSupport(ctx));
        supports.add(new CommonCodeSystemsTerminologyService(ctx));
        supports.add(new InMemoryTerminologyServerValidationSupport(ctx));
        supports.add(new SnapshotGeneratingValidationSupport(ctx));
        String terminologyServer = options.getOrDefault(TERMINOLOGY_SERVER, "").trim();
        if (!terminologyServer.isEmpty()) {
            supports.add(new RemoteTerminologyServiceValidationSupport(ctx, terminologyServer));
        }
        UnknownCodeSystemWarningValidationSupport unknown = new UnknownCodeSystemWarningValidationSupport(ctx);
        unknown.setNonExistentCodeSystemSeverity(severity(options.getOrDefault(UNKNOWN_CODE_SYSTEMS, "warning")));
        supports.add(unknown);

        FhirInstanceValidator module = new FhirInstanceValidator(new ValidationSupportChain(supports));
        module.setAnyExtensionsAllowed(!"false".equalsIgnoreCase(options.get(ANY_EXTENSIONS_ALLOWED)));
        module.setErrorForUnknownProfiles(true);
        // Recommendations such as dom-6 ("should have narrative") would flag nearly every message.
        module.setBestPracticeWarningLevel(BestPracticeWarningLevel.Ignore);

        FhirValidator validator = ctx.newValidator();
        validator.registerValidatorModule(module);
        return validator;
    }

    /**
     * Profiles from files and folders: NPM packages (.tgz, as published on packages.fhir.org, e.g.
     * nictiz.fhir.nl.r4.zib2020) and single conformance resources in JSON or XML.
     */
    private static PrePopulatedValidationSupport loadProfiles(FhirContext ctx, String paths) {
        List<File> files = new ArrayList<>();
        for (String path : paths.split(";")) {
            if (!path.trim().isEmpty()) {
                collect(new File(path.trim()), files);
            }
        }
        if (files.isEmpty()) {
            return null;
        }
        PrePopulatedValidationSupport support = new PrePopulatedValidationSupport(ctx);
        for (File file : files) {
            String name = file.getName().toLowerCase();
            try {
                if (name.endsWith(".tgz") || name.endsWith(".tar.gz")) {
                    loadPackage(ctx, file, support);
                } else {
                    String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    IParser parser = name.endsWith(".xml") ? ctx.newXmlParser() : ctx.newJsonParser();
                    add(support, parser.parseResource(text));
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot load profile file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
            }
        }
        return support;
    }

    private static void loadPackage(FhirContext ctx, File file, PrePopulatedValidationSupport support) throws IOException {
        NpmPackage npm;
        try (InputStream in = new FileInputStream(file)) {
            npm = NpmPackage.fromPackage(in);
        }
        IParser parser = ctx.newJsonParser();
        for (String resourceFile : npm.listResources(CONFORMANCE_TYPES.toArray(new String[0]))) {
            try (InputStream in = npm.loadResource(resourceFile)) {
                add(support, parser.parseResource(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
    }

    private static void add(PrePopulatedValidationSupport support, IBaseResource resource) {
        if (resource != null && CONFORMANCE_TYPES.contains(resource.fhirType())) {
            support.addResource(resource);
        }
    }

    private static void collect(File file, List<File> files) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children);
                for (File child : children) {
                    collect(child, files);
                }
            }
        } else if (file.isFile()) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".tgz") || name.endsWith(".tar.gz") || name.endsWith(".json") || name.endsWith(".xml")) {
                files.add(file);
            }
        } else {
            throw new IllegalArgumentException("Profile path not found: " + file.getAbsolutePath());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Only R4 for now; a new version needs its structures and validation-resources libraries. */
    private static FhirContext context(String fhirVersion) {
        if (fhirVersion == null || fhirVersion.isEmpty() || fhirVersion.equalsIgnoreCase("R4")) {
            return FhirContext.forCached(FhirVersionEnum.R4);
        }
        throw new IllegalArgumentException("FHIR version " + fhirVersion + " is not supported.");
    }

    private static IParser strict(IParser parser) {
        return parser.setParserErrorHandler(new StrictErrorHandler());
    }

    private static IValidationSupport.IssueSeverity severity(String value) {
        switch (value.toLowerCase()) {
            case "error":
                return IValidationSupport.IssueSeverity.ERROR;
            case "information":
                return IValidationSupport.IssueSeverity.INFORMATION;
            default:
                return IValidationSupport.IssueSeverity.WARNING;
        }
    }

    /** HAPI finds its XML and JSON implementations through the context class loader. */
    private <T> T withEngineClassLoader(Callable<T> work) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(getClass().getClassLoader());
        try {
            return work.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
