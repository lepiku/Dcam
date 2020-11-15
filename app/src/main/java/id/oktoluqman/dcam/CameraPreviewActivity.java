package id.oktoluqman.dcam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;

import java.util.Collections;

public class CameraPreviewActivity extends AppCompatActivity {

    public static final int CAMERA_REQUEST_CODE = 10001234;

    Button button;
    TextureView textureView;
    CameraManager cameraManager;
    String cameraId;
    CameraDevice cameraDevice;
    Size size;
    Handler backgroundHandler;
    HandlerThread handlerThread;
    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            closeCamera();
        }
    };

    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder captureRequestBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        textureView = findViewById(R.id.texture_view);
        button = findViewById(R.id.fab_take_photo);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    }


    @Override
    protected void onResume() {
        System.out.println("onResume");
        super.onResume();
        openBackgroundThread();
        if (textureView.isAvailable()) {
            setupCamera();
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("onPause");
        closeCamera();
        closeBackgroundThread();
    }

    @Override
    protected void onStop() {
        super.onStop();
        System.out.println("onStop");
        closeCamera();
        closeBackgroundThread();
    }

    private void setupCamera() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String cameraId: cameraIds) {
                System.out.println("camid " + cameraId);
                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(cameraId);
                System.out.println("camid " + cameraId + " facing " + cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) + " " + cameraFacing);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == cameraFacing) {
                    this.cameraId = cameraId;
                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    size = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
                    System.out.println("set as camera " + cameraId);
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            try {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void openBackgroundThread() {
        handlerThread = new HandlerThread("camera_thread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    private void closeBackgroundThread() {
        if (backgroundHandler != null) {
            handlerThread.quitSafely();
            handlerThread = null;
            backgroundHandler = null;
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void createCaptureSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (cameraDevice == null) {
                                return;
                            }

                            try {
                                CaptureRequest captureRequest = captureRequestBuilder.build();
                                CameraPreviewActivity.this.cameraCaptureSession = cameraCaptureSession;
                                CameraPreviewActivity.this.cameraCaptureSession.setRepeatingRequest(
                                        captureRequest, null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

}