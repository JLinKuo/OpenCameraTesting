package com.example.opencameratesting.opencamera;

/** Stores all of the string keys used for SharedPreferences.
 */
public class PreferenceKeys {
    // must be static, to safely call from other Activities
	
	// arguably the static methods here that don't receive an argument could just be static final strings? Though we may want to change some of them to be cameraId-specific in future

	public static final String IsVideoPreferenceKey = "is_video";

	public static final String LocationPreferenceKey = "preference_location";

	public static final String FrontCameraMirrorKey = "preference_front_camera_mirror";

	public static final String ThumbnailAnimationPreferenceKey = "preference_thumbnail_animation";

	public static final String TakePhotoBorderPreferenceKey = "preference_take_photo_border";

	public static final String ShowZoomPreferenceKey = "preference_show_zoom";

	public static final String ShowISOPreferenceKey = "preference_show_iso";

	public static final String ShowVideoMaxAmpPreferenceKey = "preference_show_video_max_amp";

	public static final String ShowAnglePreferenceKey = "preference_show_angle";

	public static final String ShowAngleLinePreferenceKey = "preference_show_angle_line";

	public static final String ShowPitchLinesPreferenceKey = "preference_show_pitch_lines";

	public static final String ShowGeoDirectionLinesPreferenceKey = "preference_show_geo_direction_lines";

	public static final String ShowAngleHighlightColorPreferenceKey = "preference_angle_highlight_color";

	public static final String ShowGeoDirectionPreferenceKey = "preference_show_geo_direction";

	public static final String ShowFreeMemoryPreferenceKey = "preference_free_memory";

	public static final String ShowTimePreferenceKey = "preference_show_time";

	public static final String ShowBatteryPreferenceKey = "preference_show_battery";

	public static final String ShowGridPreferenceKey = "preference_grid";

	public static final String ShowCropGuidePreferenceKey = "preference_crop_guide";

	public static final String GhostImagePreferenceKey = "preference_ghost_image";
    
    public static String getRecordAudioPreferenceKey() {
    	return "preference_record_audio";
    }

    public static String getRecordAudioChannelsPreferenceKey() {
    	return "preference_record_audio_channels";
    }

    public static String getRecordAudioSourcePreferenceKey() {
    	return "preference_record_audio_src";
    }

	public static final String PreviewSizePreferenceKey = "preference_preview_size";

    public static String getTimerPreferenceKey() {
    	return "preference_timer";
    }
    
    public static String getRepeatModePreferenceKey() {
    	// note for historical reasons the preference refers to burst; the feature was renamed to
		// "repeat" in v1.43, but we still need to use the old string to avoid changing user settings
		// when people upgrade
    	return "preference_burst_mode";
    }
    
	public static final String ImmersiveModePreferenceKey = "preference_immersive_mode";
}
