package com.linkline.fam.audio.win;

import com.linkline.fam.audio.AudioDevice;
import com.linkline.fam.audio.DeviceCatalog;
import com.linkline.fam.audio.DeviceState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lists Windows audio output endpoints through IMMDeviceEnumerator. */
public final class WindowsDeviceCatalog implements DeviceCatalog {

    @Override
    public List<AudioDevice> listOutputs() throws Exception {
        return ComThread.call(this::enumerate);
    }

    @Override
    public String description() {
        return "Windows Core Audio (IMMDeviceEnumerator)";
    }

    private List<AudioDevice> enumerate() {
        List<AudioDevice> result = new ArrayList<>();
        try (ComPtr enumerator = CoreAudio.createEnumerator()) {
            String defaultId = CoreAudio.defaultRenderId(enumerator);
            try (ComPtr devices = enumerator.getInterface("EnumAudioEndpoints",
                    CoreAudio.ENUM_ENUM_AUDIO_ENDPOINTS, CoreAudio.E_RENDER, CoreAudio.DEVICE_STATE_ALL)) {
                int n = CoreAudio.count(devices, CoreAudio.COLL_GET_COUNT);
                for (int i = 0; i < n; i++) {
                    try (ComPtr dev = devices.getInterface("IMMDeviceCollection::Item", CoreAudio.COLL_ITEM, i)) {
                        String id = CoreAudio.deviceId(dev);
                        String name = CoreAudio.friendlyName(dev);
                        DeviceState state = toState(CoreAudio.deviceState(dev));
                        result.add(new AudioDevice(id, name == null ? id : name, Objects.equals(id, defaultId), state));
                    } catch (ComException e) {
                        // skip a single broken endpoint rather than failing the whole list
                    }
                }
            }
        }
        return result;
    }

    private static DeviceState toState(int state) {
        switch (state) {
            case CoreAudio.DEVICE_STATE_ACTIVE:
                return DeviceState.ACTIVE;
            case CoreAudio.DEVICE_STATE_DISABLED:
                return DeviceState.DISABLED;
            case CoreAudio.DEVICE_STATE_NOTPRESENT:
                return DeviceState.NOT_PRESENT;
            case CoreAudio.DEVICE_STATE_UNPLUGGED:
                return DeviceState.UNPLUGGED;
            default:
                return DeviceState.UNKNOWN;
        }
    }
}
