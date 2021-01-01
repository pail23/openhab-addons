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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link DingzBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Paul Frank - Initial contribution
 */
@NonNullByDefault
public class DingzBindingConstants {

    public static final int DEFAULT_REFRESH_RATE_SECONDS = 10;

    private static final String BINDING_ID = "dingz";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SWITCH = new ThingTypeUID(BINDING_ID, "dingzwlanswitch");

    // List of all Channel ids
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_TARGET_TEMPERATURE = "target-temperature";
    public static final String CHANNEL_THERMOSTAT_MODE = "thermostat-mode";
    public static final String CHANNEL_THERMOSTAT_OUTPUT = "thermostat-output";
}
