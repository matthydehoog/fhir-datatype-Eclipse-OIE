/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which validator options are still in use, so the engine can drop the validators (and the FHIR
 * specifications, R5 alone is about 500 MB) of channels that were undeployed or changed.
 *
 * A serializer holds its options for as long as it lives; the engine lets go of the serializers of
 * an undeployed channel, so they disappear from the weak map at the next garbage collection.
 * Callers without a serializer, such as FhirTools in a script, keep their validator for
 * {@link #IDLE_MILLIS} after their last call.
 */
final class FhirValidatorUsage {

    static final long IDLE_MILLIS = 15 * 60 * 1000L;

    private static final long CHECK_INTERVAL_MILLIS = 60 * 1000L;

    private static final Map<Object, Map<String, String>> OWNERS = new WeakHashMap<>();

    private static long lastCheck = System.currentTimeMillis();

    private FhirValidatorUsage() {}

    static synchronized void register(Object owner, Map<String, String> options) {
        OWNERS.put(owner, options);
    }

    static synchronized List<Map<String, String>> inUse() {
        return new ArrayList<>(OWNERS.values());
    }

    /** Lets the engine drop what is no longer used; does the work at most once a minute. */
    static void release(FhirEngine engine) {
        synchronized (FhirValidatorUsage.class) {
            long now = System.currentTimeMillis();
            if (now - lastCheck < CHECK_INTERVAL_MILLIS) {
                return;
            }
            lastCheck = now;
        }
        releaseNow(engine, IDLE_MILLIS);
    }

    static void releaseNow(FhirEngine engine, long idleMillis) {
        engine.retainValidators(inUse(), idleMillis);
    }
}
