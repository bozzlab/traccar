/*
 * Copyright 2016 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.events;

import java.util.Collection;
import java.util.Collections;

import org.traccar.BaseEventHandler;
import org.traccar.Context;
import org.traccar.model.Device;
import org.traccar.model.DeviceState;
import org.traccar.model.Event;
import org.traccar.model.Position;

public class OverspeedEventHandler extends BaseEventHandler {

    public static final String ATTRIBUTE_SPEED_LIMIT = "speedLimit";

    private boolean notRepeat;
    private long minimalDuration;

    public OverspeedEventHandler() {
        notRepeat = Context.getConfig().getBoolean("event.overspeed.notRepeat");
        minimalDuration = Context.getConfig().getLong("event.overspeed.minimalDuration") * 1000;
    }

    public static Event updateOverspeedState(DeviceState deviceState, Position position, double speedLimit,
            long minimalOverspeedDuration, boolean notRepeat) {
        Event result = null;

        Boolean oldOverspeed = deviceState.getOverspeedState();

        long currentTime = position.getFixTime().getTime();
        boolean newOverspeed = position.getSpeed() > speedLimit;
        if (newOverspeed && !oldOverspeed) {
            if (deviceState.getOverspeedPosition() == null) {
                deviceState.setOverspeedPosition(position);
            }
        } else if (oldOverspeed && !newOverspeed) {
            deviceState.setOverspeedState(false);
            deviceState.setOverspeedPosition(null);
        } else {
            deviceState.setOverspeedPosition(null);
        }
        Position overspeedPosition = deviceState.getOverspeedPosition();
        if (overspeedPosition != null) {
            long overspeedTime = overspeedPosition.getFixTime().getTime();
            if (newOverspeed && overspeedTime + minimalOverspeedDuration <= currentTime) {
                result = new Event(Event.TYPE_DEVICE_OVERSPEED, overspeedPosition.getDeviceId(),
                        overspeedPosition.getId());
                result.set("speed", overspeedPosition.getSpeed());
                result.set(ATTRIBUTE_SPEED_LIMIT, speedLimit);
                deviceState.setOverspeedState(notRepeat);
                deviceState.setOverspeedPosition(null);
            }
        }
        return result;
    }

    @Override
    protected Collection<Event> analyzePosition(Position position) {

        long deviceId = position.getDeviceId();
        Device device = Context.getIdentityManager().getById(deviceId);
        if (device == null) {
            return null;
        }
        if (!Context.getIdentityManager().isLatestPosition(position) || !position.getValid()) {
            return null;
        }

        double speedLimit = Context.getDeviceManager().lookupAttributeDouble(deviceId, ATTRIBUTE_SPEED_LIMIT, 0, false);
        if (speedLimit == 0) {
            return null;
        }

        Event result = null;
        DeviceState deviceState = Context.getDeviceManager().getDeviceState(deviceId);

        if (deviceState.getOverspeedState() == null) {
            deviceState.setOverspeedState(position.getSpeed() > speedLimit);
        } else {
            result = updateOverspeedState(deviceState, position, speedLimit, minimalDuration, notRepeat);
        }

        Context.getDeviceManager().setDeviceState(deviceId, deviceState);
        if (result != null) {
            return Collections.singleton(result);
        }
        return null;
    }

}
