package com.jiangdg.demo

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IDeviceStatusCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.utils.SettableFuture
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import com.jiangdg.usb.USBMonitor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NewCameraView(context: Context) : FrameLayout(context), ICameraStateCallBack {
    private var mCameraView: IAspectRatio? = null
    private var mCameraClient: MultiCameraClient? = null
    private val mCameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()
    private var mCurrentCamera: SettableFuture<MultiCameraClient.ICamera>? = null

    private val mRequestPermission: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    init {
        inflate(context, R.layout.new_fragment_uvc_camera, this)
        val cameraView = AspectRatioTextureView(context)
        val cameraViewContainer = findViewById<ViewGroup>(R.id.fragment_container)
        if (cameraViewContainer != null) {
            cameraViewContainer.removeAllViews()
            cameraViewContainer.addView(
                cameraView,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
            )
        }
        handleTextureView(cameraView)
    }

    private fun handleTextureView(textureView: AspectRatioTextureView) {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Log.i(TAG, "handleTextureView onSurfaceTextureAvailable")
                registerMultiCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Log.i(TAG, "handleTextureView onSurfaceTextureSizeChanged")
                surfaceSizeChanged(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Log.i(TAG, "handleTextureView onSurfaceTextureDestroyed")
                unRegisterMultiCamera()
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun registerMultiCamera() {
        mCameraClient = MultiCameraClient(context, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                context?.let {
                    if (mCameraMap.containsKey(device.deviceId)) {
                        return
                    }
                    generateCamera(it, device).apply {
                        mCameraMap[device.deviceId] = this
                    }
                    // Initiate permission request when device insertion is detected
                    // If you want to open the specified camera, you need to override getDefaultCamera()
                    if (mRequestPermission.get()) {
                        return@let
                    }
                    getDefaultCamera()?.apply {
                        if (vendorId == device.vendorId && productId == device.productId) {
                            Log.d(TAG, "default camera pid: $productId, vid: $vendorId")
                            requestPermission(device)
                        }
                        return@let
                    }
                    requestPermission(device)
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                mCameraMap.remove(device?.deviceId)?.apply {
                    setUsbControlBlock(null)
                }
                mRequestPermission.set(false)
                try {
                    mCurrentCamera?.cancel(true)
                    mCurrentCamera = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                context ?: return
                mCameraMap[device.deviceId]?.apply {
                    setUsbControlBlock(ctrlBlock)
                }?.also { camera ->
                    try {
                        mCurrentCamera?.cancel(true)
                        mCurrentCamera = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    mCurrentCamera = SettableFuture()
                    mCurrentCamera?.set(camera)
                    openCamera(mCameraView)
                    Log.d(
                        TAG, "camera connection. pid: ${device.productId}, vid: ${device.vendorId}"
                    )
                }
            }

            override fun onDisConnectDec(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?
            ) {
                closeCamera()
                mRequestPermission.set(false)
            }

            override fun onCancelDev(device: UsbDevice?) {
                mRequestPermission.set(false)
                try {
                    mCurrentCamera?.cancel(true)
                    mCurrentCamera = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
        mCameraClient?.register()
    }

    protected fun unRegisterMultiCamera() {
        mCameraMap.values.forEach {
            it.closeCamera()
        }
        mCameraMap.clear()
        mCameraClient?.unRegister()
        mCameraClient?.destroy()
        mCameraClient = null
    }

    protected fun getDeviceList() = mCameraClient?.getDeviceList()

    /**
     * Get default camera
     *
     * @return Open camera by default, should be [UsbDevice]
     */
    protected open fun getDefaultCamera(): UsbDevice? = null

    protected fun openCamera(st: IAspectRatio? = null) {
        when (st) {
            is TextureView, is SurfaceView -> {
                st
            }
            else -> {
                null
            }
        }.apply {
            getCurrentCamera()?.openCamera(this, getCameraRequest())
            getCurrentCamera()?.setCameraStateCallBack(this@NewCameraView)
        }
    }

    protected fun closeCamera() {
        getCurrentCamera()?.closeCamera()
    }

    private fun surfaceSizeChanged(surfaceWidth: Int, surfaceHeight: Int) {
        getCurrentCamera()?.setRenderSize(surfaceWidth, surfaceHeight)
    }

    protected fun getCurrentCamera(): MultiCameraClient.ICamera? {
        return try {
            mCurrentCamera?.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected open fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(640)
            .setPreviewHeight(480)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(RotateType.ANGLE_0)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
            .setAspectRatioShow(true)
            .setCaptureRawImage(false)
            .setRawPreviewData(false)
            .create();
    }

    /**
     * Generate camera
     *
     * @param ctx context [Context]
     * @param device Usb device, see [UsbDevice]
     * @return Inheritor assignment camera api policy
     */
    protected open fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device, object : IDeviceStatusCallBack {
            override fun onConnectDev(device: UsbDevice?) {
                println("Device Connected")
            }

            override fun onDisConnectDev(device: UsbDevice?) {
                println("Device Disconnected")
            }
        })
    }

    protected fun requestPermission(device: UsbDevice?) {
        mRequestPermission.set(true)
        mCameraClient?.requestPermission(device)
    }


    companion object {
        private const val TAG = "NewCameraView"
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        when (code) {
            ICameraStateCallBack.State.ERROR -> {
                println("Camera State Error")
//                cameraConnection(false)
            }
            ICameraStateCallBack.State.CLOSED -> {
                println("Camera State Closed")
//                cameraConnection(false)
            }
            ICameraStateCallBack.State.OPENED -> {
                println("Camera State Open")
//                cameraConnection(true)
            }
        }
    }
}