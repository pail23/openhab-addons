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
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_LED;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_MAX_TARGET_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_MIN_TARGET_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_POWER1;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_POWER2;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_POWER3;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_POWER4;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_TARGET_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_TEMPERATURE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_THERMOSTAT_MODE;
import static org.openhab.binding.dingz.internal.DingzBindingConstants.CHANNEL_THERMOSTAT_OUTPUT;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;
import static org.openhab.core.library.unit.Units.WATT;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.core.cache.ExpiringCache;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
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

    private static class DingzLedReport {

        public String mode = "";
        public boolean on;
        public String rgb = "";
        public String hsv = "";
    }

    private static class DingzSetLedReport {

        public String color = "";
        public boolean on;
        public String mode = "";
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
        public DingzLedReport led = new DingzLedReport();
        public DingzThermostatReport thermostat = new DingzThermostatReport();
    }

    private static final int HTTP_OK_CODE = 200;
    private static final String COMMUNICATION_ERROR = "Error while communicating to the dingz switch: ";
    private static final String HTTP_REQUEST_URL_PREFIX = "http://";
    private static final String THERMOSTAT_CALL = "api/v1/thermostat";
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
                switch (channelUID.getId()) {
                    case CHANNEL_TARGET_TEMPERATURE:
                        if (command instanceof Number) {
                            Number value = (Number) command;
                            String response = sendHttpPost(THERMOSTAT_CALL,
                                    RequestParameter.createParameter("target_temp", value.floatValue()));
                            DingzThermostatReport report = gson.fromJson(response, DingzThermostatReport.class);
                            if (report != null) {
                                updateThermostat(report);
                            }
                        }
                        break;
                    case CHANNEL_LED:
                        RequestParameter parameters = null;
                        if (command instanceof HSBType) {
                            HSBType value = (HSBType) command;
                            String color = String.format("%d;%d;%d", value.getHue().intValue(),
                                    value.getSaturation().intValue(), value.getBrightness().intValue());
                            String action = value.getBrightness().intValue() > 0 ? "on" : "off";

                            parameters = RequestParameter.createParameter("action", action).add("color", color)
                                    .add("mode", "hsv");
                        } else if (command instanceof OnOffType) {
                            parameters = RequestParameter.createParameter("action",
                                    command.equals(OnOffType.ON) ? "on" : "off");
                        }
                        if (parameters != null) {
                            String response = sendHttpPostWithContent("api/v1/led/set", parameters);

                            DingzSetLedReport report = gson.fromJson(response, DingzSetLedReport.class);
                            if (report != null) {
                                HSBType hsbType = new HSBType(report.color.replace(';', ','));
                                if (!report.on) {
                                    hsbType = new HSBType(hsbType.getHue(), hsbType.getSaturation(),
                                            new PercentType(0));
                                }
                                updateState(CHANNEL_LED, hsbType);
                            }
                        }
                        break;
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
            if (stateReport.led != null) {
                updateLED(stateReport.led);
            }
        }
    }

    private void updateThermostat(DingzThermostatReport report) {
        updateState(CHANNEL_THERMOSTAT_OUTPUT, report.on ? OnOffType.ON : OnOffType.OFF);
        updateState(CHANNEL_THERMOSTAT_MODE, StringType.valueOf(report.mode));
        updateState(CHANNEL_TARGET_TEMPERATURE, QuantityType.valueOf(report.target_temp, CELSIUS));
        updateState(CHANNEL_MIN_TARGET_TEMPERATURE, QuantityType.valueOf(report.min_target_temp, CELSIUS));
        updateState(CHANNEL_MAX_TARGET_TEMPERATURE, QuantityType.valueOf(report.max_target_temp, CELSIUS));
    }

    private void updateSensor(DingzSensorReport report) {
        updateState(CHANNEL_TEMPERATURE, QuantityType.valueOf(report.room_temperature, CELSIUS));
        updateState(CHANNEL_BRIGHTNESS, new DecimalType(report.brightness));

        updateState(CHANNEL_POWER1, QuantityType.valueOf(report.power_outputs[0].value, WATT));
        updateState(CHANNEL_POWER2, QuantityType.valueOf(report.power_outputs[1].value, WATT));
        updateState(CHANNEL_POWER3, QuantityType.valueOf(report.power_outputs[2].value, WATT));
        updateState(CHANNEL_POWER4, QuantityType.valueOf(report.power_outputs[3].value, WATT));
    }

    private void updateLED(DingzLedReport report) {
        if (report.mode.equals("hsv")) {
            HSBType hsbType = new HSBType(report.hsv.replace(';', ','));
            updateState(CHANNEL_LED, hsbType);
        }
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
        Request request = httpClient.newRequest(url).timeout(10, TimeUnit.SECONDS).method(HttpMethod.GET);
        return sendHttpRequest(request);
    }

    private String sendHttpPost(String action, RequestParameter parameter) throws DingzException {
        String url = hostname + "/" + action + "?" + parameter.toString();
        Request request = httpClient.newRequest(url).timeout(10, TimeUnit.SECONDS).method(HttpMethod.POST);
        return sendHttpRequest(request);
    }

    private String sendHttpPostWithContent(String action, RequestParameter parameter) throws DingzException {
        String url = hostname + "/" + action;
        StringContentProvider provider = new StringContentProvider(parameter.toString());

        Request request = httpClient.newRequest(url).timeout(10, TimeUnit.SECONDS).method(HttpMethod.POST)
                .content(provider);
        return sendHttpRequest(request);
    }

    private String sendHttpRequest(Request request) throws DingzException {
        ContentResponse response = null;
        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new DingzException(COMMUNICATION_ERROR + e.getMessage());
        }

        if (response.getStatus() != HTTP_OK_CODE) {
            throw new DingzException("Error sending HTTP request to " + request.getPath() + ". Got response code: "
                    + response.getStatus());
        }
        return response.getContentAsString();
    }
}
