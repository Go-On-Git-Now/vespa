// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

/**
 * Exception that will create a http response with NOT_FOUND response code (404)
 *
 * @author hmusum
 * @since 5.1.17
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

}

