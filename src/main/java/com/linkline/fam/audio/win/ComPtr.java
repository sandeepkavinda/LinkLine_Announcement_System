package com.linkline.fam.audio.win;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.PointerByReference;

/**
 * Thin wrapper around a raw COM interface pointer. Methods are called by vtable index, which keeps
 * the JNA surface small: IUnknown occupies slots 0-2 (QueryInterface, AddRef, Release) and each
 * interface's own methods follow in declaration order.
 */
final class ComPtr implements AutoCloseable {

    private final Pointer ptr;
    private boolean released;

    ComPtr(Pointer ptr) {
        if (ptr == null) {
            throw new IllegalArgumentException("null COM pointer");
        }
        this.ptr = ptr;
    }

    /** Calls the method at {@code slot}; returns the HRESULT. */
    int invoke(int slot, Object... args) {
        Pointer vtbl = ptr.getPointer(0);
        Pointer fn = vtbl.getPointer((long) slot * Native.POINTER_SIZE);
        Function f = Function.getFunction(fn, Function.ALT_CONVENTION);
        Object[] all = new Object[args.length + 1];
        all[0] = ptr;
        System.arraycopy(args, 0, all, 1, args.length);
        return f.invokeInt(all);
    }

    /** Like {@link #invoke} but throws on a failure HRESULT. */
    int check(String operation, int slot, Object... args) {
        int hr = invoke(slot, args);
        if (hr < 0) {
            throw new ComException(operation, hr);
        }
        return hr;
    }

    /** Calls a method whose last parameter is an out-pointer to another interface. */
    ComPtr getInterface(String operation, int slot, Object... argsBeforeOut) {
        PointerByReference out = new PointerByReference();
        Object[] args = new Object[argsBeforeOut.length + 1];
        System.arraycopy(argsBeforeOut, 0, args, 0, argsBeforeOut.length);
        args[argsBeforeOut.length] = out;
        check(operation, slot, args);
        return new ComPtr(out.getValue());
    }

    ComPtr queryInterface(Guid.GUID iid) {
        return getInterface("QueryInterface", 0, iid);
    }

    @Override
    public void close() {
        if (!released) {
            released = true;
            try {
                invoke(2); // IUnknown::Release
            } catch (RuntimeException ignored) {
                // nothing sensible to do if releasing fails
            }
        }
    }
}
