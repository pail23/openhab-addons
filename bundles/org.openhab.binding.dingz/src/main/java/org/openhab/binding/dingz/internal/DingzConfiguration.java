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

import static org.openhab.binding.dingz.internal.DingzBindingConstants.DEFAULT_REFRESH_RATE_SECONDS;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link DingzConfiguration} class contains fields mapping thing
 * configuration parameters.
 *
 * @author Paul Frank - Initial contribution
 */
@NonNullByDefault
public class DingzConfiguration {

    /**
     * Hostname of the myStrom device.
     */
    public String hostname = "localhost";
    /**
     * Number of seconds in between refreshes from the myStrom device.
     */
    public int refresh = DEFAULT_REFRESH_RATE_SECONDS;
}
