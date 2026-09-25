/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.mirth.connect.plugins.datatypes.fhir.FhirSerializationProperties.FailOn;

/** The outcome of validating one message: its issues and whether they make it invalid. */
public final class FhirValidation {

    private final List<Map<String, String>> issues;
    private final boolean invalid;

    FhirValidation(List<Map<String, String>> issues, FailOn failOn) {
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        boolean bad = false;
        for (Map<String, String> issue : issues) {
            if (counts(issue.get(FhirEngine.SEVERITY), failOn)) {
                bad = true;
                break;
            }
        }
        this.invalid = bad;
    }

    public List<Map<String, String>> getIssues() {
        return issues;
    }

    public boolean isValid() {
        return !invalid;
    }

    /** Errors and warnings; information messages are left out. */
    public List<Map<String, String>> getProblems() {
        List<Map<String, String>> problems = new ArrayList<>();
        for (Map<String, String> issue : issues) {
            if (!"information".equals(issue.get(FhirEngine.SEVERITY))) {
                problems.add(issue);
            }
        }
        return problems;
    }

    /** One line per error and warning: "error Patient.gender (line 5, column 3): message". */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> issue : getProblems()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(issue.get(FhirEngine.SEVERITY)).append(' ').append(issue.get(FhirEngine.LOCATION));
            String line = issue.get(FhirEngine.LINE);
            if (line != null && !line.isEmpty()) {
                sb.append(" (line ").append(line).append(", column ").append(issue.get(FhirEngine.COLUMN)).append(')');
            }
            sb.append(": ").append(issue.get(FhirEngine.MESSAGE));
        }
        return sb.toString();
    }

    private static boolean counts(String severity, FailOn failOn) {
        if ("fatal".equals(severity) || "error".equals(severity)) {
            return true;
        }
        return failOn == FailOn.Warnings && "warning".equals(severity);
    }
}
