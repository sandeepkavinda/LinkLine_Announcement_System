package com.linkline.fam.audio.win;

import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/** GUIDs, vtable slots and small helpers for the Windows Core Audio (MMDevice / WASAPI) interfaces. */
final class CoreAudio {

    static final Guid.GUID CLSID_MMDEVICE_ENUMERATOR = new Guid.GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}");
    static final Guid.GUID IID_IMMDEVICE_ENUMERATOR = new Guid.GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}");
    static final Guid.GUID IID_IAUDIO_SESSION_MANAGER2 = new Guid.GUID("{77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F}");
    static final Guid.GUID IID_IAUDIO_SESSION_CONTROL2 = new Guid.GUID("{BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D}");
    static final Guid.GUID IID_ISIMPLE_AUDIO_VOLUME = new Guid.GUID("{87CE5498-68D6-44E5-9215-6DA47EF883D8}");

    // PKEY_Device_FriendlyName = {A45C254E-DF1C-4EFD-8020-67D146A850E0}, 14
    private static final Guid.GUID FMTID_DEVICE_FRIENDLY_NAME = new Guid.GUID("{A45C254E-DF1C-4EFD-8020-67D146A850E0}");
    private static final int PID_DEVICE_FRIENDLY_NAME = 14;

    static final int CLSCTX_ALL = 0x17;
    static final int E_RENDER = 0;
    static final int E_CONSOLE = 0;
    static final int DEVICE_STATE_ACTIVE = 0x1;
    static final int DEVICE_STATE_DISABLED = 0x2;
    static final int DEVICE_STATE_NOTPRESENT = 0x4;
    static final int DEVICE_STATE_UNPLUGGED = 0x8;
    static final int DEVICE_STATE_ALL = 0xF;
    static final int AUDIO_SESSION_STATE_EXPIRED = 2;
    private static final short VT_LPWSTR = 31;

    // IMMDeviceEnumerator slots
    static final int ENUM_ENUM_AUDIO_ENDPOINTS = 3;
    static final int ENUM_GET_DEFAULT_ENDPOINT = 4;
    static final int ENUM_GET_DEVICE = 5;
    // IMMDeviceCollection slots
    static final int COLL_GET_COUNT = 3;
    static final int COLL_ITEM = 4;
    // IMMDevice slots
    static final int DEV_ACTIVATE = 3;
    static final int DEV_OPEN_PROPERTY_STORE = 4;
    static final int DEV_GET_ID = 5;
    static final int DEV_GET_STATE = 6;
    // IPropertyStore slot
    static final int PROPS_GET_VALUE = 5;
    // IAudioSessionManager2 slot
    static final int MGR_GET_SESSION_ENUMERATOR = 5;
    // IAudioSessionEnumerator slots
    static final int SENUM_GET_COUNT = 3;
    static final int SENUM_GET_SESSION = 4;
    // IAudioSessionControl / IAudioSessionControl2 slots
    static final int CTL_GET_STATE = 3;
    static final int CTL2_GET_SESSION_INSTANCE_ID = 13;
    static final int CTL2_GET_PROCESS_ID = 14;
    // ISimpleAudioVolume slots
    static final int VOL_SET_MASTER = 3;
    static final int VOL_GET_MASTER = 4;

    private CoreAudio() {
    }

    static void check(String operation, int hr) {
        if (hr < 0) {
            throw new ComException(operation, hr);
        }
    }

    static ComPtr createEnumerator() {
        PointerByReference out = new PointerByReference();
        WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                CLSID_MMDEVICE_ENUMERATOR, null, CLSCTX_ALL, IID_IMMDEVICE_ENUMERATOR, out);
        check("CoCreateInstance(MMDeviceEnumerator)", hr.intValue());
        return new ComPtr(out.getValue());
    }

    static int count(ComPtr collection, int slot) {
        IntByReference n = new IntByReference();
        collection.check("GetCount", slot, n);
        return n.getValue();
    }

    /** Reads a string the callee allocated with CoTaskMemAlloc, and frees it. */
    static String takeString(PointerByReference ref) {
        Pointer p = ref.getValue();
        if (p == null) {
            return null;
        }
        try {
            return p.getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(p);
        }
    }

    static String deviceId(ComPtr device) {
        PointerByReference out = new PointerByReference();
        device.check("IMMDevice::GetId", DEV_GET_ID, out);
        return takeString(out);
    }

    static int deviceState(ComPtr device) {
        IntByReference state = new IntByReference();
        device.check("IMMDevice::GetState", DEV_GET_STATE, state);
        return state.getValue();
    }

    static String friendlyName(ComPtr device) {
        try (ComPtr store = device.getInterface("IMMDevice::OpenPropertyStore", DEV_OPEN_PROPERTY_STORE, 0 /* STGM_READ */)) {
            Memory key = new Memory(20); // PROPERTYKEY: GUID (16 bytes) + DWORD pid
            key.clear();
            FMTID_DEVICE_FRIENDLY_NAME.write();
            key.write(0, FMTID_DEVICE_FRIENDLY_NAME.getPointer().getByteArray(0, 16), 0, 16);
            key.setInt(16, PID_DEVICE_FRIENDLY_NAME);

            Memory variant = new Memory(32); // PROPVARIANT is 16 bytes (x86) or 24 bytes (x64)
            variant.clear();
            int hr = store.invoke(PROPS_GET_VALUE, key, variant);
            if (hr < 0) {
                return null;
            }
            try {
                if (variant.getShort(0) == VT_LPWSTR) {
                    Pointer text = variant.getPointer(8);
                    return text == null ? null : text.getWideString(0);
                }
                return null;
            } finally {
                NativeLibrary.getInstance("ole32").getFunction("PropVariantClear").invokeInt(new Object[] {variant});
            }
        }
    }

    /** The default render endpoint's id, or null if the machine has no active output device. */
    static String defaultRenderId(ComPtr enumerator) {
        PointerByReference out = new PointerByReference();
        int hr = enumerator.invoke(ENUM_GET_DEFAULT_ENDPOINT, E_RENDER, E_CONSOLE, out);
        if (hr < 0 || out.getValue() == null) {
            return null;
        }
        try (ComPtr dev = new ComPtr(out.getValue())) {
            return deviceId(dev);
        }
    }

    static ComPtr activate(ComPtr device, Guid.GUID iid) {
        return device.getInterface("IMMDevice::Activate", DEV_ACTIVATE, iid, CLSCTX_ALL, null);
    }
}
