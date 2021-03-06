// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import com.yahoo.log.LogLevel;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Trace exporter which dumps traces and their description as warning-entries in the Vespa log.
 */
public class LoggingTraceExporter implements TraceExporter {

    private static final Logger log = Logger.getLogger(LoggingTraceExporter.class.getName());

    @Override
    public void maybeExport(Supplier<TraceDescription> traceDescriptionSupplier) {
        var traceDescription = traceDescriptionSupplier.get();
        if (traceDescription.getTrace() != null) {
            log.log(LogLevel.WARNING, String.format("%s: %s", traceDescription.getDescription(),
                    traceDescription.getTrace().toString()));
        }
    }

}
