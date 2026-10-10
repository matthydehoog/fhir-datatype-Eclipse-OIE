# Development guide

This guide is for people who build, change or release the FHIR Data Type. For using the data type in a channel, see the [README](../README.md); for the script snippets, see [code-templates.md](code-templates.md).

## Contents

- [Repository layout](#repository-layout)
- [Building](#building)
- [How the plugin is put together](#how-the-plugin-is-put-together)
- [Message flow](#message-flow)
- [The isolated FHIR engine](#the-isolated-fhir-engine)
- [Validation](#validation)
- [Properties and saved channels](#properties-and-saved-channels)
- [Batch processing and responses](#batch-processing-and-responses)
- [Code templates and the web administrator](#code-templates-and-the-web-administrator)
- [Dependencies](#dependencies)
- [Testing](#testing)
- [Trying it in OIE](#trying-it-in-oie)
- [Common changes](#common-changes)
- [Releasing](#releasing)
- [Conventions](#conventions)

## Repository layout

| Path | What it is |
| --- | --- |
| `src/main/java/.../fhir/` | Shared classes: serializer, properties, the `FhirEngine` interface and its loader, and the pure-Java JSON/XML handling. Goes into `datatype-fhir-shared.jar`. |
| `src/main/java/.../fhir/server/` | Server-only classes: server plugin, batch adaptor, auto responder. `datatype-fhir-server.jar`. |
| `src/main/java/.../fhir/client/` | Administrator classes: client plugin and the code templates. `datatype-fhir-client.jar`. |
| `src/main/java/.../fhir/engine/` | `HapiFhirEngine`, the only code that touches HAPI FHIR. `lib/datatype-fhir-engine.jar`. |
| `src/main/resources/plugin.xml` | The extension descriptor the engine reads. |
| `src/test/` | JUnit 4 tests, plus a test profile in `resources/profiles/`. |
| `webadmin/` | The properties panel for the OIE web administrator (`plugin.json`, `web/plugin.js`). |
| `examples/` | Test messages and the generated code template library for the web administrator. |
| `docs/` | This guide and the code template reference. |
| `assembly.xml` | How the extension zip is laid out. |
| `oie.json` | The manifest for the OIE Community Store catalog. |
| `.github/` | Workflows for the catalog pull request and the Jira issue sync. |

`...` is `com/mirth/connect/plugins/datatypes`. The package name follows the engine's own data types, so the plugin looks like a built-in one.

## Building

### Prerequisites

- **JDK 17.** The plugin jars target Java 11, the engine jar Java 17 (see [Two Java levels](#two-java-levels)); one JDK 17 builds both.
- **Maven 3.8** or later.
- **The OIE 4.6.0 engine jars** in your local Maven repository. They are not published to Maven Central, so install them once from an OIE 4.6.0 installation:

| Maven artifact (`com.mirth.connect`, version `4.6.0`) | File in the OIE installation |
| --- | --- |
| `server-api` | `server-lib/mirth-server.jar` |
| `donkey-server` | `server-lib/donkey/donkey-server.jar` |
| `donkey-model` | `server-lib/donkey/donkey-model.jar` |
| `client` | `client-lib/mirth-client.jar` |
| `client-core` | `server-lib/mirth-client-core.jar` |

```bash
OIE_HOME="/opt/openintegrationengine"   # or "C:/Program Files/OpenIntegrationEngine"
install() {
  mvn install:install-file -Dfile="$OIE_HOME/$2" -DgroupId=com.mirth.connect -DartifactId="$1" -Dversion=4.6.0 -Dpackaging=jar
}
install server-api    server-lib/mirth-server.jar
install donkey-server server-lib/donkey/donkey-server.jar
install donkey-model  server-lib/donkey/donkey-model.jar
install client        client-lib/mirth-client.jar
install client-core   server-lib/mirth-client-core.jar
```

### Build

```bash
mvn clean package
```

The result is `target/datatype-fhir-<version>.zip`, about 80 MB. Always use `clean`: `target/lib` keeps jars from an earlier build, and a dependency you excluded would otherwise still end up in the zip.

`mvn package` runs the tests first. Add `-DskipTests` to skip them, or `-o` to build offline once everything is downloaded.

### Two Java levels

`pom.xml` compiles in two passes:

| Execution | Java | What |
| --- | --- | --- |
| `default-compile` | 11 | Everything except the `engine` package: the jars the engine and the Administrator load. |
| `engine-compile` | 17 | The `engine` package, because HAPI FHIR 8 needs Java 17. |
| `default-testCompile` | 17 | The tests. |

The Administrator can therefore still run on Java 11, while the server, which OIE ships with Java 17, runs the engine.

### What is in the zip

`maven-jar-plugin` makes four jars out of one source tree, by package; `assembly.xml` puts them in the zip:

```
datatype-fhir/
├── plugin.xml
├── datatype-fhir-shared.jar     fhir/*.class
├── datatype-fhir-server.jar     fhir/server/**
├── datatype-fhir-client.jar     fhir/client/**
├── lib/
│   ├── datatype-fhir-engine.jar fhir/engine/**
│   └── *.jar                    HAPI FHIR and its dependencies, unmodified
└── webadmin/                    the web administrator panel
```

The folder name `datatype-fhir` must equal the `path` attribute in `plugin.xml`: the engine and the web administrator build URLs from it.

## How the plugin is put together

OIE knows a data type through a few plugin points. This plugin implements them as follows:

| Plugin point | Class | Purpose |
| --- | --- | --- |
| `DataTypeDelegate` | `FhirDataTypeDelegate` | Name (`FHIR`), serializer, default properties; the transformer works on XML. |
| `DataTypeServerPlugin` | `server.FhirDataTypeServerPlugin` | Registers the data type on the server, plus the batch adaptor and the auto responder. |
| `DataTypeClientPlugin` | `client.FhirDataTypeClientPlugin` | Registers the data type in the Swing Administrator. |
| `DataTypeCodeTemplatePlugin` | `client.FhirDataTypeCodeTemplatePlugin` | The *FHIR Functions* reference list (`templateClassName` in `plugin.xml`). |
| `IMessageSerializer` | `FhirSerializer` | Inbound, transformer and outbound conversion, and validation. |
| `DataTypeProperties` | `FhirDataTypeProperties` | Holds `FhirSerializationProperties` and `FhirBatchProperties`. |

The other shared classes:

| Class | Role |
| --- | --- |
| `FhirXml` | Format detection, FHIR JSON to FHIR XML, namespace stripping and restoring, DOCTYPE check. Pure Java. |
| `FhirJson` | A small JSON reader and writer that keeps property order and the exact text of numbers (`1.50` stays `1.50`). Pure Java. |
| `FhirOperationOutcome` | Builds OperationOutcome JSON without HAPI. |
| `FhirValidation` | The result of one validation: issues, valid or not, summary. |
| `FhirBundleSplitter` | Splits a Bundle into one message per entry. Pure Java. |
| `FhirTools` | Static functions for channel scripts. |
| `FhirEngine`, `FhirEngineLoader` | The boundary to HAPI FHIR; see [The isolated FHIR engine](#the-isolated-fhir-engine). |
| `FhirVocabulary` | Required by the engine; adds no descriptions. |

"Pure Java" matters: those classes have no dependencies, so they also run in the Administrator, where HAPI FHIR is not available.

## Message flow

```
inbound message (FHIR JSON or XML)
   │
   ├─ populateMetaData()              resource type, version, inbound validation
   │                                  → connector map fhirValid, fhirIssueCount, fhirIssues, fhirOperationOutcome
   │
   ├─ with filter/transformer steps:
   │     toXML()                      refuse DOCTYPE, reject if invalid, JSON→XML (FhirXml), strip namespace
   │        ↓ transformer (E4X on msg)
   │     fromXML()                    refuse DOCTYPE, add namespace, encode(fromTransformer = true)
   │
   └─ without steps:
         transformWithoutSerializing()  refuse DOCTYPE, reject if invalid, encode(fromTransformer = false)

encode():  outbound validation (optional, always rejects)
           XML  → JSON  HAPI toJson
           JSON → XML   HAPI toXml
           XML  → XML   HAPI reorderXml, only after a transformer and only on the server
           otherwise    unchanged
```

Points worth knowing:

- **Validation runs once per message.** The engine calls `populateMetaData` first for every message, with or without a transformer. The result is kept in a `ThreadLocal` keyed on the properties object and the message text, and `toXML` / `transformWithoutSerializing` reuse it.
- **`populateMetaData` never throws.** A failure there would be lost; `toXML` raises it on the message instead.
- **JSON to transformer XML is pure Java** (`FhirXml.jsonToXml`), so the transformer sees exactly what came in: property order kept, nothing added or dropped. The way back uses HAPI with a `StrictErrorHandler`, so an element that is not in the specification is refused instead of silently dropped.
- **XML after a transformer is written again** (`reorderXml`) because E4X appends elements at the end, and FHIR XML requires the order of the specification.
- **In the Administrator** (`FhirEngineLoader.isServer()` is false) validation is skipped and HAPI is never loaded.

## The isolated FHIR engine

HAPI FHIR brings Jackson, Guava, commons-* and more, often in other versions than the engine's own `server-lib`. To avoid conflicts, HAPI never touches the engine's classpath.

- **`FhirEngine`** is the interface the data type uses. Only JDK types (`String`, `Map`, `List`) cross it.
- **`HapiFhirEngine`** implements it, in `lib/datatype-fhir-engine.jar`. It is not listed in `plugin.xml`, so the engine never loads it.
- **`FhirEngineLoader`** finds `lib/` next to the shared jar, creates a child-first `URLClassLoader` over all jars in it, and instantiates `HapiFhirEngine` once.

The class loader looks in `lib/` first, except for these prefixes, which always come from the parent:

| Prefix | Why |
| --- | --- |
| `java.`, `javax.xml.`, `jdk.`, `sun.`, `com.sun.`, `org.w3c.`, `org.xml.` | The JDK. |
| `org.apache.logging.log4j.` | The engine's Log4j, so HAPI's logging ends up in the server log. |
| `FhirEngine` | The interface itself, otherwise the cast fails. |

`getResources` returns the engine's own resources first (the FHIR specification, `META-INF/services` files) and does not consult the parent for service files.

HAPI looks up its parsers through the thread's context class loader, so every `HapiFhirEngine` method runs inside `withEngineClassLoader`.

If the engine cannot be loaded, the error is remembered and every call throws `IllegalStateException: The FHIR engine is not available (...)`; the serializer turns that into a message error such as *Cannot validate FHIR*.

System properties for development:

| Property | Effect |
| --- | --- |
| `fhir.engine.direct=true` | Load `HapiFhirEngine` from the normal classpath instead of `lib/`. The tests use this. |
| `fhir.engine.lib=<folder>` | Load the engine jars from another folder. |

## Validation

`HapiFhirEngine.build` creates a `FhirValidator` with this support chain, in this order:

1. `PrePopulatedValidationSupport` with the profiles from *Profile Packages*: `.tgz` NPM packages and single conformance resources (StructureDefinition, ValueSet, CodeSystem, ConceptMap, NamingSystem) in JSON or XML. Other resources in a package are skipped.
2. `DefaultProfileValidationSupport`: the FHIR R4 specification.
3. `CommonCodeSystemsTerminologyService`, `InMemoryTerminologyServerValidationSupport`, `SnapshotGeneratingValidationSupport`.
4. `RemoteTerminologyServiceValidationSupport`, only when a terminology server is set.
5. `UnknownCodeSystemWarningValidationSupport` with the *Unknown Code Systems* severity.

The instance validator refuses unknown profiles, accepts unknown extensions when the property says so, and ignores best-practice warnings (such as dom-6, "should have narrative"), which would flag nearly every message.

Building a validator takes seconds and about 300 MB of heap, so validators are cached per combination of options. The required profile is passed per call and is not part of the cache key. `FhirSerializer` starts building in a background thread (`warmUp`) when a channel with validation is deployed, so the first message does not wait as long.

Profile files are read once, when the validator is built: after changing them, restart the channel or the server.

## Properties and saved channels

Channels are stored with XStream, which **does not run constructors**. A property added in a new version is therefore `null` in a channel saved by an older version. The rules that follow from this:

- Properties that default to `true` are `Boolean`, not `boolean`; the getter returns the default when the field is `null`.
- Enum and String getters do the same (`getFhirVersion()` returns R4 when null).
- `FhirDataTypeProperties.getBatchProperties()` creates the batch properties for channels saved before 1.3.0.

### Adding a property

1. Add the field with its default to `FhirSerializationProperties` (or `FhirBatchProperties`), with a null-safe getter.
2. Add it to `getPropertyDescriptors()` (label and tooltip in the Swing Administrator) and `setProperties()`.
3. If the engine needs it, add an option key to `FhirEngine` and put it in `engineOptions()`. It then automatically becomes part of the validator cache key.
4. Add it to `webadmin/web/plugin.js`, with the same key, label, default and tooltip.
5. Document it in the README's property table.
6. Test that a channel saved with the previous version still deploys.

## Batch processing and responses

**Batch.** With *Process Batch* on, the engine asks `FhirDataTypeServerPlugin.getBatchAdaptorFactory` for an adaptor. `FhirBatchAdaptor` either splits with `FhirBundleSplitter` (*Bundle Entry*) or runs the user's split script (*JavaScript*, modelled on the engine's other batch adaptors). The source map variables (`fhirBundleType`, `fhirEntryIndex`, ...) are set in an overridden `getMessage()`, because the source connector copies the batch message's source map right after that call while `getNextMessage` already reads ahead.

**Responses.** For *Auto-generate* source responses, `FhirAutoResponder` returns an OperationOutcome in the format of the incoming message, and puts `fhirHttpStatus` (200, 400, 500) and `fhirContentType` in the channel map for the HTTP Listener.

## Code templates and the web administrator

The *FHIR Functions* templates are defined in `FhirDataTypeCodeTemplatePlugin.fhirTemplates()`. Each template and the library get a fixed id derived from their name, so importing a newer library replaces the templates instead of adding copies.

The web administrator has no plugin hook for reference items, so the same templates are shipped as a code template library: `examples/fhir-functions-code-templates.xml`, generated from `fhirLibrary()` with the engine's `ObjectXMLSerializer`. A test fails when the file is out of date. After changing a template:

```bash
mvn test -DupdateExamples=true
```

and commit the regenerated file. The tests also compile every snippet in Rhino 1.7.13 with E4X, so a syntax error in a template fails the build.

The web administrator's properties panel is `webadmin/web/plugin.js`. It is a static description of the same fields as `getPropertyDescriptors()`: keep both in sync by hand.

## Dependencies

HAPI FHIR pulls in far more than validating R4 needs. `pom.xml` excludes what was found unnecessary, which keeps the zip at about 80 MB. The comments in `pom.xml` list what was tried:

- **Excluded:** dstu2, dstu2016may, r4b, PlantUML, JGit, SQLite, Saxon, OGNL, xmlsec, commonmark, Apache httpclient (from `hapi-fhir-validation`), OkHttp/Okio/Kotlin.
- **Tried and needed:** dstu3 and the convertors (profiles are converted internally), Thymeleaf, ICU4J, nimbus-jose-jwt (Bundle validation), `hapi-fhir-client` with its HTTP client (terminology server).

When you change an exclusion, run all tests **and** validate a Bundle, a resource against a Nictiz package and a code against a terminology server in a real OIE: a missing class often only shows on one path.

Versions that must match the engine:

| Dependency | Rule |
| --- | --- |
| `log4j-slf4j2-impl` (`log4j.version`) | Equal to the engine's `server-lib/log4j`. It routes HAPI's SLF4J logging to the engine's Log4j; `log4j-api` and `log4j-core` are excluded and come from the engine. |
| `commons-lang3` (provided) | The engine's version, 3.20.0. HAPI's terminology client needs 3.18 or later. |
| Engine jars (`oie.version`) | The engine version you compile against. |

**Updating HAPI FHIR:** change `hapi.version`, run `mvn clean package`, and check the security advisories for HAPI FHIR and `org.hl7.fhir.core` (see the README's XXE section, which names the versions).

## Testing

```bash
mvn test
```

There are 54 tests (JUnit 4). They run without OIE: the engine jars are on the test classpath, and the tests that need HAPI set `fhir.engine.direct=true`, so HAPI is loaded from the Maven classpath instead of `lib/`.

| Test | Covers |
| --- | --- |
| `FhirSerializerTest` | Inbound and outbound conversion, validation, reject and accept, element order, DOCTYPE refusal. |
| `FhirXmlTest` | Format detection, JSON to XML, namespaces, `hasDoctype`. |
| `FhirToolsTest` | The script functions. |
| `FhirBundleSplitterTest`, `server/FhirBatchAdaptorTest` | Batch splitting and source map variables, using `examples/observation-bundle.json`. |
| `server/FhirAutoResponderTest` | OperationOutcome responses and HTTP status. |
| `client/FhirDataTypeCodeTemplatePluginTest` | Templates, their contexts, the snippets in Rhino, and the example library. |
| `TerminologyServerTest` | The terminology server option against `https://tx.fhir.org/r4`. Skipped when there is no network. |

Run tests from the repository root: some read files from `examples/`.

## Trying it in OIE

1. `mvn clean package`.
2. Install `target/datatype-fhir-<version>.zip` on a **test** server (*Settings > Extensions*), or unzip it into `<OIE_HOME>/extensions/`, and restart the service.
3. Give the server at least 1 GB of heap (`-Xmx1g` in `conf/custom.vmoptions`).
4. Use the messages in `examples/`; the 27 build.fhir.org Patient examples are a quick regression set (25 valid against R4, 2 rejected for R6-only elements).

The server log shows HAPI's warnings and errors. *The FHIR engine is not available* means `lib/` is missing or incomplete, usually an extension that was copied without its `lib` folder.

## Common changes

### Supporting another FHIR version (R4B, R5)

The design keeps this a local change:

1. Add the version to `FhirSerializationProperties.FhirVersion` (saved channels keep R4).
2. Add `hapi-fhir-structures-<version>` and `hapi-fhir-validation-resources-<version>` to `pom.xml`, and remove the matching exclusion (r4b is excluded today).
3. Map the version in `HapiFhirEngine.context()`.
4. `FhirTools.toJson` and `toXml` use `"R4"`; give them a version parameter or overload.
5. `FhirOperationOutcome` writes R4; check that the structure is the same in the new version.
6. Add the option to `webadmin/web/plugin.js` and the README.

### Changing what goes into a message

Keep the pure-Java paths (`FhirXml`, `FhirJson`) faithful: they must not reorder, add or drop anything, because users rely on the transformer seeing the message as it came in. Changes in order or content belong in the HAPI paths of `encode()`.

## Releasing

1. **Branch.** Work on a branch and open a pull request; reference the issue (`Closes #n`).
2. **Version.** Set the new version in all four places:
   - `pom.xml` (`<version>`)
   - `src/main/resources/plugin.xml` (`<pluginVersion>`)
   - `oie.json` (`"version"`)
   - `webadmin/plugin.json` (`"version"`)
3. **Engine versions.** `<mirthVersion>` in `plugin.xml` lists every OIE version the extension may load on, comma-separated (now `4.6.0,4.7.0`). OIE only loads an extension whose list contains its **exact** version, so a new OIE release needs a new plugin release. `minEngineVersion` in `oie.json` is the lowest supported version for the catalog.
4. **Build and test.** `mvn clean package`, then test the zip in OIE.
5. **Merge** the pull request.
6. **Release.** Create the release on GitHub with the zip and its SHA-256 in the notes:

   ```bash
   sha256sum target/datatype-fhir-1.4.2.zip
   gh release create v1.4.2 target/datatype-fhir-1.4.2.zip --target main \
     --title "1.4.2: also accepts OIE 4.7.0" --notes-file notes.md
   ```

   Release notes describe what changed for the user, how to upgrade (replace the extension, restart; channel settings are kept), and the SHA-256.
7. **Catalog.** Publishing the release triggers `catalog-pr.yml`, which files a pull request to the [OIE Community Store catalog](https://github.com/gibson9583/oie-community-catalog) from the fork `<owner>/oie-community-catalog`, using `oie.json` at the tag. It needs the repository secret `CATALOG_TOKEN` (a classic PAT with `public_repo`); without it the job is skipped. Close earlier catalog pull requests of this plugin that are still open.
8. **Issues.** New GitHub issues get a Jira issue through `jira-issue.yml` (variables `JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_PROJECT_KEY`, secret `JIRA_API_TOKEN`). Closing the GitHub issue does not close the Jira issue: set it to *Done* yourself.
9. **Documentation.** Update the README, `docs/`, and the [online course](https://eclipse-oie.moodiy.cloud/course/view.php?id=9) when behaviour changes.

## Conventions

- Every file in `src/main` starts with the MPL 2.0 header.
- Only JDK types cross the `FhirEngine` boundary; nothing outside `engine/` imports HAPI.
- Shared, server and client code stays at Java 11; only `engine/` may use Java 17.
- Error messages are written for the person running the channel: say what is wrong and what to do, for example *"FHIR XML must not contain a DOCTYPE (DTD) declaration."*
- Comments explain *why*, not *what*; keep them short.
- Every change comes with a test; run `mvn clean package` before you push.
