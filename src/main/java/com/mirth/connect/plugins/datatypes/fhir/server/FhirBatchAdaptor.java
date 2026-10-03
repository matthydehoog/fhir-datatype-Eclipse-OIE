/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * The JavaScript split is modelled on the batch adaptors of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.fhir.server;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptorFactory;
import com.mirth.connect.donkey.server.message.batch.BatchMessageException;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReceiver;
import com.mirth.connect.plugins.datatypes.fhir.FhirBatchProperties;
import com.mirth.connect.plugins.datatypes.fhir.FhirBatchProperties.SplitType;
import com.mirth.connect.plugins.datatypes.fhir.FhirBundleSplitter;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ScriptController;
import com.mirth.connect.server.message.DebuggableBatchAdaptor;
import com.mirth.connect.server.message.DebuggableBatchAdaptorFactory;
import com.mirth.connect.server.userutil.SourceMap;
import com.mirth.connect.server.util.CompiledScriptCache;
import com.mirth.connect.server.util.javascript.JavaScriptExecutorException;
import com.mirth.connect.server.util.javascript.JavaScriptScopeUtil;
import com.mirth.connect.server.util.javascript.JavaScriptTask;
import com.mirth.connect.server.util.javascript.JavaScriptUtil;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Bundle Entry: reads the whole Bundle and returns its entries one by one; JavaScript: a custom
 * split script, as in the other data types.
 */
public class FhirBatchAdaptor extends DebuggableBatchAdaptor {
    private final Logger logger = LogManager.getLogger(this.getClass());
    private BufferedReader bufferedReader;
    private List<FhirBundleSplitter.Part> parts;
    private Map<String, Object> batchSourceMap;

    public FhirBatchAdaptor(BatchAdaptorFactory factory, SourceConnector sourceConnector, BatchRawMessage batchRawMessage) {
        super(factory, sourceConnector, batchRawMessage);
    }

    @Override
    public void cleanup() throws BatchMessageException {}

    /**
     * The source connector copies the batch's source map for every message right after this call,
     * so this is where the entry's fullUrl and request go in. Not in getNextMessage: with look-ahead
     * that already reads the next entry.
     */
    @Override
    public String getMessage() throws BatchMessageException {
        String message = super.getMessage();
        if (message != null && parts != null) {
            int index = getBatchSequenceId() - 1;
            if (index >= 0 && index < parts.size()) {
                Map<String, Object> sourceMap = new HashMap<String, Object>(batchSourceMap);
                sourceMap.putAll(parts.get(index).getSourceMap());
                batchRawMessage.setSourceMap(sourceMap);
            }
        }
        return message;
    }

    @Override
    protected String getNextMessage(int batchSequenceId) throws Exception {
        if (batchRawMessage.getBatchMessageSource() instanceof BatchMessageReader) {
            if (batchSequenceId == 1) {
                BatchMessageReader batchMessageReader = (BatchMessageReader) batchRawMessage.getBatchMessageSource();
                bufferedReader = new BufferedReader(batchMessageReader.getReader());
            }
            return getMessageFromReader(batchSequenceId);
        } else if (batchRawMessage.getBatchMessageSource() instanceof BatchMessageReceiver) {
            return getMessageFromReceiver((BatchMessageReceiver) batchRawMessage.getBatchMessageSource());
        }

        return null;
    }

    private String getMessageFromReceiver(BatchMessageReceiver batchMessageReceiver) throws Exception {
        byte[] bytes = null;

        if (batchMessageReceiver.canRead()) {
            try {
                bytes = batchMessageReceiver.readBytes();
            } finally {
                batchMessageReceiver.readCompleted();
            }

            if (bytes != null) {
                return batchMessageReceiver.getStringFromBytes(bytes);
            }
        }
        return null;
    }

    private String getMessageFromReader(int batchSequenceId) throws Exception {
        FhirBatchProperties batchProperties = (FhirBatchProperties) getBatchProperties();
        SplitType splitType = batchProperties != null ? batchProperties.getSplitType() : SplitType.Bundle_Entry;

        if (splitType == SplitType.Bundle_Entry) {
            if (parts == null) {
                batchSourceMap = batchRawMessage.getSourceMap() != null ? new HashMap<String, Object>(batchRawMessage.getSourceMap()) : new HashMap<String, Object>();
                parts = FhirBundleSplitter.split(readAll(bufferedReader));
                if (parts.isEmpty()) {
                    logger.warn("The FHIR batch has no entries with a resource; no messages were created.");
                }
            }
            int index = batchSequenceId - 1;
            return index < parts.size() ? parts.get(index).getMessage() : null;
        } else if (splitType == SplitType.JavaScript) {
            return getMessageFromScript(batchProperties);
        } else {
            throw new BatchMessageException("No valid batch splitting method configured");
        }
    }

    private static String readAll(Reader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int n;
        while ((n = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, n);
        }
        return sb.toString();
    }

    private String getMessageFromScript(FhirBatchProperties batchProperties) throws Exception {
        if (StringUtils.isEmpty(batchProperties.getBatchScript())) {
            throw new BatchMessageException("No batch script was set.");
        }

        try {
            final String batchScriptId = ScriptController.getScriptId(ScriptController.BATCH_SCRIPT_KEY, sourceConnector.getChannelId());
            final Boolean debug = ((DebuggableBatchAdaptorFactory) getFactory()).isDebug();
            MirthContextFactory contextFactory = getContextFactoryAndRecompile(ControllerFactory.getFactory().createContextFactoryController(), debug, batchScriptId, batchProperties.getBatchScript());

            triggerDebug(debug);

            String result = JavaScriptUtil.execute(new JavaScriptTask<String>(contextFactory, "FHIR Batch Adaptor", sourceConnector) {
                @Override
                public String doCall() throws Exception {
                    Script compiledScript = CompiledScriptCache.getInstance().getCompiledScript(batchScriptId);

                    if (compiledScript == null) {
                        logger.error("Batch script could not be found in cache");
                        return null;
                    } else {
                        Logger scriptLogger = LogManager.getLogger(ScriptController.BATCH_SCRIPT_KEY.toLowerCase());

                        try {
                            Scriptable scope = JavaScriptScopeUtil.getBatchProcessorScope(getContextFactory(), scriptLogger, sourceConnector.getChannelId(), sourceConnector.getChannel().getName(), getScopeObjects(bufferedReader));
                            return (String) Context.jsToJava(executeScript(compiledScript, scope), String.class);
                        } finally {
                            Context.exit();
                        }
                    }
                }
            });

            return StringUtils.isEmpty(result) ? null : result;
        } catch (JavaScriptExecutorException e) {
            logger.error(e.getCause());
        } catch (Throwable e) {
            logger.error(e);
        }

        return null;
    }

    private Map<String, Object> getScopeObjects(Reader in) {
        Map<String, Object> scopeObjects = new HashMap<String, Object>();

        // Provide the reader in the scope
        scopeObjects.put("reader", in);

        scopeObjects.put("sourceMap", new SourceMap(Collections.unmodifiableMap(batchRawMessage.getSourceMap())));

        return scopeObjects;
    }
}
