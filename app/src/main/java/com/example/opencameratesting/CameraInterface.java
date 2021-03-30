package com.example.opencameratesting;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;

import androidx.fragment.app.Fragment;

import com.example.opencameratesting.opencamera.CameraController.RawImage;
import com.example.opencameratesting.opencamera.CameraVideoHelper;
import com.example.opencameratesting.opencamera.GyroSensor;
import com.example.opencameratesting.opencamera.LocationSupplier;
import com.example.opencameratesting.opencamera.MyDebug;
import com.example.opencameratesting.opencamera.PreferenceKeys;
import com.example.opencameratesting.opencamera.Preview.BasicApplicationInterface;
import com.example.opencameratesting.opencamera.Preview.VideoProfile;
import com.example.opencameratesting.opencamera.UI.DrawPreview;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;


public class CameraInterface extends BasicApplicationInterface {
    private static final String TAG = "CameraInterface";

    private String displayMode = "";

    // note, okay to change the order of enums in future versions, as getPhotoMode() does not rely on the order for the saved photo mode
    public enum PhotoMode {
        Standard,
        DRO, // single image "fake" HDR
        HDR, // HDR created from multiple (expo bracketing) images
        ExpoBracketing, // take multiple expo bracketed images, without combining to a single image
        FocusBracketing, // take multiple focus bracketed images, without combining to a single image
        FastBurst,
        NoiseReduction,
        Panorama
    }

    private CameraVideoHelper cameraVideoHelper;
    private final LocationSupplier locationSupplier;
    private final DrawPreview drawPreview;
    private final GyroSensor gyroSensor;
    // store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
    private final SharedPreferences sharedPreferences;

    private int zoom_factor; // don't save zoom, as doing so tends to confuse users; other camera applications don't seem to save zoom when pause/resuming
    // camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
    private int cameraId = 0;
    private int n_capture_images = 0; // how many calls to onPictureTaken() since the last call to onCaptureStarted()

    private boolean used_front_screen_flash;
    private final Rect text_bounds = new Rect();
    private boolean isVideoMode = false;
    private String orientation = "landscape";
    private TakePhotoListener takePhotoListener;

    public CameraInterface(
            CameraVideoHelper cameraVideoHelper,
            boolean isVideoMode,
            String orientation
    ) {
        this.cameraVideoHelper = cameraVideoHelper;
        this.drawPreview = new DrawPreview(cameraVideoHelper, this);
        this.gyroSensor = new GyroSensor(cameraVideoHelper.getActivity());
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(cameraVideoHelper.getActivity());
        this.locationSupplier = new LocationSupplier(cameraVideoHelper.getActivity());
        this.isVideoMode = isVideoMode;
        this.orientation = orientation;

        this.reset();
    }

    public GyroSensor getGyroSensor() {
        return gyroSensor;
    }

    public enum Alignment {
        ALIGNMENT_TOP,
        ALIGNMENT_CENTRE,
        ALIGNMENT_BOTTOM
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, Alignment.ALIGNMENT_BOTTOM);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, true);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, boolean shadow) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, shadow, null);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, boolean shadow, Rect bounds) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        paint.setAlpha(64);
        if( bounds != null ) {
            text_bounds.set(bounds);
        }
        else {
            int alt_height = 0;
            if( ybounds_text != null ) {
                paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), text_bounds);
                alt_height = text_bounds.bottom - text_bounds.top;
            }
            paint.getTextBounds(text, 0, text.length(), text_bounds);
            if( ybounds_text != null ) {
                text_bounds.bottom = text_bounds.top + alt_height;
            }
        }
        final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
        if( paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER ) {
            float width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
			/*if( MyDebug.LOG )
				Log.d(TAG, "width: " + width);*/
            if( paint.getTextAlign() == Paint.Align.CENTER )
                width /= 2.0f;
            text_bounds.left -= width;
            text_bounds.right -= width;
        }
		/*if( MyDebug.LOG )
			Log.d(TAG, "text_bounds left-right: " + text_bounds.left + " , " + text_bounds.right);*/
        text_bounds.left += location_x - padding;
        text_bounds.right += location_x + padding;
        // unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
        int top_y_diff = - text_bounds.top + padding - 1;
        if( alignment_y == Alignment.ALIGNMENT_TOP ) {
            int height = text_bounds.bottom - text_bounds.top + 2*padding;
            text_bounds.top = location_y - 1;
            text_bounds.bottom = text_bounds.top + height;
            location_y += top_y_diff;
        }
        else if( alignment_y == Alignment.ALIGNMENT_CENTRE ) {
            int height = text_bounds.bottom - text_bounds.top + 2*padding;
            //int y_diff = - text_bounds.top + padding - 1;
            text_bounds.top = (int)(0.5 * ( (location_y - 1) + (text_bounds.top + location_y - padding) )); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
            text_bounds.bottom = text_bounds.top + height;
            location_y += (int)(0.5*top_y_diff); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
        }
        else {
            text_bounds.top += location_y - padding;
            text_bounds.bottom += location_y + padding;
        }
        paint.setColor(foreground);
        canvas.drawText(text, location_x, location_y, paint);
        if( shadow ) {
            paint.setColor(background);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            canvas.drawText(text, location_x, location_y, paint);
            paint.setStyle(Paint.Style.FILL); // set back to default
        }
        return text_bounds.bottom - text_bounds.top;
    }

    public boolean getThumbnailAnimationPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, true);
    }

    /** Returns the current photo mode.
     *  Note, this always should return the true photo mode - if we're in video mode and taking a photo snapshot while
     *  video recording, the caller should override. We don't override here, as this preference may be used to affect how
     *  the CameraController is set up, and we don't always re-setup the camera when switching between photo and video modes.
     */
    public PhotoMode getPhotoMode() {
        return PhotoMode.Standard;
    }

    LocationSupplier getLocationSupplier() {
        return locationSupplier;
    }

    public void setTakePhotoListener(TakePhotoListener listener) {
        this.takePhotoListener = listener;
    }

    public boolean isMockLocation(Location location) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && location != null && location.isFromMockProvider();
    }

    @Override
    public Location getLocation() {
        Location location = locationSupplier.getLocation();

        if(location != null) {
//            Log.d("JLin", "" + location);

            if (isMockLocation(location)) {
//                if (getContext() instanceof BaseActivity) {
//                    ((BaseActivity) getContext()).showOneButtonDialog("", getContext().getString(R.string.error_gps), null);
//                }

                location = null;
            }
        } else {
//            Log.e("JLin", "location is null");
        }

        return location;
    }

    @Override
    public int getCameraIdPref() {
        return cameraId;
    }

    @Override
    public String getFlashPref() {
        return super.getFlashPref();
    }

    @Override
    public String getFocusPref(boolean is_video) {
        return super.getFocusPref(is_video);
    }

    @Override
    public boolean isVideoPref() {
        if(isVideoMode) {
            return sharedPreferences.getBoolean(PreferenceKeys.IsVideoPreferenceKey, false);
        }

        return super.isVideoPref();
    }

    @Override
    public String getSceneModePref() {
        return super.getSceneModePref();
    }

    @Override
    public String getColorEffectPref() {
        return super.getColorEffectPref();
    }

    @Override
    public String getWhiteBalancePref() {
        return super.getWhiteBalancePref();
    }

    @Override
    public int getWhiteBalanceTemperaturePref() {
        return super.getWhiteBalanceTemperaturePref();
    }

    @Override
    public String getAntiBandingPref() {
        return super.getAntiBandingPref();
    }

    @Override
    public String getEdgeModePref() {
        return super.getEdgeModePref();
    }

    @Override
    public String getCameraNoiseReductionModePref() {
        return super.getCameraNoiseReductionModePref();
    }

    @Override
    public String getISOPref() {
        return super.getISOPref();
    }

    @Override
    public int getExposureCompensationPref() {
        return super.getExposureCompensationPref();
    }

    @Override
    public Pair<Integer, Integer> getCameraResolutionPref() {
        return super.getCameraResolutionPref();
    }

    @Override
    public int getImageQualityPref() {
        return super.getImageQualityPref();
    }

    @Override
    public boolean getFaceDetectionPref() {
        return super.getFaceDetectionPref();
    }

    @Override
    public String getVideoQualityPref() {
        return super.getVideoQualityPref();
    }

    @Override
    public boolean getVideoStabilizationPref() {
        return super.getVideoStabilizationPref();
    }

    @Override
    public boolean getForce4KPref() {
        return super.getForce4KPref();
    }

    @Override
    public String getRecordVideoOutputFormatPref() {
        return super.getRecordVideoOutputFormatPref();
    }

    @Override
    public String getVideoBitratePref() {
        return super.getVideoBitratePref();
    }

    @Override
    public String getVideoFPSPref() {
        return super.getVideoFPSPref();
    }

    @Override
    public float getVideoCaptureRateFactor() {
        return super.getVideoCaptureRateFactor();
    }

    @Override
    public boolean useVideoLogProfile() {
        return super.useVideoLogProfile();
    }

    @Override
    public float getVideoLogProfileStrength() {
        return super.getVideoLogProfileStrength();
    }

    @Override
    public long getVideoMaxDurationPref() {
        return super.getVideoMaxDurationPref();
    }

    @Override
    public int getVideoRestartTimesPref() {
        return super.getVideoRestartTimesPref();
    }

    @Override
    public VideoMaxFileSize getVideoMaxFileSizePref() throws NoFreeStorageException {
        return super.getVideoMaxFileSizePref();
    }

    @Override
    public boolean getVideoFlashPref() {
        return super.getVideoFlashPref();
    }

    @Override
    public boolean getVideoLowPowerCheckPref() {
        return super.getVideoLowPowerCheckPref();
    }

    @Override
    public String getPreviewSizePref() {
        return sharedPreferences.getString(PreferenceKeys.PreviewSizePreferenceKey, "preference_preview_size_display");
    }

    @Override
    public String getPreviewRotationPref() {
        return super.getPreviewRotationPref();
    }

    @Override
    public String getLockOrientationPref() {
        return orientation;
    }

    @Override
    public boolean getTouchCapturePref() {
        return super.getTouchCapturePref();
    }

    @Override
    public boolean getDoubleTapCapturePref() {
        return super.getDoubleTapCapturePref();
    }

    @Override
    public boolean getPausePreviewPref() {
        return super.getPausePreviewPref();
    }

    @Override
    public boolean getShowToastsPref() {
        return super.getShowToastsPref();
    }

    @Override
    public boolean getShutterSoundPref() {
        return false;
    }

    @Override
    public boolean getStartupFocusPref() {
        return super.getStartupFocusPref();
    }

    @Override
    public long getTimerPref() {
        return super.getTimerPref();
    }

    @Override
    public String getRepeatPref() {
        return super.getRepeatPref();
    }

    @Override
    public long getRepeatIntervalPref() {
        return super.getRepeatIntervalPref();
    }

    @Override
    public boolean getGeotaggingPref() {
        return true;
    }

    @Override
    public boolean getRequireLocationPref() {
        return super.getRequireLocationPref();
    }

    @Override
    public boolean getRecordAudioPref() {
        return super.getRecordAudioPref();
    }

    @Override
    public String getRecordAudioChannelsPref() {
        return super.getRecordAudioChannelsPref();
    }

    @Override
    public String getRecordAudioSourcePref() {
        return super.getRecordAudioSourcePref();
    }

    @Override
    public int getZoomPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getZoomPref: " + zoom_factor);
        return zoom_factor;
    }

    @Override
    public double getCalibratedLevelAngle() {
        return super.getCalibratedLevelAngle();
    }

    @Override
    public boolean canTakeNewPhoto() {
        return super.canTakeNewPhoto();
    }

    @Override
    public boolean imageQueueWouldBlock(boolean has_raw, int n_jpegs) {
        return super.imageQueueWouldBlock(has_raw, n_jpegs);
    }

    @Override
    public long getExposureTimePref() {
        return super.getExposureTimePref();
    }

    @Override
    public float getFocusDistancePref(boolean is_target_distance) {
        return super.getFocusDistancePref(is_target_distance);
    }

    @Override
    public boolean isExpoBracketingPref() {
        return super.isExpoBracketingPref();
    }

    @Override
    public int getExpoBracketingNImagesPref() {
        return super.getExpoBracketingNImagesPref();
    }

    @Override
    public double getExpoBracketingStopsPref() {
        return super.getExpoBracketingStopsPref();
    }

    @Override
    public int getFocusBracketingNImagesPref() {
        return super.getFocusBracketingNImagesPref();
    }

    @Override
    public boolean getFocusBracketingAddInfinityPref() {
        return super.getFocusBracketingAddInfinityPref();
    }

    @Override
    public boolean isFocusBracketingPref() {
        return super.isFocusBracketingPref();
    }

    @Override
    public boolean isCameraBurstPref() {
        return super.isCameraBurstPref();
    }

    @Override
    public int getBurstNImages() {
        return super.getBurstNImages();
    }

    @Override
    public boolean getBurstForNoiseReduction() {
        return super.getBurstForNoiseReduction();
    }

    @Override
    public NRModePref getNRModePref() {
        return super.getNRModePref();
    }

    @Override
    public boolean getOptimiseAEForDROPref() {
        return super.getOptimiseAEForDROPref();
    }

    @Override
    public RawPref getRawPref() {
        return super.getRawPref();
    }

    @Override
    public int getMaxRawImages() {
        return super.getMaxRawImages();
    }

    @Override
    public boolean useCamera2FakeFlash() {
        return super.useCamera2FakeFlash();
    }

    @Override
    public boolean useCamera2FastBurst() {
        return super.useCamera2FastBurst();
    }

    @Override
    public boolean usePhotoVideoRecording() {
        return super.usePhotoVideoRecording();
    }

    @Override
    public boolean isTestAlwaysFocus() {
        return super.isTestAlwaysFocus();
    }

    @Override
    public void cameraSetup() {
        Fragment fragment = cameraVideoHelper.getFragment();

        if(fragment instanceof CameraFragment) {
            ((CameraFragment)fragment).cameraSetup();
        }
        drawPreview.clearContinuousFocusMove();
        // Need to cause drawPreview.updateSettings(), otherwise icons like HDR won't show after force-restart, because we only
        // know that HDR is supported after the camera is opened
        // Also needed for settings which update when switching between photo and video mode.
        drawPreview.updateSettings();
    }

    @Override
    public void touchEvent(MotionEvent event) {
        super.touchEvent(event);
    }

    @Override
    public void startingVideo() {
        super.startingVideo();
    }

    @Override
    public void startedVideo() {
        super.startedVideo();
    }

    @Override
    public void stoppingVideo() {
        super.stoppingVideo();
    }

    @Override
    public void stoppedVideo(int video_method, Uri uri, String filename) {
        cameraVideoHelper.saveVideo(filename);
    }

    @Override
    public void onFailedStartPreview() {
        super.onFailedStartPreview();
    }

    @Override
    public void onCameraError() {
        super.onCameraError();
    }

    @Override
    public void onPhotoError() {
        super.onPhotoError();
    }

    @Override
    public void onVideoInfo(int what, int extra) {
        super.onVideoInfo(what, extra);
    }

    @Override
    public void onVideoError(int what, int extra) {
        super.onVideoError(what, extra);
    }

    @Override
    public void onVideoRecordStartError(VideoProfile profile) {
        super.onVideoRecordStartError(profile);
    }

    @Override
    public void onVideoRecordStopError(VideoProfile profile) {
        super.onVideoRecordStopError(profile);
    }

    @Override
    public void onFailedReconnectError() {
        super.onFailedReconnectError();
    }

    @Override
    public void onFailedCreateVideoFileError() {
        super.onFailedCreateVideoFileError();
    }

    @Override
    public void hasPausedPreview(boolean paused) {
        super.hasPausedPreview(paused);
    }

    @Override
    public void cameraInOperation(boolean in_operation, boolean is_video) {
        if( MyDebug.LOG )
            Log.d(TAG, "cameraInOperation: " + in_operation);
        if( !in_operation && used_front_screen_flash ) {
//            main_activity.setBrightnessForCamera(false); // ensure screen brightness matches user preference, after using front screen flash
            used_front_screen_flash = false;
        }
        drawPreview.cameraInOperation(in_operation);
    }

    @Override
    public void turnFrontScreenFlashOn() {
        super.turnFrontScreenFlashOn();
    }

    @Override
    public void cameraClosed() {
        super.cameraClosed();
    }

    @Override
    public void timerBeep(long remaining_time) {
        super.timerBeep(remaining_time);
    }

    @Override
    public void layoutUI() {
        super.layoutUI();
    }

    @Override
    public void multitouchZoom(int new_zoom) {
        Fragment fragment = cameraVideoHelper.getFragment();

        if(fragment instanceof CameraFragment) {
            ((CameraFragment)fragment).setSeekbarZoom(new_zoom);
        }
    }

    @Override
    public void setCameraIdPref(int cameraId) {
        this.cameraId = cameraId;
    }

    @Override
    public void setFlashPref(String flash_value) {
        super.setFlashPref(flash_value);
    }

    @Override
    public void setFocusPref(String focus_value, boolean is_video) {
        super.setFocusPref(focus_value, is_video);
    }

    @Override
    public void setVideoPref(boolean is_video) {
        super.setVideoPref(is_video);
    }

    @Override
    public void setSceneModePref(String scene_mode) {
        super.setSceneModePref(scene_mode);
    }

    @Override
    public void clearSceneModePref() {
        super.clearSceneModePref();
    }

    @Override
    public void setColorEffectPref(String color_effect) {
        super.setColorEffectPref(color_effect);
    }

    @Override
    public void clearColorEffectPref() {
        super.clearColorEffectPref();
    }

    @Override
    public void setWhiteBalancePref(String white_balance) {
        super.setWhiteBalancePref(white_balance);
    }

    @Override
    public void clearWhiteBalancePref() {
        super.clearWhiteBalancePref();
    }

    @Override
    public void setWhiteBalanceTemperaturePref(int white_balance_temperature) {
        super.setWhiteBalanceTemperaturePref(white_balance_temperature);
    }

    @Override
    public void setISOPref(String iso) {
        super.setISOPref(iso);
    }

    @Override
    public void clearISOPref() {
        super.clearISOPref();
    }

    @Override
    public void setExposureCompensationPref(int exposure) {
        super.setExposureCompensationPref(exposure);
    }

    @Override
    public void clearExposureCompensationPref() {
        super.clearExposureCompensationPref();
    }

    @Override
    public void setCameraResolutionPref(int width, int height) {
        super.setCameraResolutionPref(width, height);
    }

    @Override
    public void setVideoQualityPref(String video_quality) {
        super.setVideoQualityPref(video_quality);
    }

    @Override
    public void setZoomPref(int zoom) {
        if( MyDebug.LOG )
            Log.d(TAG, "setZoomPref: " + zoom);
        this.zoom_factor = zoom;
    }

    /** Should be called to reset parameters which aren't expected to be saved (e.g., resetting zoom when application is paused,
     *  when switching between photo/video modes, or switching cameras).
     */
    public void reset() {
        if( MyDebug.LOG )
            Log.d(TAG, "reset");
        this.zoom_factor = 0;
    }

    @Override
    public void setExposureTimePref(long exposure_time) {
        super.setExposureTimePref(exposure_time);
    }

    @Override
    public void clearExposureTimePref() {
        super.clearExposureTimePref();
    }

    @Override
    public void setFocusDistancePref(float focus_distance, boolean is_target_distance) {
        super.setFocusDistancePref(focus_distance, is_target_distance);
    }

    @Override
    public void onDrawPreview(Canvas canvas) {
        drawPreview.onDrawPreview(canvas);
    }

    @Override
    public boolean onBurstPictureTaken(List<byte[]> images, Date current_date) {
        return super.onBurstPictureTaken(images, current_date);
    }

    @Override
    public boolean onRawPictureTaken(RawImage raw_image, Date current_date) {
        return super.onRawPictureTaken(raw_image, current_date);
    }

    @Override
    public void onCaptureStarted() {
        super.onCaptureStarted();
    }

    @Override
    public void onPictureCompleted() {
        super.onPictureCompleted();
        if(takePhotoListener != null) {
            takePhotoListener.onTakePhotoFinished();
        }
    }

    @Override
    public void onContinuousFocusMove(boolean start) {
        super.onContinuousFocusMove(start);
    }

    @Override
    public Context getContext() {
        return cameraVideoHelper.getActivity();
    }

    @Override
    public boolean useCamera2() {
        return false;
    }

    @Override
    public int createOutputVideoMethod() {
        return 0;
    }

    @Override
    public File createOutputVideoFile(String extension) throws IOException {
        return cameraVideoHelper.getVideoFile();
    }

    @Override
    public Uri createOutputVideoSAF(String extension) throws IOException {
        return null;
    }

    @Override
    public Uri createOutputVideoUri() {
        return null;
    }

    @Override
    public void requestCameraPermission() {

    }

    @Override
    public boolean needsStoragePermission() {
        return false;
    }

    @Override
    public void requestStoragePermission() {

    }

    @Override
    public void requestRecordAudioPermission() {

    }

    @Override
    public boolean onPictureTaken(byte[] data, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "onPictureTaken");

        n_capture_images++;
        if( MyDebug.LOG )
            Log.d(TAG, "n_capture_images is now " + n_capture_images);

        boolean success = cameraVideoHelper.saveImage(data);

        if( MyDebug.LOG )
            Log.d(TAG, "onPictureTaken complete, success: " + success);

        return success;
    }

    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        if( drawPreview != null ) {
            drawPreview.onDestroy();
        }
    }

    public interface TakePhotoListener {
        void onTakePhotoFinished();
    }
}
