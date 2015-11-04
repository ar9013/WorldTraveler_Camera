package com.Kangyue.worldtraveler;

import java.io.File;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;


//Use the deprecated Camera class.

public class CameraActivity extends Activity implements CvCameraViewListener2 {

	// A tag for log output.
	private static String TAG = CameraActivity.class.getSimpleName();

	// A key for storing the index of the active camera.
	private static final String STATE_CAMERA_INDEX = "cameraIndex";

	// A key for storing the index of the active image size.
	private static final String STATE_IMAGE_SIZE_INDEX = "imageSizeIndex";

	// An ID for items in the size submenu.
	private static final int MENU_GROUP_ID_SIZE = 2;

	// The index of the active camera.
	private int mCameraIndex;

	// The index of the active image size.
	private int mImgSizeIndex;

	// Whather the active camera is front-facing.
	// If so, the camera view should be mirroed.
	private boolean mIsCameraFrontFacing;

	// The number of cameras on the device.
	private int mNumCameras;
	
    // The image sizes supported by the active camera.
    private List<Size> mSupportedImageSizes;

	// The camera view.
	private CameraBridgeViewBase mCameraView;

	// Whather the next camera frame should br saved sa a photo.
	private boolean mIsPhotoPending;

	// A matrix that id used when saving photos.
	private Mat mBgr;

	// Whether an asynchronous menu action is in progress.
	// If so, menu interaction should be disabled.
	private boolean mIsMenuLocked;

	// The OpenCV loader callback.
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS:
				Log.d(TAG, "OpenCV loaded successfully");
				mCameraView.enableView();
				
				mBgr = new Mat();
				break;

			default:
				super.onManagerConnected(status);
				break;
			}
		};
	};
	
	// Support back incompatibility errors because we provide 
	// backward-compatible fallbacks.
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	
		if(savedInstanceState != null){
			mCameraIndex = savedInstanceState.getInt(STATE_CAMERA_INDEX, 0);
			mImgSizeIndex = savedInstanceState.getInt(STATE_IMAGE_SIZE_INDEX,0);
		}else{
			mCameraIndex = 0;
			mImgSizeIndex = 0;
		}
		
		final Camera camera;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD){
			CameraInfo cameraInfo = new CameraInfo();
			Camera.getCameraInfo(mCameraIndex, cameraInfo);
			
			mIsCameraFrontFacing = (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT);
			mNumCameras = Camera.getNumberOfCameras();
			camera = Camera.open(mCameraIndex);
		}else{ //pre-Gingerbread
			// Assume that there is only 1 camera and it is rear-facing.
			mIsCameraFrontFacing = false;
			mNumCameras = 1;
			camera= Camera.open();
			
		}
		
		final Parameters parameters =camera.getParameters();
		camera.release();
		mSupportedImageSizes = parameters.getSupportedPreviewSizes();
		
		final Size size = mSupportedImageSizes.get(mImgSizeIndex);
		
		mCameraView = new JavaCameraView(this, mCameraIndex);
		mCameraView.setMaxFrameSize(size.width, size.height);
		mCameraView.setCvCameraViewListener(this);
		setContentView(mCameraView);
		
	}
	
	@Override
	protected void onSaveInstanceState(Bundle savedInstanceState) {
		// Save the current camera index.
		savedInstanceState.putInt(STATE_CAMERA_INDEX, mCameraIndex);
		
		super.onSaveInstanceState(savedInstanceState);
	}
	// Suppress backward incompatibility errors because we provide 
	// backward-compatible fallbacks.

	public void recreate() {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
			super.recreate();
		}else{
			finish();
			startActivity(getIntent());
		}
	};
	
	public void onPause() {
		if(mCameraView != null){
			mCameraView.disableView();
		}
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
//		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
		if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, CameraActivity.this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
		
		mIsMenuLocked = false;
	}
	
	@Override
	protected void onDestroy() {
		if(mCameraView != null){
			mCameraView.disableView();
		}
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_camera,menu);
		if(mNumCameras <2){
			menu.removeItem(R.id.menu_next_camera);
		}
		int numSupportedImageSizes = mSupportedImageSizes.size();
		if(numSupportedImageSizes >1){
			final SubMenu sizeSubMenu = menu.addSubMenu(R.string.menu_image_size);
			
			for(int i=0;i<numSupportedImageSizes; i++){
				final Size size = mSupportedImageSizes.get(i);
				sizeSubMenu.add(MENU_GROUP_ID_SIZE,i,Menu.NONE,String.format("%dx%d", size.width,size.height));
			}
		}
		return true;
		
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(mIsMenuLocked){
			return true;
		}
		if(item.getGroupId() == MENU_GROUP_ID_SIZE){
			mImgSizeIndex = item.getItemId();
			recreate();
			
			return true;
		}
		switch (item.getItemId()) {
		case R.id.menu_next_camera:
			mIsMenuLocked = true;
			
			// With another camera index, recreate the activity.
			mCameraIndex++;
			if(mCameraIndex == mNumCameras){
				mCameraIndex = 0;
			}
			mImgSizeIndex = 0;
			recreate();
			return true;

		case R.id.menu_take_photo:
			mIsMenuLocked = true;
			
			// Next frame, take the photo.
			mIsPhotoPending = true;
			
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onCameraViewStarted(int width, int height) {
	

	}

	@Override
	public void onCameraViewStopped() {
		// TODO Auto-generated method stub

	}

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		final Mat rgba = inputFrame.rgba();
		
		if(mIsPhotoPending){
			mIsPhotoPending = false;
			takePhoto(rgba);
		}
		
		if(mIsCameraFrontFacing){
			Core.flip(rgba, rgba, 1);
		}
		return rgba;
	}
	
	private void takePhoto(final Mat rgba){
		
		// Determine the path and metadata for the photo.
		final long currentTimeMillis = System.currentTimeMillis();
		final String appName = getString(R.string.app_name);
		final String galleryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
		final String albumPath = galleryPath + File.separator +
	               appName;
		final String photoPath = albumPath + File.separator +
                currentTimeMillis + LabActivity.PHOTO_FILE_EXTENSION;
		final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, photoPath);
        values.put(Images.Media.MIME_TYPE,
                LabActivity.PHOTO_MIME_TYPE);
        values.put(Images.Media.TITLE, appName);
        values.put(Images.Media.DESCRIPTION, appName);
        values.put(Images.Media.DATE_TAKEN, currentTimeMillis);
	
     // Ensure that the album directory exists.
        File album = new File(albumPath);
        if (!album.isDirectory() && !album.mkdirs()) {
            Log.e(TAG, "Failed to create album directory at " +
                    albumPath);
            onTakePhotoFailed();
            return;
        }
        
        // Try to create the photo.
        Imgproc.cvtColor(rgba, mBgr, Imgproc.COLOR_RGBA2BGR, 3);
        if (!Imgcodecs.imwrite(photoPath, mBgr)) {
            Log.e(TAG, "Failed to save photo to " + photoPath);
            onTakePhotoFailed();
        }
        Log.d(TAG, "Photo saved successfully to " + photoPath);
        
        // Try to insert the photo into the MediaStore.
        Uri uri;
        try {
            uri = getContentResolver().insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (final Exception e) {
            Log.e(TAG, "Failed to insert photo into MediaStore");
            e.printStackTrace();
            
            // Since the insertion failed, delete the photo.
            File photo = new File(photoPath);
            if (!photo.delete()) {
                Log.e(TAG, "Failed to delete non-inserted photo");
            }
            
            onTakePhotoFailed();
            return;
        }
        
        // Open the photo in LabActivity.
        final Intent intent = new Intent(this, LabActivity.class);
        intent.putExtra(LabActivity.EXTRA_PHOTO_URI, uri);
        intent.putExtra(LabActivity.EXTRA_PHOTO_DATA_PATH,
                photoPath);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startActivity(intent);
            }
        });
	}
	
	 private void onTakePhotoFailed() {
	        mIsMenuLocked = false;
	        
	        // Show an error message.
	        final String errorMessage =
	                getString(R.string.photo_error_message);
	        runOnUiThread(new Runnable() {
	            @Override
	            public void run() {
	                Toast.makeText(CameraActivity.this, errorMessage,
	                        Toast.LENGTH_SHORT).show();
	            }
	        });
	    }

	
}
