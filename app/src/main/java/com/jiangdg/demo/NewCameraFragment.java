package com.jiangdg.demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.base.CameraFragment;
import com.jiangdg.ausbc.callback.ICaptureCallBack;
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.jiangdg.ausbc.widget.IAspectRatio;

public class NewCameraFragment extends CameraFragment {
    private ViewGroup mCameraViewContainer;

    @Nullable
    @Override
    protected View getRootView(@NonNull LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup) {
        View mView = layoutInflater.inflate(R.layout.fragment_uvc_camera,viewGroup,false);
        mCameraViewContainer = mView.findViewById(R.id.fragment_container_new);
        return mView.getRootView();
    }

    @Nullable
    @Override
    protected IAspectRatio getCameraView() {
        return new AspectRatioTextureView(requireContext());
    }

    @Nullable
    @Override
    protected ViewGroup getCameraViewContainer() {
        return mCameraViewContainer;
    }

    @Override
    public void onCameraState(@NonNull MultiCameraClient.ICamera iCamera, @NonNull State state, @Nullable String s) {
        switch (state){
            case ERROR:
                System.out.println("Camera State Error");
                break;
            case CLOSED:
                System.out.println("Camera State Closed");
                break;
            case OPENED:
                System.out.println("Camera State Open");
                break;
        }
    }

    private void captureImage() {
        captureImage(new ICaptureCallBack() {
            @Override
            public void onBegin() {
            }

            @Override
            public void onError(String error) {

            }

            @Override
            public void onComplete(String path) {

            }
        },null);
    }

}
