package com.linkline.fam.audio.win;

/** A COM call returned a failure HRESULT. */
public class ComException extends RuntimeException {

    private final int hresult;

    public ComException(String operation, int hresult) {
        super(operation + " failed (HRESULT 0x" + String.format("%08X", hresult) + ")");
        this.hresult = hresult;
    }

    public int hresult() {
        return hresult;
    }
}
