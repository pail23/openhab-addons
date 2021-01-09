/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.dingz.internal;

public class RequestParameter {
    private String payload = "";

    private RequestParameter(String payload) {
        this.payload = payload;
    }

    public RequestParameter add(String name, String value) {
        return new RequestParameter(getPayload() + "&" + name + "=" + value);
    }

    public static RequestParameter createParameter(String name, String value) {
        return new RequestParameter(String.format("%s=%s", name, value));
    }

    public static RequestParameter createParameter(String name, float value) {
        return new RequestParameter(String.format("%s=%f", name, value));
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return payload;
    }
}
