package com.otaliastudios.cameraview.picture;

import android.hardware.Camera;

import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.engine.Camera1Engine;
import com.otaliastudios.cameraview.internal.utils.ExifHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * A {@link PictureResult} that uses standard APIs.
 */
public class Full1PictureRecorder extends FullPictureRecorder {

    private final Camera mCamera;
    private final Camera1Engine mEngine;
    private final int displayOrientation;
    private final int deviceOrientation;

    public Full1PictureRecorder(@NonNull PictureResult.Stub stub,
                                @NonNull Camera1Engine engine,
                                @NonNull Camera camera,
                                @NonNull int displayOrientation,
                                @NonNull int deviceOrientation
        ) {
        super(stub, engine);
        mEngine = engine;
        this.displayOrientation = displayOrientation;
        this.deviceOrientation = deviceOrientation;
        mCamera = camera;

        // We set the rotation to the camera parameters, but we don't know if the result will be
        // already rotated with 0 exif, or original with non zero exif. we will have to read EXIF.
        Camera.Parameters params = mCamera.getParameters();
        params.setRotation(mResult.rotation);
        mCamera.setParameters(params);
    }

    @Override
    public void take() {
        LOG.i("take() called.");
        // Stopping the preview callback is important on older APIs / emulators,
        // or takePicture can hang and leave the camera in a bad state.
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.takePicture(
                new Camera.ShutterCallback() {
                    @Override
                    public void onShutter() {
                        LOG.i("take(): got onShutter callback.");
                        dispatchOnShutter(true);
                    }
                },
                null,
                null,
                new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, final Camera camera) {
                        LOG.i("take(): got picture callback.");
                        int exifRotation;
                        try {
                            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(data));
                            int exifOrientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL);
                            exifRotation = ExifHelper.getOrientation(exifOrientation);
                        } catch (IOException e) {
                            exifRotation = 0;
                        }
                        mResult.data = data;
                        mResult.rotation = exifRotation;
                        LOG.i("take(): starting preview again. ", Thread.currentThread());
                        camera.setPreviewCallbackWithBuffer(mEngine);
                        camera.startPreview(); // This is needed, read somewhere in the docs.
                        dispatchResult();
                    }
                }
        );
        LOG.i("take() returned.");
    }

    @Override
    protected void dispatchResult() {
        LOG.i("dispatching result. Thread:", Thread.currentThread());
        super.dispatchResult();
    }

    private int calculateCaptureRotation(Camera camera) {
        int captureRotation = 0;

        Camera.CameraInfo mCameraInfo = null;
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, mCameraInfo);
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            captureRotation = (mCameraInfo.orientation + displayOrientation) % 360;
        } else {  // back-facing camera
            captureRotation = (mCameraInfo.orientation - displayOrientation + 360) % 360;
        }

        // Accommodate for any extra device rotation relative to fixed screen orientations
        // (e.g. activity fixed in portrait, but user took photo/video in landscape)
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            captureRotation = ((captureRotation - (displayOrientation - deviceOrientation)) + 360) % 360;
        } else {  // back-facing camera
            captureRotation = (captureRotation + (displayOrientation - deviceOrientation) + 360) % 360;
        }

        return captureRotation;
    }

}
