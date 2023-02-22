package com.jiangdg.ausbc.callback

import android.hardware.usb.UsbDevice

interface IDeviceStatusCallBack {
    fun onConnectDev(device: UsbDevice?)
    fun onDisConnectDev(device: UsbDevice?)
}