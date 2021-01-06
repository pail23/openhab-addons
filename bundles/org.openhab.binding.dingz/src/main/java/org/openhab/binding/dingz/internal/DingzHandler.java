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

import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_BRIGHTNESS;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_MAX_TARGET_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_MIN_TARGET_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_TARGET_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_THERMOSTAT_MODE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_THERMOSTAT_OUTPUT;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.core.cache.ExpiringCache;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link DingzHandler} is responsible for handling commands, which are sent
 * to one of the channels.
 *
 * @author Paul Frank - Initial contribution
 */
@NonNullByDefault
public class DingzHandler extends BaseThingHandler {

    private static class DingzThermostatReport {

        public float target_temp;
        public String mode = "";
        public boolean on;
        public float temp;
        public int min_target_temp;
        public int max_target_temp;
    }

    private static class PowerValueReport {
        public float value;
    }

    private static class DingzSensorReport {

        public int brightness;
        public String light_state = "";
        public float room_temperature;
        public PowerValueReport[] power_outputs = new PowerValueReport[0];
    }

    private static class DingzStateReport {
        public DingzSensorReport sensors = new DingzSensorReport();
        public DingzThermostatReport thermostat = new DingzThermostatReport();
    }

    private static final int HTTP_OK_CODE = 200;
    private static final String COMMUNICATION_ERROR = "Error while communicating to the myStrom plug: ";
    private static final String HTTP_REQUEST_URL_PREFIX = "http://";
    private static final String THERMOSTAT_CALL = "api/v1/thermostat";
    private static final String SENSORS_CALL = "api/v1/sensors";
    private static final String STATE_CALL = "api/v1/state";

    private final Logger logger = LoggerFactory.getLogger(DingzHandler.class);

    private HttpClient httpClient;
    private String hostname = "";

    private @Nullable ScheduledFuture<?> pollingJob;

    private ExpiringCache<DingzStateReport> stateCache = new ExpiringCache<>(Duration.ofSeconds(3),
            this::getStateValues);
    private final Gson gson = new Gson();

    public DingzHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            if (command instanceof RefreshType) {
                pollDevice();
            } else {
                if (CHANNEL_TARGET_TEMPERATURE.equals(channelUID.getId()) && command instanceof Number) {
                    Number value = (Number) command;
                    Float floatValue = value.floatValue();
                    String response = sendHttpPost(THERMOSTAT_CALL, "target_temp", floatValue.toString());
                    DingzThermostatReport report = gson.fromJson(response, DingzThermostatReport.class);
                    if (report != null) {
                        updateThermostat(report);
                    }
                    scheduler.schedule(this::pollDevice, 500, TimeUnit.MILLISECONDS);
                }
            }
        } catch (DingzException e) {
            logger.warn("Error while handling command {}", e.getMessage());
        }
    }

    private @Nullable DingzStateReport getStateValues() {
        try {
            String returnContent = sendHttpGet(STATE_CALL);
            DingzStateReport report = gson.fromJson(returnContent, DingzStateReport.class);
            updateStatus(ThingStatus.ONLINE);
            return report;
        } catch (DingzException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
            return null;
        }
    }

    private void pollDevice() {
        DingzStateReport stateReport = stateCache.getValue();
        if (stateReport != null) {
            if (stateReport.sensors != null) {
                updateSensor(stateReport.sensors);
            }
            if (stateReport.thermostat != null) {
                updateThermostat(stateReport.thermostat);
            }
        }
    }

    private void updateThermostat(DingzThermostatReport report) {
        updateState(CHANNEL_THERMOSTAT_OUTPUT, report.on ? OnOffType.ON : OnOffType.OFF);
        updateState(CHANNEL_THERMOSTAT_MODE, StringType.valueOf(report.mode));
        // updateState(CHANNEL_TEMPERATURE, QuantityType.valueOf(report.temp, CELSIUS));
        updateState(CHANNEL_TARGET_TEMPERATURE, QuantityType.valueOf(report.target_temp, CELSIUS));
        updateState(CHANNEL_MIN_TARGET_TEMPERATURE, QuantityType.valueOf(report.min_target_temp, CELSIUS));
        updateState(CHANNEL_MAX_TARGET_TEMPERATURE, QuantityType.valueOf(report.max_target_temp, CELSIUS));
    }

    private void updateSensor(DingzSensorReport report) {
        updateState(CHANNEL_TEMPERATURE, QuantityType.valueOf(report.room_temperature, CELSIUS));
        updateState(CHANNEL_BRIGHTNESS, new DecimalType(report.brightness));
    }

    @Override
    public void initialize() {
        DingzConfiguration config = getConfigAs(DingzConfiguration.class);
        this.hostname = HTTP_REQUEST_URL_PREFIX + config.hostname;

        updateStatus(ThingStatus.UNKNOWN);
        pollingJob = scheduler.scheduleWithFixedDelay(this::pollDevice, 0, config.refresh, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        if (pollingJob != null) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
        super.dispose();
    }

    /**
     * Given a URL and a set parameters, send a HTTP GET request to the URL location
     * created by the URL and parameters.
     *
     * @param url The URL to send a GET request to.
     * @return String contents of the response for the GET request.
     * @throws Exception
     */
    public String sendHttpGet(String action) throws DingzException {
        String url = hostname + "/" + action;
        ContentResponse response = null;
        try {
            response = httpClient.newRequest(url).timeout(10, TimeUnit.SECONDS).method(HttpMethod.GET).send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new DingzException(COMMUNICATION_ERROR + e.getMessage());
        }

        if (response.getStatus() != HTTP_OK_CODE) {
            throw new DingzException(
                    "Error sending HTTP GET request to " + url + ". Got response code: " + response.getStatus());
        }
        return response.getContentAsString();
    }

    private String sendHttpPost(String action, String parameter, String value) throws DingzException {
        String url = hostname + "/" + action + "?" + parameter + "=" + value;
        ContentResponse response = null;
        try {
            response = httpClient.newRequest(url).timeout(10, TimeUnit.SECONDS).method(HttpMethod.POST).send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new DingzException(COMMUNICATION_ERROR + e.getMessage());
        }

        if (response.getStatus() != HTTP_OK_CODE) {
            throw new DingzException(
                    "Error sending HTTP POST request to " + url + ". Got response code: " + response.getStatus());
        }
        return response.getContentAsString();
    }
}
